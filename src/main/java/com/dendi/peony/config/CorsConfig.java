package com.dendi.peony.config;

import java.util.List;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Browser CORS configuration (issue #24).
 *
 * <p>Registers the allowlisted origins from {@link CorsProperties} for every API path. When the
 * allowlist is empty nothing is registered, so no request receives CORS headers and CORS is
 * effectively disabled. Credentials are not allowed: authentication is Bearer-only (ADR-005), so
 * cookies and session credentials never need to cross origins.
 *
 * <p>Spring Security installs the CORS filter ahead of the JWT and authorization filters, so an
 * allowed preflight request succeeds without a Bearer token.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(CorsProperties.class)
public class CorsConfig {

  private static final List<String> ALLOWED_METHODS =
      List.of("GET", "POST", "PATCH", "DELETE", "OPTIONS");
  private static final List<String> ALLOWED_HEADERS =
      List.of("Authorization", "Content-Type", "Accept");
  private static final long PREFLIGHT_MAX_AGE_SECONDS = 3600L;

  /**
   * Creates the CORS configuration source used by the security filter chain.
   *
   * <p>An empty allowlist registers no configuration, which disables CORS entirely.
   *
   * @param properties configured allowlist
   * @return the CORS source for the API paths
   */
  @Bean
  public CorsConfigurationSource corsConfigurationSource(CorsProperties properties) {
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    List<String> allowedOrigins = properties.allowedOrigins();
    if (!allowedOrigins.isEmpty()) {
      CorsConfiguration configuration = new CorsConfiguration();
      configuration.setAllowedOrigins(allowedOrigins);
      configuration.setAllowedMethods(ALLOWED_METHODS);
      configuration.setAllowedHeaders(ALLOWED_HEADERS);
      configuration.setAllowCredentials(false);
      configuration.setMaxAge(PREFLIGHT_MAX_AGE_SECONDS);
      source.registerCorsConfiguration("/**", configuration);
    }
    return source;
  }
}
