package com.dendi.peony.dataset;

import com.dendi.peony.config.CatalogueImageProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Runs the catalogue image seeder once at startup when explicitly enabled (issue #9 follow-up).
 *
 * <p>Ordered after {@link DatasetSeedRunner} so the 50 plants exist before their images are seeded.
 * A validation failure propagates and exits non-zero; per-species failures are logged and counted,
 * and the run completes.
 */
@Component
@Order(2)
@RequiredArgsConstructor
@Slf4j
public class CatalogueImageSeedRunner implements ApplicationRunner {

  private final CatalogueImageSeeder seeder;

  private final CatalogueImageProperties properties;

  @Override
  public void run(ApplicationArguments args) {
    if (!properties.enabled()) {
      log.debug("Catalogue-image seeder is disabled; nothing to do");
      return;
    }
    CatalogueImageSeeder.SeedResult result = seeder.seed();
    if (result.failed() > 0) {
      log.warn(
          "Seeded {} catalogue images, skipped {}, failed {} (see the warnings above)",
          result.seeded(),
          result.skipped(),
          result.failed());
    } else {
      log.info("Seeded {} catalogue images, skipped {}", result.seeded(), result.skipped());
    }
  }
}
