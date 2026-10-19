package com.dendi.peony.config;

import org.springframework.context.annotation.Configuration;

/**
 * Redis cache manager configuration for PlantNet API distributed caching. This class is kept for
 * potential Redis integration in the future, but currently database-backed caching is the primary
 * strategy.
 */
@Configuration
public class RedisCacheConfig {
  // Future Redis integration can be added here when needed
}
