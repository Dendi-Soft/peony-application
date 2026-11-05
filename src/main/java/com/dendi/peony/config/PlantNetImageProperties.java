package com.dendi.peony.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * PlantNet species-image download configuration (issue #45, ADR-008).
 *
 * <p>{@code peony.plantnet-image.allowed-origin} pins the only origin the backend will download
 * from, so an upstream-controlled URL cannot make the server fetch an arbitrary host. The default
 * in {@code application.yml} is {@code https://bs.plantnet.org}; tests override it with their
 * MockWebServer origin.
 *
 * @param allowedOrigin scheme, host, and port allowed for image downloads
 */
@ConfigurationProperties(prefix = "peony.plantnet-image")
public record PlantNetImageProperties(String allowedOrigin) {}
