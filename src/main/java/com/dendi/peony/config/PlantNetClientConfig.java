package com.dendi.peony.config;

import com.dendi.peony.service.plantrecognition.PlantNetCacheService;
import com.dendi.peony.service.plantrecognition.PlantNetClient;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * PlantNet client configuration. Configures the PlantNet client with caching support for rate limit
 * mitigation.
 */
@Configuration
public class PlantNetClientConfig {

  private PlantNetClient plantNetClient;

  /**
   * Creates PlantNet client instance configured with cache service. This wiring ensures all API
   * calls automatically benefit from caching.
   *
   * @param cacheService Cache service for species data persistence
   */
  @Bean
  public PlantNetClient plantNetClient(PlantNetCacheService cacheService) {
    this.plantNetClient = new PlantNetClient(cacheService);
    return this.plantNetClient;
  }

  /** Post-construct hook to ensure the bean is properly initialized. */
  @PostConstruct
  public void init() {
    // Ensure plantNetClient bean is created with proper initialization
  }
}
