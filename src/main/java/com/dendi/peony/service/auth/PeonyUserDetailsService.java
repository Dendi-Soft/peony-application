package com.dendi.peony.service.auth;

import com.dendi.peony.entity.User;
import com.dendi.peony.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Loads peony accounts for Spring Security authentication.
 *
 * <p>Looks the account up by username first, then by email, so clients may sign in with either
 * identifier.
 */
@Service
@RequiredArgsConstructor
public class PeonyUserDetailsService implements UserDetailsService {

  private final UserRepository userRepository;

  /**
   * Loads the account matching the given username or email.
   *
   * @param identifier username or email address from the signin request
   * @return the principal for the account
   * @throws UsernameNotFoundException when no account matches
   */
  @Override
  public UserDetails loadUserByUsername(String identifier) throws UsernameNotFoundException {
    User user =
        userRepository
            .findByUsername(identifier)
            .or(() -> userRepository.findByEmail(identifier))
            .orElseThrow(() -> new UsernameNotFoundException("No account for " + identifier));
    return new PeonyUserDetails(user.getId(), user.getUsername(), user.getPasswordHash());
  }
}
