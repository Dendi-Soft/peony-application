package com.dendi.peony.repository;

import com.dendi.peony.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Data access for refresh tokens (spec #17).
 *
 * <p>Lookup by {@code tokenHash} and rotation queries are added by the ticket that issues and
 * validates tokens; this repository currently exposes the base CRUD contract only.
 */
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {}
