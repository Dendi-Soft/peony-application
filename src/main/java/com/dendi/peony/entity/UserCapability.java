package com.dendi.peony.entity;

/**
 * Capability flags exposed on tiered API responses (profile, recognition, ...).
 *
 * <p>The free and premium tiers grant different subsets of these capabilities; the mapping lives in
 * {@link UserTier}.
 */
public enum UserCapability {

  /** Read plant records the user owns. */
  PLANTS_READ,

  /** Register plants manually. */
  PLANTS_CREATE,

  /** Update the per-plant notes of plants the user owns. */
  PLANTS_UPDATE_NOTES,

  /** Basic plant recognition results (top match with limited species detail). */
  RECOGNITION_BASIC,

  /** Complete recognition results with full species detail. */
  RECOGNITION_COMPLETE,

  /** Synchronize plant data across devices (flag only; sync is future work). */
  MULTI_DEVICE_SYNC,

  /** Advanced analytics over the user's plant data. */
  ADVANCED_ANALYTICS
}
