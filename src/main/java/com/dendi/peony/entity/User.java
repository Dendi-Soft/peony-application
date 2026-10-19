package com.dendi.peony.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A registered Peony account with an access tier.
 *
 * <p>Maps to the {@code users} table (changeset 001) extended with the tier column (changeset 003).
 * Passwords are stored only as a hash in {@code password_hash}, never in plain text.
 */
@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
public class User implements Serializable {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, unique = true)
  private String username;

  @Column(nullable = false, unique = true)
  private String email;

  @Column(name = "password_hash", nullable = false)
  private String passwordHash;

  @Column(name = "first_name", nullable = false)
  private String firstName;

  @Column(name = "last_name", nullable = false)
  private String lastName;

  /** Access tier; new accounts are created as {@link UserTier#FREE}. */
  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private UserTier tier;

  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  /**
   * Creates a new account with the given credentials, names, and tier.
   *
   * @param username unique username
   * @param email unique email address
   * @param passwordHash hash of the account password
   * @param firstName account holder first name
   * @param lastName account holder last name
   * @param tier access tier granted at creation
   * @return a new, unsaved account
   */
  public static User create(
      String username,
      String email,
      String passwordHash,
      String firstName,
      String lastName,
      UserTier tier) {
    User user = new User();
    user.username = username;
    user.email = email;
    user.passwordHash = passwordHash;
    user.firstName = firstName;
    user.lastName = lastName;
    user.tier = tier;
    LocalDateTime now = LocalDateTime.now();
    user.createdAt = now;
    user.updatedAt = now;
    return user;
  }

  /**
   * Moves the account to the given tier and refreshes {@code updated_at}.
   *
   * @param tier new access tier
   */
  public void setTierAndTouch(UserTier tier) {
    this.tier = tier;
    this.updatedAt = LocalDateTime.now();
  }
}
