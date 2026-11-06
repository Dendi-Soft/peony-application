package com.dendi.peony.dataset;

import com.dendi.peony.config.CatalogueImageProperties;
import com.dendi.peony.config.PlantImageProperties;
import com.dendi.peony.entity.Plant;
import com.dendi.peony.entity.PlantImageSource;
import com.dendi.peony.exception.CorruptImageException;
import com.dendi.peony.repository.PlantImageRepository;
import com.dendi.peony.repository.PlantRepository;
import com.dendi.peony.service.plant.ImageDirectory;
import com.dendi.peony.service.plant.SpeciesImageProcessor;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

/**
 * Seeds one curated catalogue image per plant from the committed selection (issue #9 follow-up).
 *
 * <p>Validation of the whole selection happens before the first download, so a malformed file
 * leaves the database untouched. A species is skipped before downloading when it already has a
 * {@code SEED} image. Download, decode, file, and database failures for one species are logged and
 * counted; the run continues with the remaining species. A file written for a row that did not
 * persist is removed.
 *
 * <p>The seeder is intentionally not transactional: the network download must not run inside a
 * transaction, and the writer opens one short transaction per species.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CatalogueImageSeeder {

  private static final String FILENAME_PREFIX = "seed-";

  private static final String FILENAME_SUFFIX = ".jpg";

  private final CatalogueImageProperties properties;

  private final PlantImageProperties plantImageProperties;

  private final PlantRepository plantRepository;

  private final PlantImageRepository plantImageRepository;

  private final CatalogueImageDownloader downloader;

  private final SpeciesImageProcessor processor;

  private final CatalogueImageWriter writer;

  private final Clock clock;

  /**
   * Seeds the catalogue images from the committed selection.
   *
   * @return counts of stored, skipped, and failed species
   * @throws DatasetValidationException when the selection file is missing, malformed, or invalid
   */
  public SeedResult seed() {
    CatalogueImageFile file = CatalogueImageFile.load(properties.file());
    CatalogueImageValidator.validate(file);

    Path imageDir = plantImageProperties.directory();
    LocalDateTime now = LocalDateTime.now(clock);
    int seeded = 0;
    int skipped = 0;
    int failed = 0;

    for (CatalogueImageEntry entry : file.species()) {
      Optional<Plant> maybePlant = plantRepository.findByScientificName(entry.scientificName());
      if (maybePlant.isEmpty()) {
        log.warn(
            "Cannot seed the catalogue image for {}: the plant is not in the catalogue",
            entry.scientificName());
        failed++;
        continue;
      }
      Plant plant = maybePlant.get();
      if (plantImageRepository.existsByPlantIdAndSource(plant.getId(), PlantImageSource.SEED)) {
        skipped++;
        continue;
      }
      pauseBetweenDownloads();
      if (storeOne(plant, entry, imageDir, now)) {
        seeded++;
      } else {
        failed++;
      }
    }

    return new SeedResult(seeded, skipped, failed);
  }

  /**
   * Spaces out requests so Wikimedia does not rate-limit a full-selection run. The downloader also
   * retries 429 responses; pacing avoids triggering them in the first place.
   */
  private void pauseBetweenDownloads() {
    int intervalMs = properties.requestIntervalMs();
    if (intervalMs <= 0) {
      return;
    }
    try {
      Thread.sleep(intervalMs);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(
          "Interrupted while pacing catalogue image downloads", exception);
    }
  }

  private boolean storeOne(
      Plant plant, CatalogueImageEntry entry, Path imageDir, LocalDateTime now) {
    Path file = null;
    boolean stored = false;
    try {
      byte[] processed =
          processor.process(downloader.download(entry.image().url()), properties.longEdge());

      String filename = FILENAME_PREFIX + UUID.randomUUID() + FILENAME_SUFFIX;
      file = ImageDirectory.resolve(imageDir, filename);
      if (file == null) {
        log.warn("Refusing unsafe catalogue image file name for {}", entry.scientificName());
        return false;
      }
      Files.createDirectories(imageDir);
      Files.write(file, processed);

      writer.insertPrimary(plant, filename, entry, now);
      stored = true;
      return true;
    } catch (IOException | UncheckedIOException exception) {
      log.warn(
          "Could not download or store the catalogue image for {}: {}",
          entry.scientificName(),
          exception.getMessage());
      return false;
    } catch (CorruptImageException exception) {
      log.warn(
          "Could not decode the catalogue image for {}: {}",
          entry.scientificName(),
          exception.getMessage());
      return false;
    } catch (DataAccessException exception) {
      log.warn(
          "Could not persist the catalogue image for {}: {}",
          entry.scientificName(),
          exception.getMessage());
      return false;
    } finally {
      if (file != null && !stored) {
        deleteQuietly(file);
      }
    }
  }

  private static void deleteQuietly(Path file) {
    try {
      Files.deleteIfExists(file);
    } catch (IOException exception) {
      log.warn("Could not remove the unused catalogue-image file {}", file.getFileName());
    }
  }

  /**
   * Outcome of one seed run.
   *
   * @param seeded number of species whose image was stored
   * @param skipped number of species that already had a seed image
   * @param failed number of species whose plant, download, decode, or write failed
   */
  public record SeedResult(int seeded, int skipped, int failed) {}
}
