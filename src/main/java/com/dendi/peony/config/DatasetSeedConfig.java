package com.dendi.peony.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Exposes {@link DatasetSeedProperties} to the context. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(DatasetSeedProperties.class)
public class DatasetSeedConfig {}
