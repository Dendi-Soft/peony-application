package com.dendi.peony.config;

import java.util.List;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Browser CORS allowlist configuration (issue #24).
 *
 * <p>{@code peony.cors.allowed-origins} is a comma-separated list bound from {@code
 * PEONY_CORS_ALLOWED_ORIGINS}. It is empty by default, which disables CORS entirely: no origins are
 * allowed and no CORS headers are returned. The local profile allows the Expo web dev server at
 * {@code http://localhost:8081}; production supplies its real web-app origin through the
 * environment variable, documented as {@code https://app.peony.example.com} until the domain is
 * chosen.
 *
 * <p>Entries are trimmed and blank entries are dropped, so an empty environment value cannot
 * accidentally become an allowlisted blank origin.
 *
 * @param allowedOrigins origins permitted to call the API cross-origin; empty disables CORS
 */
@ConfigurationProperties(prefix = "peony.cors")
public record CorsProperties(List<String> allowedOrigins) {

  public CorsProperties {
    allowedOrigins =
        allowedOrigins == null
            ? List.of()
            : allowedOrigins.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toList();
  }
}
