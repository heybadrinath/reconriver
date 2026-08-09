package io.github.heybadrinath.reconriver.shared.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class JsonConfig {
    @Bean
    ObjectMapper applicationObjectMapper() {
        return new ObjectMapper().findAndRegisterModules();
    }
}
