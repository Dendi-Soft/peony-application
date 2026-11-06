package com.dendi.peony.service.plant;

import com.dendi.peony.entity.Plant;
import com.dendi.peony.entity.PlantNetSpeciesImage;
import com.dendi.peony.repository.PlantImageRepository;
import com.dendi.peony.repository.PlantRepository;
import com.dendi.peony.service.plantrecognition.PlantNetStagingService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Fills an image-less catalogue entry with a PlantNet species image (issue #45, ADR-008).
 *
 * <p>Runs after a scan has been kept. The pipeline reads the catalogue plant, skips it when any
 * image already exists, looks for a fresh staged candidate, downloads the medium image, turns it
 * into a 256 px JPEG, writes it under {@code peony.plant-images.dir}, and only then inserts the
 * position-0 row and retires the candidate. Every failure is best effort: the scan has already
 * succeeded, so a broken download, processor, or racing insert must not fail it, and a later scan
 * of the same species retries the fill.
 *
 * <p>The method is intentionally not transactional: the writer opens its own short transaction
 * after the scan transaction has committed, and the network download never runs inside one.
 */
@Service
@Slf4j
public class SpeciesImageService {

  private static final String FILENAME_PREFIX = "plantnet-";

  private static final String FILENAME_SUFFIX = ".jpg";

  private final Path imageDir;
  private final PlantRepository plantRepository;
  private final PlantImageRepository plantImageRepository;
  private final PlantNetStagingService stagingService;
  private final PlantNetImageDownloadClient downloadClient;
  private final SpeciesImageProcessor processor;
  private final SpeciesImageWriter writer;

  /**
   * Creates the species-image fill service.
   *
   * @param imageDir configured directory holding the plant image files
   * @param plantRepository catalogue plant access
   * @param plantImageRepository catalogue image access
   * @param stagingService PlantNet candidate staging
   * @param downloadClient allowlisted image downloader
   * @param processor 256 px JPEG processor
   * @param writer transactional image/staging writer
   */
  public SpeciesImageService(
      @Value("${peony.plant-images.dir}") String imageDir,
      PlantRepository plantRepository,
      PlantImageRepository plantImageRepository,
      PlantNetStagingService stagingService,
      PlantNetImageDownloadClient downloadClient,
      SpeciesImageProcessor processor,
      SpeciesImageWriter writer) {
    this.imageDir = Path.of(imageDir).toAbsolutePath().normalize();
    this.plantRepository = plantRepository;
    this.plantImageRepository = plantImageRepository;
    this.stagingService = stagingService;
    this.downloadClient = downloadClient;
    this.processor = processor;
    this.writer = writer;
  }

  /**
   * Fills the catalogue plant's image when it has none and a fresh candidate is staged.
   *
   * @param plantId catalogue plant id
   * @return true when an image was stored
   */
  public boolean fillIfMissing(Long plantId) {
    try {
      return tryFill(plantId);
    } catch (RuntimeException exception) {
      log.warn("Could not fill the PlantNet species image for plant {}", plantId, exception);
      return false;
    }
  }

  private boolean tryFill(Long plantId) {
    Optional<Plant> maybePlant = plantRepository.findById(plantId);
    if (maybePlant.isEmpty()) {
      return false;
    }
    Plant plant = maybePlant.get();
    if (plantImageRepository.existsByPlantId(plantId)) {
      return false;
    }
    Optional<PlantNetSpeciesImage> maybeStaged =
        stagingService.findFreshForName(plant.getScientificName());
    if (maybeStaged.isEmpty()) {
      return false;
    }
    PlantNetSpeciesImage staged = maybeStaged.get();

    byte[] download;
    try {
      download = downloadClient.download(staged.getImageUrl());
    } catch (IOException | RuntimeException exception) {
      log.warn(
          "Could not download the PlantNet species image for plant {}: {}",
          plantId,
          exception.getMessage());
      return false;
    }

    byte[] processed;
    try {
      processed = processor.process(download);
    } catch (RuntimeException exception) {
      log.warn(
          "Could not process the PlantNet species image for plant {}: {}",
          plantId,
          exception.getMessage());
      return false;
    }

    String filename = FILENAME_PREFIX + UUID.randomUUID() + FILENAME_SUFFIX;
    Path file = ImageDirectory.resolve(imageDir, filename);
    if (file == null) {
      return false;
    }
    try {
      Files.createDirectories(imageDir);
      Files.write(file, processed);
    } catch (IOException exception) {
      log.warn("Could not store the PlantNet species image for plant {}", plantId, exception);
      return false;
    }

    try {
      writer.insertPrimary(plantId, filename, staged);
      return true;
    } catch (RuntimeException exception) {
      deleteQuietly(file);
      log.warn(
          "Could not persist the PlantNet species image for plant {}: {}",
          plantId,
          exception.getMessage());
      return false;
    }
  }

  private static void deleteQuietly(Path file) {
    try {
      Files.deleteIfExists(file);
    } catch (IOException exception) {
      log.warn("Could not remove the unused species-image file {}", file.getFileName());
    }
  }
}
