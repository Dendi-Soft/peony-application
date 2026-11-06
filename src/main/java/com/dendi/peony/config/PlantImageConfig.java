package com.dendi.peony.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Exposes {@link PlantImageProperties} to the context.
 *
 * <p>Binds {@code peony.plant-images.dir}, the storage location for curated catalogue images and
 * PlantNet species fallbacks (issues #10 and #45, ADR-008).
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(PlantImageProperties.class)
public class PlantImageConfig {}
