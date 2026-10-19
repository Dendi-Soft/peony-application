package com.dendi.peony.service.plant;

import com.dendi.peony.entity.PlantImage;
import com.dendi.peony.exception.ImageNotFoundException;
import com.dendi.peony.exception.PlantNotFoundException;
import com.dendi.peony.repository.PlantImageRepository;
import com.dendi.peony.repository.PlantRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Serves plant image files from disk (issue #10 Phase 3).
 *
 * <p>The plant_images table maps plants to files stored under {@code peony.plant-images.dir}; the
 * image at position 0 doubles as the thumbnail. Files are seeded by the dataset (#9); ingestion is
 * out of scope here.
 */
@Service
public class PlantImageService {

  /** Gallery position of the image that doubles as the thumbnail. */
  static final int THUMB_POSITION = 0;

  private final Path imageDir;
  private final PlantRepository plantRepository;
  private final PlantImageRepository plantImageRepository;

  /**
   * Creates the service.
   *
   * @param imageDir configured directory holding the plant image files
   * @param plantRepository plant data access
   * @param plantImageRepository image data access
   */
  public PlantImageService(
      @Value("${peony.plant-images.dir}") String imageDir,
      PlantRepository plantRepository,
      PlantImageRepository plantImageRepository) {
    this.imageDir = Path.of(imageDir).toAbsolutePath().normalize();
    this.plantRepository = plantRepository;
    this.plantImageRepository = plantImageRepository;
  }

  /** A stored image: the file name (drives the content type) and the bytes. */
  public record StoredImage(String filename, byte[] data) {}

  /**
   * Loads the plant's thumbnail (the image at position 0).
   *
   * @param plantId id of the plant
   * @return the thumbnail bytes with their file name
   * @throws PlantNotFoundException when no plant exists with the id
   * @throws ImageNotFoundException when the plant has no thumbnail or the file is missing
   */
  @Transactional(readOnly = true)
  public StoredImage thumb(Long plantId) {
    return imageAt(plantId, THUMB_POSITION);
  }

  /**
   * Loads a gallery image at the given position.
   *
   * @param plantId id of the plant
   * @param position 0-based gallery position
   * @return the image bytes with their file name
   * @throws PlantNotFoundException when no plant exists with the id
   * @throws ImageNotFoundException when the position holds no image or the file is missing
   */
  @Transactional(readOnly = true)
  public StoredImage galleryImage(Long plantId, int position) {
    return imageAt(plantId, position);
  }

  private StoredImage imageAt(Long plantId, int position) {
    if (!plantRepository.existsById(plantId)) {
      throw new PlantNotFoundException(plantId);
    }
    PlantImage image =
        plantImageRepository
            .findByPlantIdAndPosition(plantId, position)
            .orElseThrow(() -> new ImageNotFoundException(plantId, position));
    Path file = resolveImageFile(plantId, position, image.getFilename());
    try {
      return new StoredImage(image.getFilename(), Files.readAllBytes(file));
    } catch (IOException e) {
      throw new ImageNotFoundException(plantId, position);
    }
  }

  /** Resolves the stored file inside the image directory; anything else behaves as missing. */
  private Path resolveImageFile(Long plantId, int position, String filename) {
    Path resolved = imageDir.resolve(filename).normalize();
    if (!resolved.startsWith(imageDir) || !Files.isRegularFile(resolved)) {
      throw new ImageNotFoundException(plantId, position);
    }
    return resolved;
  }
}
