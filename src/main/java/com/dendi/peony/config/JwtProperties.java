package com.dendi.peony.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT configuration for the session -> JWT cutover (spec #17).
 *
 * <p>{@code peony.jwt.secret} has no default on purpose: binding fails (and startup is aborted)
 * when the secret is blank, so the application can never sign tokens without a key. The local and
 * test profiles supply a development secret; every other environment must provide one via {@code
 * JWT_SECRET}.
 *
 * @param secret HMAC signing secret for access and refresh tokens
 * @param accessTokenTtl lifetime of issued access tokens
 * @param refreshTokenTtl lifetime of issued refresh tokens
 */
@ConfigurationProperties(prefix = "peony.jwt")
public record JwtProperties(String secret, Duration accessTokenTtl, Duration refreshTokenTtl) {

  public JwtProperties {
    if (secret == null || secret.isBlank()) {
      throw new IllegalArgumentException(
          "peony.jwt.secret must not be blank; set the JWT_SECRET environment variable "
              + "(the local and test profiles supply a development secret)");
    }
  }
}
