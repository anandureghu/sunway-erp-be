package com.erp.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI apiInfo() {

        // Define servers
        Server localServer = new Server()
                .url("http://localhost:8080")
                .description("Local Development Server");

        Server prodServer = new Server()
                .url("https://api.picominds.com")
                .description("Production Server");

        return new OpenAPI()
                .info(new Info()
                        .title("ERP System API")
                        .description("""
                                Auto-generated API documentation for HR, Finance, Inventory, and Auth modules.
                                Login may require email 2FA when enabled in the user's profile security settings
                                (POST /api/auth/login → OTP → /login/verify-2fa).
                                Password recovery: POST /api/auth/forgot-password → /reset-password (email + OTP code).
                                """)
                        .version("1.0.0")
                )
                .servers(List.of(localServer, prodServer))
                .addSecurityItem(new SecurityRequirement().addList("BearerAuth"))
                .components(new Components()
                        .addSecuritySchemes("BearerAuth",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                        )
                );
    }
}
