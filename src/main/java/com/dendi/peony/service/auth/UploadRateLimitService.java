package com.dendi.peony.service.auth;

import com.dendi.peony.entity.UserTier;
import com.dendi.peony.entity.UserUploadCounter;
import com.dendi.peony.exception.UploadLimitExceededException;
import com.dendi.peony.repository.UserUploadCounterRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
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
    String period = currentPeriod();
    UserUploadCounter counter =
        counterRepository
            .findLockedForUpdate(userId, period)
            .orElseGet(() -> new UserUploadCounter(userId, period));
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
   * Computes the calendar month the current instant falls into, using the injected clock's zone.
   *
   * @return the period formatted as {@code yyyy-MM}
   */
  private String currentPeriod() {
    LocalDate date = LocalDate.ofInstant(clock.instant(), clock.getZone());
    return YearMonth.from(date).toString();
  }
}
