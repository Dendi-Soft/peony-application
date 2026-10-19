package com.dendi.peony.service.auth;

import com.dendi.peony.dto.auth.SignupRequest;
import com.dendi.peony.entity.User;
import com.dendi.peony.entity.UserTier;
import com.dendi.peony.exception.DuplicateCredentialsException;
import com.dendi.peony.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Account management: creation and lookup of user accounts. */
@Service
@RequiredArgsConstructor
public class UserService {

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;

  /**
   * Creates a new free-tier account from a validated signup request.
   *
   * @param request signup credentials
   * @return the saved account
   * @throws DuplicateCredentialsException when the username or email is already taken
   */
  @Transactional
  public User signup(SignupRequest request) {
    if (userRepository.findByUsername(request.username()).isPresent()
        || userRepository.findByEmail(request.email()).isPresent()) {
      throw new DuplicateCredentialsException("Username or email already taken");
    }
    User user =
        User.create(
            request.username(),
            request.email(),
            passwordEncoder.encode(request.password()),
            request.firstName(),
            request.lastName(),
            UserTier.FREE);
    return userRepository.save(user);
  }

  /**
   * Loads a fresh account by id.
   *
   * @param id account id
   * @return the account
   * @throws IllegalStateException when no account exists with the id
   */
  @Transactional(readOnly = true)
  public User getById(Long id) {
    return userRepository
        .findById(id)
        .orElseThrow(() -> new IllegalStateException("Unknown user: " + id));
  }

  /**
   * Moves the account to the premium tier. Idempotent for accounts already premium.
   *
   * @param id account id
   * @return the updated account
   * @throws IllegalStateException when no account exists with the id
   */
  @Transactional
  public User upgradeToPremium(Long id) {
    User user = getById(id);
    user.setTierAndTouch(UserTier.PREMIUM);
    return userRepository.save(user);
  }
}
