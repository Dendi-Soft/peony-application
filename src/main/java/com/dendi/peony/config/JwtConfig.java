package com.dendi.peony.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Exposes {@link JwtProperties} to the context.
 *
 * <p>Binding the properties validates the signing secret: a blank {@code peony.jwt.secret} fails
 * the context refresh (see {@link JwtProperties}), so the application refuses to start without a
 * key (spec #17).
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(JwtProperties.class)
public class JwtConfig {}
