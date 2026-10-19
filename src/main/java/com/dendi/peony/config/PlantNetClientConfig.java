package com.dendi.peony.config;

import com.dendi.peony.service.plantrecognition.PlantNetClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * PlantNet client configuration.
 *
 * <p>Creates the shared PlantNet client bean with the API key and retry settings from {@code
 * application.yml}. An empty API key uses the unauthenticated public endpoint.
 */
@Configuration
public class PlantNetClientConfig {

  /**
   * Creates the shared PlantNet API client.
   *
   * @param apiKey PlantNet API key, empty for the public endpoint
   * @param retryAttempts maximum attempts per identification request
   * @param retryBaseDelayMs initial retry backoff delay in milliseconds
   * @return configured PlantNet client
   */
  @Bean
  public PlantNetClient plantNetClient(
      @Value("${plantnet.api-key:}") String apiKey,
      @Value("${plantnet.retry-attempts:5}") int retryAttempts,
      @Value("${plantnet.retry-base-delay-ms:1000}") long retryBaseDelayMs) {
    return new PlantNetClient(
        apiKey.isEmpty() ? null : apiKey,
        PlantNetClient.DEFAULT_BASE_URL,
        retryAttempts,
        retryBaseDelayMs);
  }
}
