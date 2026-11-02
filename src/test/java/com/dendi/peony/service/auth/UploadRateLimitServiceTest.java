package com.dendi.peony.service.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.dendi.peony.AdjustableClock;
import com.dendi.peony.TestcontainersConfiguration;
import com.dendi.peony.entity.User;
import com.dendi.peony.entity.UserTier;
import com.dendi.peony.entity.UserUploadCounter;
import com.dendi.peony.repository.UserRepository;
import com.dendi.peony.repository.UserUploadCounterRepository;
import java.time.Instant;
import java.time.Period;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

/**
 * Tests for the read-side quota snapshot in {@link UploadRateLimitService}, against a real
 * PostgreSQL database.
 *
 * <p>The snapshot is what profile responses surface: it reports the caller's usage in the current
 * server calendar month without taking the write lock the enforcement path uses. The clock is
 * injectable so month rollover can be exercised deterministically.
 */
@Import({TestcontainersConfiguration.class, UploadRateLimitServiceTest.TestClockConfig.class})
@SpringBootTest
class UploadRateLimitServiceTest {

  @Autowired private UploadRateLimitService uploadRateLimitService;

  @Autowired private UserRepository userRepository;

  @Autowired private UserUploadCounterRepository counterRepository;

  @Autowired private AdjustableClock clock;

  /** Test clock: starts mid-October so month rollover can be advanced deterministically. */
  @TestConfiguration(proxyBeanMethods = false)
  static class TestClockConfig {

    /**
     * Creates the adjustable test clock, taking precedence over the application clock by type.
     *
     * @return the clock starting at 2026-10-05
     */
    @Bean
    @Primary
    AdjustableClock testClock() {
      return new AdjustableClock(Instant.parse("2026-10-05T12:00:00Z"));
    }
  }

  /** With no counter row the caller has used nothing and keeps the tier's full limit. */
  @Test
  void testSnapshot_ReturnsZeroUsedAndFullLimit_WhenNoCounterRowExists() {
    User user = freeUser("quota-no-row");

    PhotoUploadQuota quota = uploadRateLimitService.snapshot(UserTier.FREE, user.getId());

    assertEquals(0, quota.used());
    assertEquals(5, quota.remaining());
  }

  /** Each recorded upload increases the usage and decreases the remaining quota. */
  @Test
  void testSnapshot_ReturnsUsedAndRemaining_WhenUploadsRecorded() {
    User user = freeUser("quota-used");

    for (int i = 0; i < 3; i++) {
      uploadRateLimitService.recordUpload(UserTier.FREE, user.getId());
    }

    PhotoUploadQuota quota = uploadRateLimitService.snapshot(UserTier.FREE, user.getId());

    assertEquals(3, quota.used());
    assertEquals(2, quota.remaining());
  }

  /** Once the counter reaches the limit no uploads remain. */
  @Test
  void testSnapshot_ReturnsZeroRemaining_WhenCounterAtLimit() {
    User user = freeUser("quota-at-limit");
    for (int i = 0; i < 5; i++) {
      uploadRateLimitService.recordUpload(UserTier.FREE, user.getId());
    }

    PhotoUploadQuota quota = uploadRateLimitService.snapshot(UserTier.FREE, user.getId());

    assertEquals(5, quota.used());
    assertEquals(0, quota.remaining());
  }

  /**
   * A counter above the limit (for example after a limit change) never yields negative remaining.
   */
  @Test
  void testSnapshot_ClampsRemainingAtZero_WhenCounterExceedsLimit() {
    User user = freeUser("quota-over-limit");
    UserUploadCounter counter =
        new UserUploadCounter(user.getId(), UploadPeriod.current(clock).stored());
    counter.setCount(7);
    counterRepository.save(counter);

    PhotoUploadQuota quota = uploadRateLimitService.snapshot(UserTier.FREE, user.getId());

    assertEquals(7, quota.used());
    assertEquals(0, quota.remaining());
  }

  /** The unlimited premium tier reports no usage or remaining count. */
  @Test
  void testSnapshot_ReturnsNullUsage_WhenTierIsPremium() {
    User user = freeUser("quota-premium");

    PhotoUploadQuota quota = uploadRateLimitService.snapshot(UserTier.PREMIUM, user.getId());

    assertNull(quota.used());
    assertNull(quota.remaining());
  }

  /** Moving into a new calendar month starts the quota over without touching the old counter. */
  @Test
  void testSnapshot_ResetsUsage_WhenCalendarMonthChanges() {
    User user = freeUser("quota-rollover");
    for (int i = 0; i < 3; i++) {
      uploadRateLimitService.recordUpload(UserTier.FREE, user.getId());
    }
    assertEquals(3, uploadRateLimitService.snapshot(UserTier.FREE, user.getId()).used());

    clock.advance(Period.ofMonths(1));

    PhotoUploadQuota quota = uploadRateLimitService.snapshot(UserTier.FREE, user.getId());
    assertEquals(0, quota.used());
    assertEquals(5, quota.remaining());
  }

  /** Saves a fresh free-tier account with a unique username. */
  private User freeUser(String username) {
    return userRepository.save(
        User.create(username, username + "@example.com", "hash", "Test", "User", UserTier.FREE));
  }
}
