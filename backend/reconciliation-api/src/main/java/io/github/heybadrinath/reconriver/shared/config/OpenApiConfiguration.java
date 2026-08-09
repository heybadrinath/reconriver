package io.github.heybadrinath.reconriver.shared.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class OpenApiConfiguration {

    private static final String BEARER_AUTH = "bearerAuth";

    @Bean
    OpenAPI applicationOpenApi() {
        return new OpenAPI()
                .info(
                        new Info()
                                .title("ReconRiver API")
                                .version("0.1.0")
                                .description(
                                        "Explainable payment reconciliation and settlement workbench API.")
                                .license(new License().name("MIT")))
                .servers(List.of(new Server().url("/")))
                .schemaRequirement(
                        BEARER_AUTH,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("opaque demo session token"))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH));
    }

    @Bean
    OpenApiCustomizer publicOperationSecurity() {
        return openApi -> {
            openApi.getPaths().get("/api/v1/demo/sessions").getPost().setSecurity(List.of());
            openApi.getPaths()
                    .forEach(
                            (path, item) -> {
                                if (path.startsWith("/api/v1/demo/scenarios")
                                        && item.getGet() != null) {
                                    item.getGet().setSecurity(List.of());
                                }
                            });
        };
    }
}
