package io.github.heybadrinath.reconriver.workspace;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.heybadrinath.reconriver.shared.config.ReconRiverProperties;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

@Component
public class RateLimitFilter implements Filter {
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
    private final ReconRiverProperties properties;
    private final Clock clock;
    private final ObjectMapper objectMapper;

    RateLimitFilter(ReconRiverProperties properties, Clock clock, ObjectMapper objectMapper) {
        this.properties = properties;
        this.clock = clock;
        this.objectMapper = objectMapper;
    }

    @Override
    public void doFilter(
            ServletRequest servletRequest, ServletResponse servletResponse, FilterChain chain)
            throws ServletException, IOException {
        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;
        Limit limit = limitFor(request);
        Instant now = clock.instant();
        String key = request.getRemoteAddr() + ':' + limit.bucket();
        Window window =
                windows.compute(
                        key,
                        (ignored, current) ->
                                current == null || !now.isBefore(current.resetAt())
                                        ? new Window(1, now.plus(limit.window()))
                                        : new Window(current.count() + 1, current.resetAt()));
        if (window.count() > limit.maximum()) {
            long retryAfter = Math.max(1, Duration.between(now, window.resetAt()).toSeconds());
            response.setStatus(429);
            response.setHeader("Retry-After", Long.toString(retryAfter));
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            objectMapper.writeValue(
                    response.getOutputStream(),
                    Map.of(
                            "type",
                            "https://reconriver.dev/problems/rate-limit-exceeded",
                            "title",
                            "Too Many Requests",
                            "status",
                            429,
                            "detail",
                            "This public demo limit has been reached. Try again after the Retry-After interval.",
                            "code",
                            "RATE_LIMIT_EXCEEDED"));
            return;
        }
        response.setHeader("X-RateLimit-Limit", Integer.toString(limit.maximum()));
        response.setHeader(
                "X-RateLimit-Remaining",
                Integer.toString(Math.max(0, limit.maximum() - window.count())));
        chain.doFilter(request, response);
        if (windows.size() > 10_000) {
            windows.entrySet().removeIf(entry -> now.isAfter(entry.getValue().resetAt()));
        }
    }

    private Limit limitFor(HttpServletRequest request) {
        String path = request.getRequestURI();
        if ("POST".equals(request.getMethod()) && path.equals("/api/v1/demo/sessions")) {
            return new Limit(
                    "session",
                    properties.security().sessionCreationsPerMinute(),
                    Duration.ofMinutes(1));
        }
        if ("POST".equals(request.getMethod()) && path.equals("/api/v1/imports")) {
            return new Limit("upload", properties.security().uploadsPerHour(), Duration.ofHours(1));
        }
        if ("POST".equals(request.getMethod())
                && path.matches("/api/v1/demo/scenarios/[^/]+/load")) {
            return new Limit(
                    "scenario-load",
                    properties.security().scenarioLoadsPerHour(),
                    Duration.ofHours(1));
        }
        return new Limit(
                "request", properties.security().requestsPerMinute(), Duration.ofMinutes(1));
    }

    private record Limit(String bucket, int maximum, Duration window) {}

    private record Window(int count, Instant resetAt) {}
}
