package com.dendi.peony.service.jwt;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.dendi.peony.config.JwtProperties;
import java.time.Clock;
import java.util.Date;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Generates and validates JWT access tokens for the session -> JWT cutover (spec #17). */
@Service
@RequiredArgsConstructor
public class JwtTokenService {

  private final JwtProperties jwtProperties;
  private final Clock clock;

  /**
   * Creates a new access token.
   *
   * @param userId the user identifier to embed in the {@code sub} claim
   * @return the signed JWT as a compact string (header.payload.signature)
   */
  public String createAccessToken(Long userId) {
    Date now = Date.from(clock.instant());
    Date exp = new Date(now.getTime() + jwtProperties.getAccessTokenTtl().toMillis());

    Algorithm algorithm = Algorithm.HMAC256(jwtProperties.secret());

    return JWT.create()
        .withSubject(String.valueOf(userId))
        .withIssuedAt(now)
        .withExpiresAt(exp)
        .sign(algorithm);
  }

  /**
   * Validates and decodes an access token.
   *
   * @param token the compact JWT string to validate
   * @return the decoded JWT containing claims like {@code sub} (user id), {@code exp}, {@code iat}
   * @throws IllegalArgumentException when the signature or expiry is invalid
   */
  public DecodedJWT validateAccessToken(String token) {
    try {
      Algorithm algorithm = Algorithm.HMAC256(jwtProperties.secret());
      // build(Clock) lives on the concrete verification type, not the Verification interface.
      JWTVerifier.BaseVerification verification =
          (JWTVerifier.BaseVerification) JWT.require(algorithm);
      return verification.build(clock).verify(token);
    } catch (Exception e) {
      throw new IllegalArgumentException("Invalid access token", e);
    }
  }

  /**
   * Extracts the user ID from an access token.
   *
   * @param token the compact JWT string
   * @return the numeric user id embedded in the {@code sub} claim
   */
  public Long getUserIdFromAccessToken(String token) {
    DecodedJWT jwt = validateAccessToken(token);
    String subject = jwt.getSubject();
    if (subject == null || subject.isEmpty()) {
      throw new IllegalArgumentException("Access token must contain a user id in the sub claim");
    }
    try {
      return Long.valueOf(subject);
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException("User id in access token is not numeric", e);
    }
  }
}
