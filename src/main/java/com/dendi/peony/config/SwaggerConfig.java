package com.dendi.peony.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Swagger/OpenAPI configuration for API documentation. */
@Configuration
public class SwaggerConfig {

  /**
   * Creates the OpenAPI document for the Peony API.
   *
   * @return OpenAPI document with title, description, and server list
   */
  @Bean
  public OpenAPI peonyAPI() {
    return new OpenAPI()
        .info(
            new Info()
                .title("Peony API")
                .description(
                    """
                                Peony is a plant recognition application.

                                This API allows you to:
                                - Recognize plants from uploaded images (JPEG, PNG or WebP)""")
                .version("0.0.1-SNAPSHOT")
                .contact(new Contact().name("Peony Team").email("support@peony.example.com")))
        .servers(
            List.of(
                new Server().url("/").description("Local instance"),
                new Server()
                    .url("https://api.peony.example.com")
                    .description("Production server")));
  }
}
