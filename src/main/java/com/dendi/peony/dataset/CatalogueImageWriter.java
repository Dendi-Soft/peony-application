package com.dendi.peony.dataset;

import com.dendi.peony.entity.Plant;
import com.dendi.peony.entity.PlantImage;
import com.dendi.peony.entity.PlantImageSource;
import com.dendi.peony.repository.PlantImageRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists one curated catalogue image at position 0 and shifts any existing rows down (issue #9
 * follow-up).
 *
 * <p>A pre-existing PlantNet fallback must never be deleted or overwritten: it keeps its file,
 * {@code source}, and attribution and moves to the next position. The move uses a two-step native
 * update through a large offset so the {@code (plant_id, position)} unique constraint is never
 * violated mid-shift.
 */
@Component
@RequiredArgsConstructor
public class CatalogueImageWriter {

  /** Distance used to park existing rows above their final positions during the shift. */
  static final int SHIFT_OFFSET = 1_000_000;

  private final PlantImageRepository plantImageRepository;

  /**
   * Inserts the seed image at position 0 and shifts existing images to later positions.
   *
   * @param plant catalogue plant receiving the image
   * @param filename stored file name
   * @param entry selection entry whose image credits are copied onto the row
   * @param now creation timestamp for the new row
   */
  @Transactional
  public void insertPrimary(
      Plant plant, String filename, CatalogueImageEntry entry, LocalDateTime now) {
    Long plantId = plant.getId();
    plantImageRepository.shiftPositionsUp(plantId, SHIFT_OFFSET);
    plantImageRepository.shiftPositionsDown(plantId, SHIFT_OFFSET);

    CatalogueImage selected = entry.image();
    PlantImage image = new PlantImage();
    image.setPlantId(plantId);
    image.setPosition(0);
    image.setFilename(filename);
    image.setSource(PlantImageSource.SEED);
    image.setAuthor(selected.author());
    image.setLicense(selected.license());
    image.setCitation(selected.title() + " — " + selected.descriptionUrl());
    image.setCreatedAt(now);
    plantImageRepository.saveAndFlush(image);
  }
}
