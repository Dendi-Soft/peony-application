package com.dendi.peony.service.plant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dendi.peony.TestcontainersConfiguration;
import com.dendi.peony.entity.Plant;
import com.dendi.peony.entity.PlantImage;
import com.dendi.peony.entity.PlantImageSource;
import com.dendi.peony.entity.PlantNetSpeciesImage;
import com.dendi.peony.repository.PlantImageRepository;
import com.dendi.peony.repository.PlantNetSpeciesImageRepository;
import com.dendi.peony.repository.PlantRepository;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

/**
 * Integration tests for the transactional species-image writer (issue #45), against a real
 * PostgreSQL database: the position-0 row carries PLANTNET origin and the staged credits, and the
 * candidate is retired in the same transaction.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class SpeciesImageWriterTest {

  @Autowired private SpeciesImageWriter writer;

  @Autowired private PlantRepository plantRepository;

  @Autowired private PlantImageRepository plantImageRepository;

  @Autowired private PlantNetSpeciesImageRepository stagingRepository;

  /** A filled image records its origin and credits, and consumes the staged candidate. */
  @Test
  void testInsertPrimary_StoresCreditedPlantNetImageAndConsumesStaging() {
    Plant plant = seedPlant();
    PlantNetSpeciesImage staged = seedStaged();

    writer.insertPrimary(plant.getId(), "plantnet-test.jpg", staged);

    PlantImage image =
        plantImageRepository.findByPlantIdAndPosition(plant.getId(), 0).orElseThrow();
    assertEquals(0, image.getPosition());
    assertEquals("plantnet-test.jpg", image.getFilename());
    assertEquals(PlantImageSource.PLANTNET, image.getSource());
    assertEquals("Ada Lovelace", image.getAuthor());
    assertEquals("cc-by-sa", image.getLicense());
    assertEquals("Ajuga reptans by Ada Lovelace", image.getCitation());
    assertTrue(
        stagingRepository.findById(staged.getId()).isEmpty(),
        "the candidate must be consumed once its image is stored");
  }

  private Plant seedPlant() {
    Plant plant = new Plant();
    plant.setScientificName("Ajuga t45 writer");
    plant.setDescription("");
    plant.setCreatedAt(LocalDateTime.now());
    return plantRepository.save(plant);
  }

  private PlantNetSpeciesImage seedStaged() {
    PlantNetSpeciesImage staged = new PlantNetSpeciesImage();
    staged.setScientificName("ajuga t45 writer");
    staged.setImageUrl("https://bs.plantnet.org/image/m/abc");
    staged.setOrgan("habit");
    staged.setAuthor("Ada Lovelace");
    staged.setLicense("cc-by-sa");
    staged.setCitation("Ajuga reptans by Ada Lovelace");
    staged.setStagedAt(LocalDateTime.now());
    return stagingRepository.save(staged);
  }
}
