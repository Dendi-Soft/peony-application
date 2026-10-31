package com.dendi.peony.service.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dendi.peony.config.JwtProperties;
import com.dendi.peony.entity.RefreshToken;
import com.dendi.peony.exception.InvalidRefreshTokenException;
import com.dendi.peony.repository.RefreshTokenRepository;
import com.dendi.peony.service.jwt.RefreshTokenService.IssuedRefreshToken;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Pins the storage and single-use rotation rules for opaque refresh tokens (spec #17). */
@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

  private static final Instant NOW = Instant.parse("2026-10-05T12:00:00Z");
  private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

  @Mock private RefreshTokenRepository refreshTokenRepository;

  private RefreshTokenService service;

  @BeforeEach
  void setUp() {
    JwtProperties properties =
        new JwtProperties("test-secret", Duration.ofHours(1), Duration.ofDays(30));
    service = new RefreshTokenService(refreshTokenRepository, properties, CLOCK);
  }

  @Test
  void testIssueRefreshToken_StoresOnlyTheHash_WithRefreshTtl() {
    IssuedRefreshToken issued = service.issueRefreshToken(7L);

    ArgumentCaptor<RefreshToken> saved = ArgumentCaptor.forClass(RefreshToken.class);
    verify(refreshTokenRepository).save(saved.capture());

    assertThat(issued.token()).matches("[A-Za-z0-9_-]{43}"); // 256 bits, Base64URL, no padding
    assertThat(saved.getValue().getTokenHash()).isEqualTo(sha256Hex(issued.token()));
    assertThat(saved.getValue().getTokenHash()).isNotEqualTo(issued.token());
    assertThat(saved.getValue().getUserId()).isEqualTo(7L);
    assertThat(saved.getValue().getExpiresAt())
        .isEqualTo(OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC).plusDays(30));
  }

  @Test
  void testRotateRefreshToken_RevokesPresentedToken_AndIssuesNewOne() {
    String presentedToken = "presented-refresh-token";
    String tokenHash = sha256Hex(presentedToken);
    RefreshToken stored =
        new RefreshToken(tokenHash, 7L, OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC).plusDays(1));
    when(refreshTokenRepository.findByTokenHashAndRevokedAtIsNull(tokenHash))
        .thenReturn(Optional.of(stored));
    when(refreshTokenRepository.revokeByTokenHash(
            tokenHash, OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC)))
        .thenReturn(1);

    IssuedRefreshToken rotated = service.rotateRefreshToken(presentedToken);

    assertThat(rotated.userId()).isEqualTo(7L);
    assertThat(rotated.token()).isNotEqualTo(presentedToken);
    verify(refreshTokenRepository)
        .revokeByTokenHash(tokenHash, OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC));
    verify(refreshTokenRepository).save(any(RefreshToken.class));
  }

  @Test
  void testRotateRefreshToken_ThrowsExpired_WhenRowIsPastItsExpiry() {
    String presentedToken = "expired-refresh-token";
    String tokenHash = sha256Hex(presentedToken);
    RefreshToken expired =
        new RefreshToken(
            tokenHash, 7L, OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC).minusMinutes(1));
    when(refreshTokenRepository.findByTokenHashAndRevokedAtIsNull(tokenHash))
        .thenReturn(Optional.of(expired));

    InvalidRefreshTokenException exception =
        assertThrows(
            InvalidRefreshTokenException.class, () -> service.rotateRefreshToken(presentedToken));

    assertEquals("Refresh token expired", exception.getMessage());
    verify(refreshTokenRepository, never()).revokeByTokenHash(any(), any());
    verify(refreshTokenRepository, never()).save(any());
  }

  @Test
  void testRotateRefreshToken_ThrowsInvalid_WhenTokenIsUnknownOrRevoked() {
    String presentedToken = "replayed-refresh-token";
    when(refreshTokenRepository.findByTokenHashAndRevokedAtIsNull(sha256Hex(presentedToken)))
        .thenReturn(Optional.empty());

    InvalidRefreshTokenException exception =
        assertThrows(
            InvalidRefreshTokenException.class, () -> service.rotateRefreshToken(presentedToken));

    assertEquals("Invalid or revoked refresh token", exception.getMessage());
    verify(refreshTokenRepository, never()).save(any());
  }

  @Test
  void testRotateRefreshToken_ThrowsInvalid_WhenConcurrentRotationWins() {
    String presentedToken = "raced-refresh-token";
    String tokenHash = sha256Hex(presentedToken);
    RefreshToken stored =
        new RefreshToken(tokenHash, 7L, OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC).plusDays(1));
    when(refreshTokenRepository.findByTokenHashAndRevokedAtIsNull(tokenHash))
        .thenReturn(Optional.of(stored));
    when(refreshTokenRepository.revokeByTokenHash(
            tokenHash, OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC)))
        .thenReturn(0);

    InvalidRefreshTokenException exception =
        assertThrows(
            InvalidRefreshTokenException.class, () -> service.rotateRefreshToken(presentedToken));

    assertEquals("Invalid or revoked refresh token", exception.getMessage());
    verify(refreshTokenRepository, never()).save(any());
  }

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
      throw new IllegalStateException(e);
    }
  }
}
