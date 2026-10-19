package com.dendi.peony.service.auth;

import com.dendi.peony.api.model.ProfileResponse;
import com.dendi.peony.entity.User;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Maps a stored account to the generated profile wire model.
 *
 * <p>Tier and capability flags are carried by the generated enums, which serialize to the same
 * lowercase tier names and upper-case capability names the profile contract pins.
 */
public final class ProfileResponseMapper {

  private ProfileResponseMapper() {}

  /**
   * Builds the profile response for a stored account.
   *
   * @param user stored account
   * @return profile with tier and capability flags
   */
  public static ProfileResponse from(User user) {
    ProfileResponse profile = new ProfileResponse();
    profile.setId(user.getId());
    profile.setUsername(user.getUsername());
    profile.setEmail(user.getEmail());
    profile.setFirstName(user.getFirstName());
    profile.setLastName(user.getLastName());
    profile.setTier(
        ProfileResponse.TierEnum.fromValue(user.getTier().name().toLowerCase(Locale.ROOT)));
    profile.setPhotoUploadsPerMonth(user.getTier().getPhotoUploadsPerMonth());
    profile.setCapabilities(
        user.getTier().getCapabilities().stream()
            .map(capability -> ProfileResponse.CapabilitiesEnum.fromValue(capability.name()))
            .collect(Collectors.toList()));
    // The wire contract pins createdAt to a plain string: format the stored timestamp the way
    // Jackson serializes a LocalDateTime so the payload is byte-identical
    profile.setCreatedAt(user.getCreatedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
    return profile;
  }
}
