package com.dendi.peony.service.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dendi.peony.api.model.AuthResponse;
import com.dendi.peony.api.model.ProfileResponse;
import com.dendi.peony.api.model.RefreshRequest;
import com.dendi.peony.entity.User;
import com.dendi.peony.entity.UserTier;
import com.dendi.peony.exception.InvalidRefreshTokenException;
import com.dendi.peony.service.jwt.JwtTokenService;
import com.dendi.peony.service.jwt.RefreshTokenService;
import com.dendi.peony.service.jwt.RefreshTokenService.IssuedRefreshToken;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;

/**
 * Tests for the refresh flow in {@link JwtAuthService} (PR #17): a valid refresh returns a new
 * pair, while expired and replayed tokens surface as {@link InvalidRefreshTokenException}.
 *
 * <p>The rotation mechanics themselves (hashing, atomic revoke, insertion) are pinned in {@link
 * com.dendi.peony.service.jwt.RefreshTokenServiceTest}.
 */
@ExtendWith(MockitoExtension.class)
class JwtAuthServiceRotationTest {

  @Mock private UserService userService;

  @Mock private AuthenticationManager authenticationManager;

  @Mock private JwtTokenService jwtTokenService;

  @Mock private RefreshTokenService refreshTokenService;

  @InjectMocks private JwtAuthService authService;

  /** Scenario 1: valid refresh token → new token pair and profile. */
  @Nested
  @DisplayName("Scenario 1: Valid refresh token rotation")
  class ValidRefreshTokenRotation {

    @Test
    void testValidRefresh_ReturnsNewTokensAndProfile() {
      Long userId = 1L;
      String oldRefreshToken = "old-refresh-token";
      User user =
          User.create("testuser", "test@example.com", "hash", "Test", "User", UserTier.FREE);
      user.setId(userId);

      when(refreshTokenService.rotateRefreshToken(oldRefreshToken))
          .thenReturn(
              new IssuedRefreshToken(
                  userId, "new-refresh-token", OffsetDateTime.now().plusDays(30)));
      when(userService.getById(userId)).thenReturn(user);
      when(jwtTokenService.createAccessToken(userId)).thenReturn("new-access-token");

      AuthResponse response =
          authService.refresh(new RefreshRequest().refreshToken(oldRefreshToken));

      assertThat(response.getToken()).isEqualTo("new-access-token");
      assertThat(response.getRefreshToken()).isEqualTo("new-refresh-token");
      assertThat(response.getProfile().getId()).isEqualTo(userId);
      assertThat(response.getProfile().getTier()).isEqualTo(ProfileResponse.TierEnum.FREE);
      verify(refreshTokenService).rotateRefreshToken(oldRefreshToken);
    }
  }

  /** Scenario 2: expired refresh token → invalid-refresh exception. */
  @Nested
  @DisplayName("Scenario 2: Expired refresh token rejection")
  class ExpiredRefreshTokenRejection {

    @Test
    void testExpiredRefresh_ThrowsRefreshTokenExpired() {
      String expiredRefreshToken = "expired-refresh-token";
      when(refreshTokenService.rotateRefreshToken(expiredRefreshToken))
          .thenThrow(new InvalidRefreshTokenException("Refresh token expired"));

      InvalidRefreshTokenException exception =
          assertThrows(
              InvalidRefreshTokenException.class,
              () -> authService.refresh(new RefreshRequest().refreshToken(expiredRefreshToken)));

      assertEquals("Refresh token expired", exception.getMessage());
    }
  }

  /** Scenario 3: replayed (already rotated) refresh token → invalid-refresh exception. */
  @Nested
  @DisplayName("Scenario 3: Already-rotated (replayed) refresh token rejection")
  class ReplayedRefreshTokenRejection {

    @Test
    void testReplayedRefresh_ThrowsInvalidOrRevokedRefreshToken() {
      String oldRefreshToken = "replayed-refresh-token";
      when(refreshTokenService.rotateRefreshToken(oldRefreshToken))
          .thenThrow(new InvalidRefreshTokenException("Invalid or revoked refresh token"));

      InvalidRefreshTokenException exception =
          assertThrows(
              InvalidRefreshTokenException.class,
              () -> authService.refresh(new RefreshRequest().refreshToken(oldRefreshToken)));

      assertEquals("Invalid or revoked refresh token", exception.getMessage());
    }
  }
}
