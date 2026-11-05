package com.dendi.peony.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Exposes {@link UserImageProperties} to the context.
 *
 * <p>Binds {@code peony.user-images.dir}, the storage location for private per-individual photos
 * (issue #44, ADR-008).
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(UserImageProperties.class)
public class UserImageConfig {}
