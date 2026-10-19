package com.dendi.peony.service.auth;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;

/**
 * Calendar period for the monthly upload quota.
 *
 * <p>Owns the {@code yyyy-MM} storage key of the upload counter table so the format lives in one
 * place; the counter entity and repository keep the raw string as the persistence seam.
 */
public record UploadPeriod(YearMonth month) {

  private static final DateTimeFormatter STORED_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");

  /**
   * Computes the period containing the current instant, using the clock's zone.
   *
   * @param clock the clock to read the instant and zone from
   * @return the current period
   */
  public static UploadPeriod current(Clock clock) {
    LocalDate date = LocalDate.ofInstant(clock.instant(), clock.getZone());
    return new UploadPeriod(YearMonth.from(date));
  }

  /**
   * Returns the period's storage key.
   *
   * @return the month formatted as {@code yyyy-MM}
   */
  public String stored() {
    return month.format(STORED_FORMAT);
  }
}
