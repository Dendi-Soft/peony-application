package com.dendi.peony.dataset;

import com.dendi.peony.repository.PlantRepository;
import com.dendi.peony.repository.UserPlantRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Idempotently writes the curated dataset into the catalogue (issue #9).
 *
 * <p>The seed file is loaded and fully validated before the first write, so an invalid dataset
 * leaves the database untouched. Every entry is upserted by scientific name: new rows record {@code
 * source = SEED}, while existing scan- or manually-created rows are enriched with curated care data
 * without their creation origin or timestamp changing. After the upserts, due dates are backfilled
 * for individuals of seeded species that have a last-watered date and no due date.
 */
@Service
@RequiredArgsConstructor
public class PlantDatasetSeeder {

  private final PlantRepository plantRepository;

  private final UserPlantRepository userPlantRepository;

  private final Clock clock;

  /**
   * Seeds the catalogue from the classpath dataset.
   *
   * @return counts of upserted plants and backfilled watering due dates
   * @throws DatasetValidationException when the dataset is missing, malformed, or invalid
   */
  @Transactional
  public SeedResult seed() {
    DatasetSeedFile file = DatasetSeedFile.load();
    DatasetValidator.validate(file);

    LocalDateTime now = LocalDateTime.now(clock);
    List<String> seededNames = new ArrayList<>(file.plants().size());
    int seeded = 0;
    for (DatasetSeedEntry entry : file.plants()) {
      seeded += plantRepository.upsertSeedEntry(entry, now);
      seededNames.add(entry.scientificName());
    }

    int backfilled = userPlantRepository.backfillNextWaterDueAt(seededNames);
    return new SeedResult(seeded, backfilled);
  }

  /**
   * Outcome of one seed run.
   *
   * @param seeded number of catalogue rows inserted or enriched
   * @param backfilled number of individual watering due dates filled
   */
  public record SeedResult(int seeded, int backfilled) {}
}
