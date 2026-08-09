package io.github.heybadrinath.reconriver.workspace;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.heybadrinath.reconriver.shared.config.ReconRiverProperties;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RateLimitFilterTest {

    @Test
    void scenarioLoadsHaveAnIndependentHourlyLimit() throws Exception {
        ReconRiverProperties properties =
                new ReconRiverProperties(
                        new ReconRiverProperties.Security(
                                List.of("http://localhost:3000"),
                                "test-secret",
                                24,
                                100,
                                100,
                                100,
                                1),
                        null,
                        null,
                        null);
        RateLimitFilter filter =
                new RateLimitFilter(
                        properties,
                        Clock.fixed(Instant.parse("2026-08-09T12:00:00Z"), ZoneOffset.UTC),
                        new ObjectMapper());

        MockHttpServletResponse accepted = invoke(filter);
        MockHttpServletResponse limited = invoke(filter);

        assertThat(accepted.getStatus()).isEqualTo(200);
        assertThat(accepted.getHeader("X-RateLimit-Limit")).isEqualTo("1");
        assertThat(limited.getStatus()).isEqualTo(429);
        assertThat(limited.getHeader("Retry-After")).isEqualTo("3600");
        assertThat(limited.getContentAsString()).contains("RATE_LIMIT_EXCEEDED");
    }

    private MockHttpServletResponse invoke(RateLimitFilter filter) throws Exception {
        MockHttpServletRequest request =
                new MockHttpServletRequest("POST", "/api/v1/demo/scenarios/clean-settlement/load");
        request.setRemoteAddr("203.0.113.10");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }
}
