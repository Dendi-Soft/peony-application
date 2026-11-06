package com.dendi.peony.config;

import com.dendi.peony.dataset.CatalogueImageDownloader;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Curated catalogue image seeding configuration (issue #9 follow-up).
 *
 * <p>Creates the downloader pinned to Wikimedia's media host. Wikimedia image URLs are public, so
 * no credentials are attached.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(CatalogueImageProperties.class)
public class CatalogueImageConfig {

  /** The only origin the catalogue image seeder downloads from. */
  public static final String ALLOWED_ORIGIN = "https://upload.wikimedia.org";

  /**
   * Creates the allowlisted catalogue image downloader.
   *
   * @return the configured downloader
   */
  @Bean
  public CatalogueImageDownloader catalogueImageDownloader() {
    return new CatalogueImageDownloader(ALLOWED_ORIGIN);
  }
}
