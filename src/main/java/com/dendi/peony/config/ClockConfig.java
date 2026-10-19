package com.dendi.peony.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Provides the application clock.
 *
 * <p>Injecting the clock as a bean keeps calendar-dependent logic (the upload quota period)
 * deterministic in tests.
 */
@Configuration
public class ClockConfig {

  /**
   * Creates the production clock.
   *
   * @return the system clock in the JVM default zone
   */
  @Bean
  public Clock clock() {
    return Clock.systemDefaultZone();
  }
}
