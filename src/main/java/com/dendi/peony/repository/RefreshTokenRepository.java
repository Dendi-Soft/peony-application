package com.dendi.peony.repository;

import com.dendi.peony.entity.RefreshToken;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

/**
 * Data access for refresh tokens (spec #17).
 *
 * <p>Lookup by {@code tokenHash} and rotation queries are added by the ticket that issues and
 * validates tokens.
 */
@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

  /** Find all unrevoked refresh tokens for a user (not yet expired). */
  List<RefreshToken> findByUserIdAndRevokedAtIsNullOrderByExpiresAtAsc(Long userId);

  /** Find all revoked refresh tokens for a user. */
  List<RefreshToken> findByUserIdAndRevokedAtIsNotNull(Long userId);

  /** Find unrevoked refresh token by hash (tokenHash column). */
  Optional<RefreshToken> findByTokenHashAndRevokedAtIsNull(String tokenHash);

  /**
   * Atomically revoke the token with the given hash if it is still active.
   *
   * <p>The returned update count is the arbiter of concurrent refreshes: exactly one request sees
   * {@code 1}, every other sees {@code 0}.
   *
   * @param tokenHash SHA-256 hex digest of the presented token
   * @param now revocation timestamp
   * @return 1 when the row was revoked, 0 when it was already revoked or absent
   */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      """
    UPDATE RefreshToken rt SET rt.revokedAt = :now
    WHERE rt.tokenHash = :tokenHash AND rt.revokedAt IS NULL
      """)
  int revokeByTokenHash(String tokenHash, OffsetDateTime now);

  /** Delete all tokens for a user and mark as revoked. */
  @Query(
      """
    UPDATE RefreshToken rt SET rt.revokedAt = :now WHERE rt.userId = :userId AND rt.revokedAt IS NULL
      """)
  int deleteByUserIdAndMarkAsRevoked(Long userId, OffsetDateTime now);

  /** Check if any active token exists for a user. */
  boolean existsByUserIdAndRevokedAtIsNullAndExpiresAtAfter(Long userId, OffsetDateTime now);

  /** Count unrevoked tokens for a user. */
  long countByUserIdAndRevokedAtIsNull(Long userId);

  /** Find active refresh token by user ID (not revoked and not expired). */
  Optional<RefreshToken> findByUserIdOrderByRevokedAtDesc(Long userId);

  /** Find all refresh tokens for a user. */
  List<RefreshToken> findByUserId(Long userId);

  /** Find unrevoked refresh tokens for a user. */
  List<RefreshToken> findByUserIdAndRevokedAtIsNull(Long userId);

  /** Delete all tokens for a user (existing method not yet used). */
  void deleteByUserId(Long userId);
}
