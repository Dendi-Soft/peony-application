package com.dendi.peony.service.plantrecognition;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Retires expired PlantNet species-image candidates once at startup (issue #45, ADR-008).
 *
 * <p>The project has no scheduler, so startup cleanup plus the opportunistic prune in {@link
 * PlantNetStagingService} keeps the staging table bounded. A cleanup failure is logged and never
 * prevents the application from starting.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PlantNetStagingCleanupRunner implements ApplicationRunner {

  private final PlantNetStagingService stagingService;

  @Override
  public void run(ApplicationArguments args) {
    try {
      long removed = stagingService.pruneExpired();
      if (removed > 0) {
        log.info("Pruned {} expired PlantNet species-image candidates", removed);
      }
    } catch (RuntimeException exception) {
      log.warn("Could not prune expired PlantNet species-image candidates", exception);
    }
  }
}
