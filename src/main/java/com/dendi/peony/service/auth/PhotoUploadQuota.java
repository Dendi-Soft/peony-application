package com.dendi.peony.service.auth;

/**
 * Read-side snapshot of a caller's monthly photo upload quota.
 *
 * <p>Both values are {@code null} for tiers without a monthly limit (premium). The snapshot is
 * informational only; enforcement still goes through {@link UploadRateLimitService#recordUpload}.
 *
 * @param used uploads recorded for the caller in the current server calendar month
 * @param remaining uploads the caller may still make this month, never negative
 */
public record PhotoUploadQuota(Integer used, Integer remaining) {}
