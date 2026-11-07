package com.dendi.peony.dataset;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.dendi.peony.TestcontainersConfiguration;
import com.dendi.peony.entity.Plant;
import com.dendi.peony.entity.PlantSource;
import com.dendi.peony.entity.User;
import com.dendi.peony.entity.UserPlant;
import com.dendi.peony.entity.UserTier;
import com.dendi.peony.repository.PlantRepository;
import com.dendi.peony.repository.UserPlantRepository;
import com.dendi.peony.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.annotation.Transactional;

/**
 * Integration tests for the idempotent curated-dataset seeder (issue #9, T9.3), against a real
 * PostgreSQL database.
 *
 * <p>The real classpath seed file is used, so the tests also exercise JSON loading and validation.
 * Each test runs in a rolled-back transaction. The catalog must converge on 50 fully populated
 * {@code source = SEED} rows on an empty database; re-running must preserve {@code source} and
 * {@code created_at}; scan- and manually-created rows must be enriched without losing their
 * creation origin; and watering due dates are backfilled only for seeded species with a
 * last-watered date and no due date.
 */
@Import({TestcontainersConfiguration.class, PlantDatasetSeederTest.MutableClockConfig.class})
@SpringBootTest
@Transactional
class PlantDatasetSeederTest {

  private static final Instant FIRST_RUN = Instant.parse("2026-01-02T08:30:15Z");
  private static final Instant SECOND_RUN = Instant.parse("2026-02-03T09:45:00Z");
  private static final LocalDateTime OLD_CREATED_AT = LocalDateTime.of(2020, 5, 6, 7, 8, 9);
  private static final LocalDateTime LAST_WATERED = LocalDateTime.of(2026, 1, 10, 18, 30);
  private static final LocalDateTime ALREADY_DUE = LocalDateTime.of(2026, 1, 20, 9, 0);

  @Autowired private PlantDatasetSeeder seeder;

  @Autowired private PlantRepository plantRepository;

  @Autowired private UserPlantRepository userPlantRepository;

  @Autowired private UserRepository userRepository;

  @Autowired private MutableClock clock;

  /** Seeds an empty catalogue: all 50 entries are inserted with every curated field populated. */
  @Test
  void testSeed_PersistsEveryCuratedPlant_WhenCatalogueIsEmpty() {
    clock.set(FIRST_RUN);
    DatasetSeedFile file = DatasetSeedFile.load();

    PlantDatasetSeeder.SeedResult result = seeder.seed();

    assertEquals(50, result.seeded(), "every curated entry must be upserted");
    List<Plant> seeded = seededPlants(file);
    assertEquals(50, seeded.size(), "the dataset must contribute exactly 50 catalogue rows");
    for (DatasetSeedEntry entry : file.plants()) {
      Plant plant =
          plantRepository
              .findByScientificName(entry.scientificName())
              .orElseThrow(
                  () -> new AssertionError("missing seeded plant " + entry.scientificName()));
      assertEquals(entry.commonName(), plant.getName());
      assertEquals(entry.family(), plant.getFamily());
      assertEquals(entry.description(), plant.getDescription());
      assertArrayEquals(entry.tags().toArray(new String[0]), plant.getTags());
      assertEquals(entry.lightNeeds(), plant.getLightNeeds());
      assertEquals(entry.waterFrequencyDays(), plant.getWateringFrequency());
      assertEquals(entry.humidity(), plant.getHumidity());
      assertEquals(entry.tempRange(), plant.getTempRange());
      assertEquals(entry.toxicToPets(), plant.getToPets());
      assertEquals(entry.toxicToHumans(), plant.getToHumans());
      assertEquals(entry.difficulty(), plant.getDifficulty());
      assertEquals(entry.nativeRegion(), plant.getNativeRegion());
      assertEquals(entry.matureSize(), plant.getMatureSize());
      assertEquals(PlantSource.SEED, plant.getSource());
      assertEquals(firstRunLocal(), plant.getCreatedAt());
    }
  }

  /** A second run changes neither the row count nor the creation origin or timestamp. */
  @Test
  void testSeed_KeepsCountSourceAndCreatedAt_WhenRunTwice() {
    clock.set(FIRST_RUN);
    seeder.seed();
    clock.set(SECOND_RUN);

    PlantDatasetSeeder.SeedResult second = seeder.seed();

    DatasetSeedFile file = DatasetSeedFile.load();
    assertEquals(50, second.seeded(), "the second run must upsert the same 50 entries");
    assertEquals(50, seededPlants(file).size(), "re-running must not duplicate rows");
    for (DatasetSeedEntry entry : file.plants()) {
      Plant plant = plantRepository.findByScientificName(entry.scientificName()).orElseThrow();
      assertEquals(PlantSource.SEED, plant.getSource());
      assertEquals(firstRunLocal(), plant.getCreatedAt(), "created_at must not change on update");
    }
  }

  /** A scan-created row is enriched with curated care data but keeps its creation origin. */
  @Test
  void testSeed_EnrichesScanCreatedRow_WithoutChangingSourceOrCreatedAt() {
    clock.set(FIRST_RUN);
    DatasetSeedFile file = DatasetSeedFile.load();
    DatasetSeedEntry entry = file.plants().get(0);
    Plant scanRow =
        existingPlant(
            entry.scientificName(), "", null, "", null, PlantSource.PLANTNET_SCAN, OLD_CREATED_AT);

    seeder.seed();

    Plant enriched = plantRepository.findById(scanRow.getId()).orElseThrow();
    assertEquals(entry.commonName(), enriched.getName(), "blank common name must be filled");
    assertEquals(entry.family(), enriched.getFamily(), "missing family must be filled");
    assertEquals(entry.description(), enriched.getDescription());
    assertEquals(entry.lightNeeds(), enriched.getLightNeeds());
    assertEquals(entry.waterFrequencyDays(), enriched.getWateringFrequency());
    assertEquals(entry.humidity(), enriched.getHumidity());
    assertEquals(entry.tempRange(), enriched.getTempRange());
    assertEquals(entry.toxicToPets(), enriched.getToPets());
    assertEquals(entry.toxicToHumans(), enriched.getToHumans());
    assertEquals(entry.difficulty(), enriched.getDifficulty());
    assertEquals(entry.nativeRegion(), enriched.getNativeRegion());
    assertEquals(entry.matureSize(), enriched.getMatureSize());
    assertEquals(PlantSource.PLANTNET_SCAN, enriched.getSource(), "origin must be preserved");
    assertEquals(OLD_CREATED_AT, enriched.getCreatedAt(), "created_at must be preserved");
  }

  /** A manually-registered row is enriched with curated care data but keeps its creation origin. */
  @Test
  void testSeed_EnrichesManualRow_WithoutChangingSourceOrCreatedAt() {
    clock.set(FIRST_RUN);
    DatasetSeedFile file = DatasetSeedFile.load();
    DatasetSeedEntry entry = file.plants().get(1);
    Plant manualRow =
        existingPlant(
            entry.scientificName(),
            "Old common name",
            "Old family",
            "Old description",
            90,
            PlantSource.MANUAL,
            OLD_CREATED_AT);

    seeder.seed();

    Plant enriched = plantRepository.findById(manualRow.getId()).orElseThrow();
    assertEquals(entry.commonName(), enriched.getName());
    assertEquals(entry.family(), enriched.getFamily());
    assertEquals(entry.description(), enriched.getDescription());
    assertEquals(entry.waterFrequencyDays(), enriched.getWateringFrequency());
    assertEquals(PlantSource.MANUAL, enriched.getSource(), "origin must be preserved");
    assertEquals(OLD_CREATED_AT, enriched.getCreatedAt(), "created_at must be preserved");
  }

  /**
   * Due dates are backfilled from the last watering only for seeded species, only when the due date
   * is absent, and only when a last-watered date exists.
   */
  @Test
  void testSeed_BackfillsNextWaterDueAt_OnlyForSeededSpeciesWithLastWateredAndNullDue() {
    clock.set(FIRST_RUN);
    DatasetSeedFile file = DatasetSeedFile.load();
    DatasetSeedEntry entry = file.plants().get(0);
    Plant seededPlant =
        existingPlant(
            entry.scientificName(), "", null, "", null, PlantSource.PLANTNET_SCAN, OLD_CREATED_AT);
    Plant otherPlant =
        existingPlant(
            "Unseeded testus",
            "Unseeded",
            "Testaceae",
            "not in dataset",
            3,
            PlantSource.MANUAL,
            OLD_CREATED_AT);
    User owner = seedUser("t93-backfill-owner");
    UserPlant backfillable = individual(owner, seededPlant, "backfillable", LAST_WATERED, null);
    UserPlant alreadyDue = individual(owner, seededPlant, "already-due", LAST_WATERED, ALREADY_DUE);
    UserPlant neverWatered = individual(owner, seededPlant, "never-watered", null, null);
    UserPlant unseeded = individual(owner, otherPlant, "unseeded", LAST_WATERED, null);

    PlantDatasetSeeder.SeedResult result = seeder.seed();

    assertEquals(
        LAST_WATERED.plusDays(entry.waterFrequencyDays()),
        userPlantRepository.findById(backfillable.getId()).orElseThrow().getNextWaterDueAt(),
        "a seeded-species individual with a last watering must get a due date");
    assertEquals(
        ALREADY_DUE,
        userPlantRepository.findById(alreadyDue.getId()).orElseThrow().getNextWaterDueAt(),
        "an existing due date must never be overwritten");
    assertNull(
        userPlantRepository.findById(neverWatered.getId()).orElseThrow().getNextWaterDueAt(),
        "an individual without a last watering must stay unscheduled");
    assertNull(
        userPlantRepository.findById(unseeded.getId()).orElseThrow().getNextWaterDueAt(),
        "species outside the seeded dataset must not be touched");
    assertEquals(1, result.backfilled(), "only the one eligible individual must be backfilled");
  }

  /** Re-running the seed leaves a backfilled due date and an existing due date unchanged. */
  @Test
  void testSeed_KeepsDueDates_WhenRunAgain() {
    clock.set(FIRST_RUN);
    DatasetSeedFile file = DatasetSeedFile.load();
    DatasetSeedEntry entry = file.plants().get(0);
    Plant seededPlant =
        existingPlant(
            entry.scientificName(), "", null, "", null, PlantSource.PLANTNET_SCAN, OLD_CREATED_AT);
    User owner = seedUser("t93-rerun-owner");
    UserPlant backfillable = individual(owner, seededPlant, "backfillable", LAST_WATERED, null);
    UserPlant alreadyDue = individual(owner, seededPlant, "already-due", LAST_WATERED, ALREADY_DUE);

    seeder.seed();
    LocalDateTime firstDue =
        userPlantRepository.findById(backfillable.getId()).orElseThrow().getNextWaterDueAt();
    assertEquals(LAST_WATERED.plusDays(entry.waterFrequencyDays()), firstDue);
    clock.set(SECOND_RUN);

    PlantDatasetSeeder.SeedResult second = seeder.seed();

    assertEquals(
        firstDue,
        userPlantRepository.findById(backfillable.getId()).orElseThrow().getNextWaterDueAt(),
        "a second run must not move an already-filled due date");
    assertEquals(
        ALREADY_DUE,
        userPlantRepository.findById(alreadyDue.getId()).orElseThrow().getNextWaterDueAt(),
        "a second run must not overwrite an existing due date");
    assertEquals(0, second.backfilled(), "nothing is eligible after the first run");
  }

  private List<Plant> seededPlants(DatasetSeedFile file) {
    Set<String> seededNames = new HashSet<>();
    for (DatasetSeedEntry entry : file.plants()) {
      seededNames.add(entry.scientificName());
    }
    return plantRepository.findAll().stream()
        .filter(plant -> seededNames.contains(plant.getScientificName()))
        .toList();
  }

  private Plant existingPlant(
      String scientificName,
      String name,
      String family,
      String description,
      Integer wateringFrequency,
      PlantSource source,
      LocalDateTime createdAt) {
    Plant plant = new Plant();
    plant.setScientificName(scientificName);
    plant.setName(name);
    plant.setFamily(family);
    plant.setDescription(description);
    plant.setWateringFrequency(wateringFrequency);
    plant.setSource(source);
    plant.setCreatedAt(createdAt);
    return plantRepository.saveAndFlush(plant);
  }

  private User seedUser(String username) {
    return userRepository.saveAndFlush(
        User.create(username, username + "@example.com", "hash", "Test", "User", UserTier.FREE));
  }

  private UserPlant individual(
      User owner,
      Plant plant,
      String nickname,
      LocalDateTime lastWateredAt,
      LocalDateTime nextWaterDueAt) {
    UserPlant individual = new UserPlant();
    individual.setUserId(owner.getId());
    individual.setPlantId(plant.getId());
    individual.setNickname(nickname);
    individual.setLastWateredAt(lastWateredAt);
    individual.setNextWaterDueAt(nextWaterDueAt);
    individual.setCreatedAt(OLD_CREATED_AT);
    individual.setUpdatedAt(OLD_CREATED_AT);
    return userPlantRepository.saveAndFlush(individual);
  }

  private static LocalDateTime firstRunLocal() {
    return LocalDateTime.ofInstant(FIRST_RUN, ZoneOffset.UTC);
  }

  /** Clock whose instant can be advanced between seed runs; the seeder reads it via injection. */
  static final class MutableClock extends Clock {

    private final AtomicReference<Instant> instant;
    private final ZoneId zone = ZoneOffset.UTC;

    MutableClock(Instant initial) {
      this.instant = new AtomicReference<>(initial);
    }

    void set(Instant value) {
      instant.set(value);
    }

    @Override
    public ZoneId getZone() {
      return zone;
    }

    @Override
    public Clock withZone(ZoneId targetZone) {
      return Clock.fixed(instant.get(), targetZone);
    }

    @Override
    public Instant instant() {
      return instant.get();
    }
  }

  /** Makes the mutable clock the primary {@link Clock} for the seeder. */
  @TestConfiguration(proxyBeanMethods = false)
  static class MutableClockConfig {

    @Bean
    @Primary
    MutableClock mutableClock() {
      return new MutableClock(FIRST_RUN);
    }
  }
}
