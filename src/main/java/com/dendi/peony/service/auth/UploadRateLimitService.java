package com.dendi.peony.service.auth;

import com.dendi.peony.entity.User;
import com.dendi.peony.entity.UserTier;
import com.dendi.peony.entity.UserUploadCounter;
import com.dendi.peony.exception.UploadLimitExceededException;
import com.dendi.peony.repository.UserUploadCounterRepository;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Enforces the per-tier monthly photo upload quota.
 *
 * <p>Quota-limited uploads are counted in a database row per user per calendar month ({@code
 * yyyy-MM}) so the limit survives restarts; unlimited tiers are not counted. The clock is injected
 * so tests can move the calendar deterministically.
 */
@Service
@RequiredArgsConstructor
public class UploadRateLimitService {

  private final UserUploadCounterRepository counterRepository;
  private final Clock clock;

  /**
   * Records one photo upload for the user, rejecting it when the tier's monthly quota is exhausted.
   *
   * @param tier the user's tier at request time
   * @param userId the user's id
   * @throws UploadLimitExceededException when the user has reached the monthly limit
   */
  @Transactional
  public void recordUpload(UserTier tier, Long userId) {
    Integer limit = tier.getPhotoUploadsPerMonth();
    if (limit == null) {
      return;
    }
    UploadPeriod period = UploadPeriod.current(clock);
    UserUploadCounter counter =
        counterRepository
            .findLockedForUpdate(userId, period.stored())
            .orElseGet(() -> new UserUploadCounter(userId, period.stored()));
    if (counter.getCount() >= limit) {
      throw new UploadLimitExceededException(
          "Free tier limit of "
              + limit
              + " photo uploads per month reached. Upgrade to premium for unlimited uploads.");
    }
    counter.recordUpload();
    counterRepository.save(counter);
  }

  /**
   * Reads the caller's quota state for the current server calendar month without locking.
   *
   * <p>Unlimited tiers report {@code null} usage. A missing counter row means the caller has not
   * uploaded anything yet this month, and remaining is clamped at zero so a counter above a changed
   * limit cannot surface a negative value.
   *
   * @param user the account whose quota is read
   * @return the current quota snapshot
   */
  @Transactional(readOnly = true)
  public PhotoUploadQuota snapshot(User user) {
    Integer limit = user.getTier().getPhotoUploadsPerMonth();
    if (limit == null) {
      return new PhotoUploadQuota(null, null);
    }
    int used =
        counterRepository
            .findByUserIdAndPeriod(user.getId(), UploadPeriod.current(clock).stored())
            .map(UserUploadCounter::getCount)
            .orElse(0);
    return new PhotoUploadQuota(used, Math.max(0, limit - used));
  }
}
