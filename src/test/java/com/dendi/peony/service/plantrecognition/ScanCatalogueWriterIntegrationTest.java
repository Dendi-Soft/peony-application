package com.dendi.peony.service.plantrecognition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dendi.peony.TestcontainersConfiguration;
import com.dendi.peony.api.model.CatalogDraft;
import com.dendi.peony.entity.Plant;
import com.dendi.peony.entity.PlantSource;
import com.dendi.peony.repository.PlantRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * Integration tests for the recognition catalogue writer (issue #52) against a real PostgreSQL
 * database: a new species becomes a scan-created identity row, and an existing curated row is never
 * overwritten beyond filling blank name and family fields.
 *
 * <p>The writer commits in {@code REQUIRES_NEW}, so a test-managed transaction rollback would not
 * undo its writes. Each test therefore uses its own unique scientific name and removes the rows in
 * {@link #cleanUp()} instead of relying on {@code @Transactional}.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class ScanCatalogueWriterIntegrationTest {

  private static final String NEW_SPECIES = "Caladium t52 writer new";

  private static final String BLANK_SPECIES = "Caladium t52 writer blanks";

  private static final String CURATED_SPECIES = "Caladium t52 writer curated";

  private static final String PARTIAL_SPECIES = "Caladium t52 writer partial";

  private static final List<String> TEST_SPECIES =
      List.of(NEW_SPECIES, BLANK_SPECIES, CURATED_SPECIES, PARTIAL_SPECIES);

  @Autowired private ScanCatalogueWriter scanCatalogueWriter;

  @Autowired private PlantRepository plantRepository;

  /** Removes this class's committed rows without rolling back the writer's transaction. */
  @AfterEach
  void cleanUp() {
    for (String scientificName : TEST_SPECIES) {
      plantRepository.findByScientificName(scientificName).ifPresent(plantRepository::delete);
    }
  }

  /** A species the catalogue has never seen becomes a scan-created identity-only row. */
  @Test
  void testRecord_InsertsScanCatalogueRow_WhenSpeciesNotInCatalogue() {
    scanCatalogueWriter.record(draft(NEW_SPECIES, "Heart of Jesus", "Araceae"));

    Plant stored = plantRepository.findByScientificName(NEW_SPECIES).orElseThrow();
    assertEquals(PlantSource.PLANTNET_SCAN, stored.getSource());
    assertEquals("Heart of Jesus", stored.getName());
    assertEquals("Araceae", stored.getFamily());
    assertEquals("", stored.getDescription());
    assertNull(stored.getWateringFrequency());
    assertNull(stored.getLightNeeds());
    assertNull(stored.getHumidity());
    assertNull(stored.getTempRange());
    assertNull(stored.getToPets());
    assertNull(stored.getToHumans());
    assertNull(stored.getDifficulty());
    assertNull(stored.getNativeRegion());
    assertNull(stored.getMatureSize());
    assertNull(stored.getTags());
    assertNotNull(stored.getCreatedAt());
  }

  /** Blank draft identity fields are stored as unknown rather than as empty strings. */
  @Test
  void testRecord_StoresUnknownNameAndFamily_WhenDraftFieldsAreBlank() {
    scanCatalogueWriter.record(draft(BLANK_SPECIES, "   ", null));

    Plant stored = plantRepository.findByScientificName(BLANK_SPECIES).orElseThrow();
    assertEquals(PlantSource.PLANTNET_SCAN, stored.getSource());
    assertEquals("", stored.getDescription());
    assertNull(stored.getName());
    assertNull(stored.getFamily());
  }

  /** A curated row keeps its name, family, care fields, description, and source. */
  @Test
  void testRecord_PreservesCuratedRow_WhenScientificNameAlreadyExists() {
    plantRepository.save(curatedRow(CURATED_SPECIES, "Curated name", "Curated family"));

    scanCatalogueWriter.record(draft(CURATED_SPECIES, "Scan name", "Scan family"));

    Plant stored = plantRepository.findByScientificName(CURATED_SPECIES).orElseThrow();
    assertEquals("Curated name", stored.getName());
    assertEquals("Curated family", stored.getFamily());
    assertEquals("Curated description", stored.getDescription());
    assertEquals(7, stored.getWateringFrequency());
    assertEquals("bright indirect", stored.getLightNeeds());
    assertEquals("high", stored.getHumidity());
    assertEquals("18-28C", stored.getTempRange());
    assertTrue(stored.getToPets());
    assertFalse(stored.getToHumans());
    assertEquals("easy", stored.getDifficulty());
    assertEquals("Brazil", stored.getNativeRegion());
    assertEquals("2m", stored.getMatureSize());
    assertEquals(PlantSource.SEED, stored.getSource());
  }

  /**
   * An existing row with blank name and family keeps its other values and only gets those filled.
   */
  @Test
  void testRecord_FillsOnlyBlankNameAndFamily_WhenExistingRowHasBlanks() {
    plantRepository.save(curatedRow(PARTIAL_SPECIES, null, null));

    scanCatalogueWriter.record(draft(PARTIAL_SPECIES, "Scan name", "Scan family"));

    Plant stored = plantRepository.findByScientificName(PARTIAL_SPECIES).orElseThrow();
    assertEquals("Scan name", stored.getName());
    assertEquals("Scan family", stored.getFamily());
    assertEquals("Curated description", stored.getDescription());
    assertEquals(7, stored.getWateringFrequency());
    assertEquals("bright indirect", stored.getLightNeeds());
    assertEquals("high", stored.getHumidity());
    assertEquals("18-28C", stored.getTempRange());
    assertTrue(stored.getToPets());
    assertFalse(stored.getToHumans());
    assertEquals("easy", stored.getDifficulty());
    assertEquals("Brazil", stored.getNativeRegion());
    assertEquals("2m", stored.getMatureSize());
    assertEquals(PlantSource.SEED, stored.getSource());
  }

  private static CatalogDraft draft(String scientificName, String commonName, String family) {
    CatalogDraft draft = new CatalogDraft();
    draft.setScientificName(scientificName);
    draft.setCommonName(commonName);
    draft.setFamily(family);
    return draft;
  }

  /** A fully curated row as the dataset seeder would leave it. */
  private static Plant curatedRow(String scientificName, String name, String family) {
    Plant plant = new Plant();
    plant.setName(name);
    plant.setScientificName(scientificName);
    plant.setFamily(family);
    plant.setDescription("Curated description");
    plant.setWateringFrequency(7);
    plant.setLightNeeds("bright indirect");
    plant.setHumidity("high");
    plant.setTempRange("18-28C");
    plant.setToPets(true);
    plant.setToHumans(false);
    plant.setDifficulty("easy");
    plant.setNativeRegion("Brazil");
    plant.setMatureSize("2m");
    plant.setSource(PlantSource.SEED);
    plant.setCreatedAt(LocalDateTime.now());
    return plant;
  }
}
