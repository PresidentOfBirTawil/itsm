package com.itsm.api;

import com.itsm.config.Brand;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** The description of the API that the interactive documentation (/api/docs) is built from. */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI quellApi(Brand brand) {
        return new OpenAPI()
                .info(new Info()
                        .title(brand.getName() + " API")
                        .version("v1 (" + brand.getName() + " " + brand.getVersion() + ")")
                        .description("""
                                Report and follow incidents, check service status and search the knowledge base \
                                from your own tools: monitoring, chat bots, dashboards, scripts.

                                **Authentication.** Make a key in %s under *Account → API keys* and send it with \
                                every request: `Authorization: Bearer qk_...`. A key acts with exactly the \
                                permissions of its owner.

                                **Limits.** %s requests a minute per key; every answer has `X-RateLimit-Remaining`.

                                **Errors** use the standard *problem details* format (RFC 9457).

                                **Webhooks.** To be told about changes instead of asking, add a webhook under \
                                *System → Webhooks*.""".formatted(brand.getName(), "120")))
                .components(new Components().addSecuritySchemes("apiKey", new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("qk_...")
                        .description("An API key from Account → API keys")))
                .addSecurityItem(new SecurityRequirement().addList("apiKey"));
    }
}
