package com.dendi.peony.dto.auth;

import com.dendi.peony.entity.User;
import com.dendi.peony.entity.UserCapability;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

/**
 * Authenticated user profile including the access tier and its capability flags.
 *
 * @param id account id
 * @param username account username
 * @param email account email address
 * @param firstName account holder first name
 * @param lastName account holder last name
 * @param tier tier name in lowercase ({@code free} or {@code premium})
 * @param photoUploadsPerMonth monthly photo upload quota for the tier, {@code null} when unlimited
 * @param capabilities capability flags granted by the tier
 * @param createdAt account creation timestamp
 */
public record ProfileResponse(
    Long id,
    String username,
    String email,
    String firstName,
    String lastName,
    String tier,
    Integer photoUploadsPerMonth,
    List<UserCapability> capabilities,
    LocalDateTime createdAt) {

  /**
   * Builds the profile response for a stored account.
   *
   * @param user stored account
   * @return profile with tier and capability flags
   */
  public static ProfileResponse from(User user) {
    return new ProfileResponse(
        user.getId(),
        user.getUsername(),
        user.getEmail(),
        user.getFirstName(),
        user.getLastName(),
        user.getTier().name().toLowerCase(Locale.ROOT),
        user.getTier().getPhotoUploadsPerMonth(),
        user.getTier().getCapabilities(),
        user.getCreatedAt());
  }
}
