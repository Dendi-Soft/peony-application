package com.dendi.peony;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAmount;

/**
 * A clock with a mutable instant, for tests that need to move the calendar forward (e.g., across a
 * month boundary) between requests.
 */
public class AdjustableClock extends Clock {

  private Instant instant;

  /**
   * Creates the clock at the given instant.
   *
   * @param initialInstant the initial instant
   */
  public AdjustableClock(Instant initialInstant) {
    this.instant = initialInstant;
  }

  /**
   * Moves the clock by the given amount (e.g., one month), keeping the time of day.
   *
   * <p>Calendar amounts (months, days) are applied in the clock zone because {@link Instant} cannot
   * represent them; {@link Duration} amounts are applied directly to the instant.
   *
   * @param amount how far to move the clock
   */
  public void advance(TemporalAmount amount) {
    if (amount instanceof Duration duration) {
      this.instant = this.instant.plus(duration);
      return;
    }
    LocalDate date = LocalDate.ofInstant(instant, getZone()).plus(amount);
    LocalTime time = LocalTime.ofInstant(instant, getZone());
    this.instant = date.atTime(time).atZone(getZone()).toInstant();
  }

  /**
   * Returns the current instant.
   *
   * @return the current instant
   */
  @Override
  public Instant instant() {
    return instant;
  }

  /**
   * Returns the clock zone (UTC).
   *
   * @return UTC
   */
  @Override
  public ZoneId getZone() {
    return ZoneOffset.UTC;
  }

  /**
   * The zone is fixed to UTC; returns this clock.
   *
   * @param zone requested zone (ignored)
   * @return this clock
   */
  @Override
  public Clock withZone(ZoneId zone) {
    return this;
  }
}
