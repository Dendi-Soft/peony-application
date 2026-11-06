package com.dendi.peony.dataset;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Runs the curated-dataset seeder once at startup when explicitly enabled (issue #9).
 *
 * <p>The runner is disabled by default so normal application starts never touch the catalogue.
 * Enable it for a one-shot job with {@code --peony.dataset.seed.enabled=true} and no web server.
 * Failures deliberately propagate: a missing or invalid dataset must exit non-zero rather than be
 * silently ignored.
 */
@Component
@ConditionalOnProperty(name = "peony.dataset.seed.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class DatasetSeedRunner implements ApplicationRunner {

  private final PlantDatasetSeeder seeder;

  @Override
  public void run(ApplicationArguments args) {
    PlantDatasetSeeder.SeedResult result = seeder.seed();
    log.info(
        "Seeded {} curated plants and backfilled {} watering due dates",
        result.seeded(),
        result.backfilled());
  }
}
