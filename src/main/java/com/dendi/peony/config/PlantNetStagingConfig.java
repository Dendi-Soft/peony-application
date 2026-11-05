package com.dendi.peony.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Exposes {@link PlantNetStagingProperties} to the context.
 *
 * <p>Binds {@code peony.plantnet-staging.ttl}, the lifetime of a staged PlantNet species-image
 * candidate (issue #45, ADR-008).
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(PlantNetStagingProperties.class)
public class PlantNetStagingConfig {}
