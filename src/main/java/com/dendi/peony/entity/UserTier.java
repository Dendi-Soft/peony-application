package com.dendi.peony.entity;

import java.util.List;

/**
 * Access tier of a user account.
 *
 * <p>Each tier defines the {@link UserCapability} flags granted to its users and the monthly photo
 * upload quota ({@code null} means unlimited). Free accounts are created at signup; the upgrade
 * endpoint moves a user to {@link #PREMIUM}.
 */
public enum UserTier {

  /** Basic tier: limited recognition results and a monthly photo upload quota. */
  FREE(
      List.of(
          UserCapability.PLANTS_READ,
          UserCapability.PLANTS_CREATE,
          UserCapability.PLANTS_UPDATE_NOTES,
          UserCapability.RECOGNITION_BASIC),
      5),

  /** Paid tier: complete recognition results, unlimited uploads, sync and analytics. */
  PREMIUM(
      List.of(
          UserCapability.PLANTS_READ,
          UserCapability.PLANTS_CREATE,
          UserCapability.PLANTS_UPDATE_NOTES,
          UserCapability.RECOGNITION_COMPLETE,
          UserCapability.MULTI_DEVICE_SYNC,
          UserCapability.ADVANCED_ANALYTICS),
      null);

  private final List<UserCapability> capabilities;
  private final Integer photoUploadsPerMonth;

  UserTier(List<UserCapability> capabilities, Integer photoUploadsPerMonth) {
    this.capabilities = capabilities;
    this.photoUploadsPerMonth = photoUploadsPerMonth;
  }

  /**
   * Returns the capability flags granted by this tier.
   *
   * @return unmodifiable list of capabilities
   */
  public List<UserCapability> getCapabilities() {
    return capabilities;
  }

  /**
   * Returns the monthly photo upload quota for this tier.
   *
   * @return quota, or {@code null} when the tier has no limit
   */
  public Integer getPhotoUploadsPerMonth() {
    return photoUploadsPerMonth;
  }
}
