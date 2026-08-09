package io.github.heybadrinath.reconriver.workspace;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.heybadrinath.reconriver.shared.config.ReconRiverProperties;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            WorkspaceAuthenticationFilter authenticationFilter,
            RateLimitFilter rateLimitFilter,
            ObjectMapper objectMapper)
            throws Exception {
        http.csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(
                        session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(
                        auth ->
                                auth.requestMatchers(HttpMethod.OPTIONS, "/**")
                                        .permitAll()
                                        .requestMatchers(HttpMethod.POST, "/api/v1/demo/sessions")
                                        .permitAll()
                                        .requestMatchers(
                                                HttpMethod.GET, "/api/v1/demo/scenarios/**")
                                        .permitAll()
                                        .requestMatchers(
                                                "/actuator/health/**",
                                                "/actuator/info",
                                                "/v3/api-docs/**",
                                                "/docs",
                                                "/docs/**",
                                                "/swagger-ui/**")
                                        .permitAll()
                                        .anyRequest()
                                        .authenticated())
                .exceptionHandling(
                        errors ->
                                errors.authenticationEntryPoint(
                                                (request, response, exception) ->
                                                        writeProblem(
                                                                response,
                                                                objectMapper,
                                                                401,
                                                                "SESSION_REQUIRED",
                                                                "Start a demo workspace or replace the expired session token."))
                                        .accessDeniedHandler(
                                                (request, response, exception) ->
                                                        writeProblem(
                                                                response,
                                                                objectMapper,
                                                                403,
                                                                "ACCESS_DENIED",
                                                                "This demo session cannot access the requested resource.")))
                .headers(
                        headers ->
                                headers.contentTypeOptions(Customizer.withDefaults())
                                        .frameOptions(frame -> frame.deny())
                                        .httpStrictTransportSecurity(
                                                hsts ->
                                                        hsts.includeSubDomains(true)
                                                                .maxAgeInSeconds(31_536_000)));
        http.addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class);
        http.addFilterBefore(authenticationFilter, AnonymousAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(ReconRiverProperties properties) {
        List<String> origins = properties.security().allowedOrigins();
        if (origins == null
                || origins.isEmpty()
                || origins.stream().anyMatch(origin -> origin.contains("*"))) {
            throw new IllegalStateException(
                    "ALLOWED_ORIGINS must contain exact origins and cannot use wildcards");
        }
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(origins);
        configuration.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));
        configuration.setAllowedHeaders(
                List.of("Authorization", "Content-Type", "Accept", "Idempotency-Key"));
        configuration.setExposedHeaders(
                List.of(
                        "Content-Disposition",
                        "Retry-After",
                        "X-RateLimit-Limit",
                        "X-RateLimit-Remaining"));
        configuration.setAllowCredentials(false);
        configuration.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    FilterRegistrationBean<WorkspaceAuthenticationFilter> disableWorkspaceFilterAutoRegistration(
            WorkspaceAuthenticationFilter filter) {
        FilterRegistrationBean<WorkspaceAuthenticationFilter> registration =
                new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    FilterRegistrationBean<RateLimitFilter> disableRateFilterAutoRegistration(
            RateLimitFilter filter) {
        FilterRegistrationBean<RateLimitFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    private static void writeProblem(
            jakarta.servlet.http.HttpServletResponse response,
            ObjectMapper objectMapper,
            int status,
            String code,
            String detail)
            throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(
                response.getOutputStream(),
                Map.of(
                        "type",
                        "https://reconriver.dev/problems/" + code.toLowerCase().replace('_', '-'),
                        "title",
                        status == 401 ? "Unauthorized" : "Forbidden",
                        "status",
                        status,
                        "detail",
                        detail,
                        "code",
                        code));
    }
}
