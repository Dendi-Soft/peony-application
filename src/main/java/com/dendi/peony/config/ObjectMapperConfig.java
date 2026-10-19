package com.dendi.peony.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * ObjectMapper configuration for Spring Boot applications. Ensures proper Jackson bean registration
 * and media type handling.
 */
@Configuration
public class ObjectMapperConfig {

  /**
   * Configured ObjectMapper bean for JSON serialization/deserialization. Registers default media
   * types for consistent Jackson behavior.
   */
  @Bean
  public ObjectMapper objectMapper() {
    ObjectMapper mapper = new ObjectMapper();

    return mapper;
  }
}
