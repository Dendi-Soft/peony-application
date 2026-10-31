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
 * <p>{@code peony.jwt.access-token-ttl} defaults to 1 hour; {@code peony.jwt.refresh-token-ttl}
 * defaults to 30 days. These can be overridden per-environment but should remain within the 1-5
 * hour band for access tokens and ~30 days for refresh tokens.
 *
 * @param secret HMAC signing secret for access and refresh tokens
 * @param accessTokenTtl lifetime of issued access tokens (default: 1h)
 * @param refreshTokenTtl lifetime of issued refresh tokens (default: 30d)
 */
@ConfigurationProperties(prefix = "peony.jwt")
public record JwtProperties(String secret, Duration accessTokenTtl, Duration refreshTokenTtl) {

  private static final Duration DEFAULT_ACCESS_TOKEN_TTL = Duration.ofHours(1);
  private static final Duration DEFAULT_REFRESH_TOKEN_TTL = Duration.ofDays(30);

  public JwtProperties {
    if (secret == null || secret.isBlank()) {
      throw new IllegalArgumentException(
          "peony.jwt.secret must not be blank; set the JWT_SECRET environment variable "
              + "(the local and test profiles supply a development secret)");
    }
  }

  /** Returns access token TTL, using default of 1 hour if not configured. */
  public Duration getAccessTokenTtl() {
    return accessTokenTtl != null ? accessTokenTtl : DEFAULT_ACCESS_TOKEN_TTL;
  }

  /** Returns refresh token TTL, using default of 30 days if not configured. */
  public Duration getRefreshTokenTtl() {
    return refreshTokenTtl != null ? refreshTokenTtl : DEFAULT_REFRESH_TOKEN_TTL;
  }
}
