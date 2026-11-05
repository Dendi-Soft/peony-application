package com.dendi.peony.service.plant;

import com.dendi.peony.entity.PlantImage;
import com.dendi.peony.entity.PlantImageSource;
import com.dendi.peony.entity.PlantNetSpeciesImage;
import com.dendi.peony.repository.PlantImageRepository;
import com.dendi.peony.repository.PlantNetSpeciesImageRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists a PlantNet species image and retires its staging row in one transaction (issue #45).
 *
 * <p>This writer is deliberately a separate bean: the scan registration transaction must already be
 * committed when the fallback fills the catalogue row, and a single transaction spanning both would
 * either see an uncommitted plant row or hold the scan open during the download. The unique {@code
 * (plant_id, position)} constraint makes a racing fill fail here, after which the caller removes
 * the losing file.
 */
@Component
@RequiredArgsConstructor
public class SpeciesImageWriter {

  private final PlantImageRepository plantImageRepository;

  private final PlantNetSpeciesImageRepository stagingRepository;

  private final Clock clock;

  /**
   * Inserts the position-0 fallback image and consumes the staged candidate.
   *
   * @param plantId catalogue plant receiving the image
   * @param filename stored file name
   * @param staged candidate whose credits are copied onto the image row
   */
  @Transactional
  public void insertPrimary(Long plantId, String filename, PlantNetSpeciesImage staged) {
    PlantImage image = new PlantImage();
    image.setPlantId(plantId);
    image.setPosition(0);
    image.setFilename(filename);
    image.setSource(PlantImageSource.PLANTNET);
    image.setAuthor(staged.getAuthor());
    image.setLicense(staged.getLicense());
    image.setCitation(staged.getCitation());
    image.setCreatedAt(LocalDateTime.now(clock));
    plantImageRepository.saveAndFlush(image);
    stagingRepository.delete(staged);
  }
}
