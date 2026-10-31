package com.dendi.peony.service.jwt;

import com.dendi.peony.config.JwtProperties;
import com.dendi.peony.entity.RefreshToken;
import com.dendi.peony.exception.InvalidRefreshTokenException;
import com.dendi.peony.repository.RefreshTokenRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Base64;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Manages opaque refresh tokens for the session -> JWT cutover (spec #17).
 *
 * <p>Tokens are 256-bit random Base64URL strings. Only their SHA-256 hash is stored, so a database
 * leak does not hand out live sign-ins. Refresh is single-use: rotation revokes the presented row
 * and inserts a new one.
 */
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

  private static final SecureRandom SECURE_RANDOM = new SecureRandom();
  private static final int TOKEN_BYTES = 32;

  private final RefreshTokenRepository refreshTokenRepository;
  private final JwtProperties jwtProperties;
  private final Clock clock;

  /**
   * A freshly issued refresh token.
   *
   * @param userId owner of the token
   * @param token the raw token returned to the client (never stored)
   * @param expiresAt instant after which the token must not be accepted
   */
  public record IssuedRefreshToken(Long userId, String token, OffsetDateTime expiresAt) {}

  /**
   * Issues a new opaque refresh token and stores its hash.
   *
   * @param userId owner of the token
   * @return the raw token and its expiry
   */
  @Transactional
  public IssuedRefreshToken issueRefreshToken(Long userId) {
    String token = generateToken();
    OffsetDateTime expiresAt = OffsetDateTime.now(clock).plus(jwtProperties.getRefreshTokenTtl());
    refreshTokenRepository.save(new RefreshToken(sha256Hex(token), userId, expiresAt));
    return new IssuedRefreshToken(userId, token, expiresAt);
  }

  /**
   * Single-use rotation: revokes the presented token and issues a new one.
   *
   * <p>The revoke is a conditional {@code UPDATE}, so concurrent refreshes with the same token
   * cannot both succeed: exactly one gets the new pair.
   *
   * @param presentedToken the raw token sent by the client
   * @return the new token and the id of the user it belongs to
   * @throws InvalidRefreshTokenException when the token is unknown, revoked, or expired
   */
  @Transactional
  public IssuedRefreshToken rotateRefreshToken(String presentedToken) {
    String tokenHash = sha256Hex(presentedToken);

    RefreshToken stored =
        refreshTokenRepository
            .findByTokenHashAndRevokedAtIsNull(tokenHash)
            .orElseThrow(
                () -> new InvalidRefreshTokenException("Invalid or revoked refresh token"));

    if (stored.getExpiresAt().isBefore(OffsetDateTime.now(clock))) {
      throw new InvalidRefreshTokenException("Refresh token expired");
    }

    int revoked = refreshTokenRepository.revokeByTokenHash(tokenHash, OffsetDateTime.now(clock));
    if (revoked == 0) {
      // A concurrent request rotated this token between the lookup and the update.
      throw new InvalidRefreshTokenException("Invalid or revoked refresh token");
    }

    return issueRefreshToken(stored.getUserId());
  }

  /** Returns a fresh 256-bit random Base64URL token. */
  private static String generateToken() {
    byte[] bytes = new byte[TOKEN_BYTES];
    SECURE_RANDOM.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  /** Computes the SHA-256 hex digest stored as the lookup key. */
  private static String sha256Hex(String input) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hashBytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));

      StringBuilder hexString = new StringBuilder();
      for (byte b : hashBytes) {
        String hex = Integer.toHexString(0xff & b);
        if (hex.length() == 1) {
          hexString.append('0');
        }
        hexString.append(hex);
      }
      return hexString.toString();
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is not available", e);
    }
  }
}
