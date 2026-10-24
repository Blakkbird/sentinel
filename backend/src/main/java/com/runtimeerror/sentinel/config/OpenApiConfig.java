package com.runtimeerror.sentinel.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

/**
 * Tells Swagger UI that the decision API wants an X-Api-Key header. Swagger then shows an
 * "Authorize" button: paste the key once and every "Try it out" call sends it.
 */
@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "Sentinel decision API",
                version = "v1",
                description = "Real-time payment risk decisions. Authenticate with an API key in the X-Api-Key header."),
        security = @SecurityRequirement(name = "apiKey"))
@SecurityScheme(name = "apiKey", type = SecuritySchemeType.APIKEY, in = SecuritySchemeIn.HEADER, paramName = "X-Api-Key")
public class OpenApiConfig {
}
