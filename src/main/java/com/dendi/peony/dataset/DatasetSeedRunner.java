package com.dendi.peony.dataset;

import com.dendi.peony.config.DatasetSeedProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Runs the curated-dataset seeder once at startup when explicitly enabled (issue #9).
 *
 * <p>The seeder is off by default so normal application starts never touch the catalogue. Enable it
 * for a one-shot job with {@code --peony.dataset.seed.enabled=true} and no web server. Failures
 * deliberately propagate: a missing or invalid dataset must exit non-zero rather than be silently
 * ignored.
 */
@Component
@Order(1)
@RequiredArgsConstructor
@Slf4j
public class DatasetSeedRunner implements ApplicationRunner {

  private final PlantDatasetSeeder seeder;

  private final DatasetSeedProperties properties;

  @Override
  public void run(ApplicationArguments args) {
    if (!properties.enabled()) {
      log.debug("Curated-dataset seeder is disabled; nothing to do");
      return;
    }
    PlantDatasetSeeder.SeedResult result = seeder.seed();
    log.info(
        "Seeded {} curated plants and backfilled {} watering due dates",
        result.seeded(),
        result.backfilled());
  }
}
