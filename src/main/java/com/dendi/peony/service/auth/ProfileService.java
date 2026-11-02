package com.dendi.peony.service.auth;

import com.dendi.peony.api.model.ProfileResponse;
import com.dendi.peony.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Builds profile responses with the caller's current monthly upload quota.
 *
 * <p>Owns the composition of the stored account, its tier contract, and the read-side quota
 * snapshot so every endpoint that returns a profile reports usage the same way.
 */
@Service
@RequiredArgsConstructor
public class ProfileService {

  private final UploadRateLimitService uploadRateLimitService;

  /**
   * Builds the profile response for a stored account.
   *
   * @param user stored account
   * @return profile with tier, capability flags, and quota usage
   */
  public ProfileResponse profileFor(User user) {
    return ProfileResponseMapper.from(user, uploadRateLimitService.snapshot(user));
  }
}
