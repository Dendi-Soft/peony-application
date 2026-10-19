package com.dendi.peony.repository;

import com.dendi.peony.entity.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Data access for user accounts. */
public interface UserRepository extends JpaRepository<User, Long> {

  /**
   * Finds the account with the given username.
   *
   * @param username username to look up
   * @return the account, if present
   */
  Optional<User> findByUsername(String username);

  /**
   * Finds the account with the given email address.
   *
   * @param email email address to look up
   * @return the account, if present
   */
  Optional<User> findByEmail(String email);
}
