package com.dendi.peony.service.auth;

import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * Spring Security principal for a peony account.
 *
 * <p>Carries the account id so downstream services can load fresh account state (e.g., the tier
 * after an upgrade) instead of trusting a snapshot from sign-in time.
 *
 * @param userId account id
 * @param username account username
 * @param passwordHash hash of the account password
 */
public record PeonyUserDetails(Long userId, String username, String passwordHash)
    implements UserDetails {

  /**
   * Returns the authorities granted to the account.
   *
   * @return the single USER role
   */
  @Override
  public Collection<? extends GrantedAuthority> getAuthorities() {
    return List.of(new SimpleGrantedAuthority("ROLE_USER"));
  }

  /**
   * Returns the password hash.
   *
   * @return BCrypt hash of the account password
   */
  @Override
  public String getPassword() {
    return passwordHash;
  }

  /**
   * Returns the account username.
   *
   * @return username
   */
  @Override
  public String getUsername() {
    return username;
  }

  /**
   * Accounts never expire.
   *
   * @return always {@code true}
   */
  @Override
  public boolean isAccountNonExpired() {
    return true;
  }

  /**
   * Accounts cannot be locked.
   *
   * @return always {@code true}
   */
  @Override
  public boolean isAccountNonLocked() {
    return true;
  }

  /**
   * Password hashes stay valid until changed.
   *
   * @return always {@code true}
   */
  @Override
  public boolean isCredentialsNonExpired() {
    return true;
  }

  /**
   * Accounts are enabled by default.
   *
   * @return always {@code true}
   */
  @Override
  public boolean isEnabled() {
    return true;
  }
}
