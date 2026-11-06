package com.dendi.peony.config;

import com.dendi.peony.service.plant.PlantNetImageDownloadClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * PlantNet species-image download configuration (issue #45, ADR-008).
 *
 * <p>Creates the allowlisted image downloader. The API key is never attached to image URLs, so the
 * client is built without credentials.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(PlantNetImageProperties.class)
public class PlantNetImageConfig {

  /**
   * Creates the PlantNet species-image download client.
   *
   * @param properties allowlisted download origin
   * @return the configured client
   */
  @Bean
  public PlantNetImageDownloadClient plantNetImageDownloadClient(
      PlantNetImageProperties properties) {
    return new PlantNetImageDownloadClient(properties.allowedOrigin());
  }
}
