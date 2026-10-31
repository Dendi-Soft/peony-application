package com.dendi.peony.service.auth;

import jakarta.annotation.Nonnull;
import java.util.Collection;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/** Spring Security principal wrapper for Peony user accounts. */
@Getter
public class PeonyUserDetails implements UserDetails {

  private final Long userId;
  private final String username;
  private final String passwordHash;

  /**
   * Creates a new PeonyUserDetails instance.
   *
   * @param userId the numeric user identifier
   * @param username the account's login name
   * @param passwordHash the BCrypt-hashed password for verification
   */
  public PeonyUserDetails(
      @Nonnull Long userId, @Nonnull String username, @Nonnull String passwordHash) {
    this.userId = userId;
    this.username = username;
    this.passwordHash = passwordHash;
  }

  /** Returns the numeric user identifier. */
  public Long getUserId() {
    return userId;
  }

  /** Returns the numeric user identifier (Lombok-style). */
  public Long userId() {
    return userId;
  }

  @Override
  public String getPassword() {
    return passwordHash;
  }

  @Override
  public String getUsername() {
    return username;
  }

  @Override
  public boolean isAccountNonExpired() {
    return true;
  }

  @Override
  public boolean isAccountNonLocked() {
    return true;
  }

  @Override
  public boolean isCredentialsNonExpired() {
    return true;
  }

  @Override
  public boolean isEnabled() {
    return true;
  }

  @Override
  public Collection<? extends GrantedAuthority> getAuthorities() {
    return java.util.Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"));
  }
}
