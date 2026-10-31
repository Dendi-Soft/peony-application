package com.dendi.peony.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A refresh token issued to a user for the session -> JWT cutover (spec #17).
 *
 * <p>Maps to the {@code refresh_tokens} table (changeset 005). Only the SHA-256 hash of the opaque
 * token is stored, never the token itself; rotation revokes the presented row ({@code revokedAt})
 * and inserts a new one. The row is deleted with its user.
 */
@Entity
@Table(name = "refresh_tokens")
@Getter
@Setter
@NoArgsConstructor
public class RefreshToken implements Serializable {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  /** SHA-256 hex digest of the opaque token; the token itself is never stored. */
  @Column(name = "token_hash", nullable = false, unique = true, length = 64)
  private String tokenHash;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(name = "expires_at", nullable = false)
  private OffsetDateTime expiresAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  /** When the token was revoked by rotation or signout; {@code null} while active. */
  @Column(name = "revoked_at")
  private OffsetDateTime revokedAt;

  /**
   * Creates an active token for a user.
   *
   * @param tokenHash SHA-256 hex digest of the opaque token
   * @param userId user the token is issued to
   * @param expiresAt instant after which the token must not be accepted
   */
  public RefreshToken(String tokenHash, Long userId, OffsetDateTime expiresAt) {
    this.tokenHash = tokenHash;
    this.userId = userId;
    this.expiresAt = expiresAt;
    this.createdAt = OffsetDateTime.now();
  }

  /** Returns a new instance with revoked_at set. */
  public RefreshToken withRevokedAt(OffsetDateTime revokedAt) {
    RefreshToken copy = new RefreshToken(this.tokenHash, this.userId, this.expiresAt);
    copy.revokedAt = revokedAt;
    return copy;
  }
}
