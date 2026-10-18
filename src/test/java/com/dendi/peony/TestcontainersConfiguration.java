package com.dendi.peony;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/** Test configuration for integration tests with Testcontainers. */
@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {

  @Bean
  @ServiceConnection
  PostgreSQLContainer postgresContainer() {
    return new PostgreSQLContainer(DockerImageName.parse("postgres:latest"));
  }

  /** Provides ObjectMapper bean for Jackson JSON processing. */
  @Bean
  public ObjectMapper objectMapper() {
    return new ObjectMapper();
  }

  @PostConstruct
  public void setupTestEnvironment() {
    // Ensure test environment is properly configured
  }
}
