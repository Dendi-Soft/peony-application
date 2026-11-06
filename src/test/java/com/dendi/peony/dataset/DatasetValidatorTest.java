package com.dendi.peony.dataset;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Rule-by-rule tests for {@link DatasetValidator} (issue #9, T9.3).
 *
 * <p>Each test starts from a valid 50-entry fixture and breaks exactly one rule, so the assertion
 * names the problem that must be reported. One test also proves the validator collects every
 * violation in a single pass instead of failing on the first one.
 */
class DatasetValidatorTest {

  private static final int PLANT_COUNT = DatasetValidator.EXPECTED_PLANT_COUNT;

  @Test
  void testValidate_AcceptsDataset_WhenEveryRuleHolds() {
    assertDoesNotThrow(() -> DatasetValidator.validate(validFile()));
  }

  @Test
  void testValidate_RejectsBlankField_WhenLightNeedsIsMissing() {
    List<DatasetSeedEntry> plants = validPlants();
    plants.set(3, EntryBuilder.from(plants.get(3)).lightNeeds(" ").build());

    DatasetValidationException exception = assertInvalid(fileWith(plants));

    assertTrue(
        exception.problems().contains("plant[3].lightNeeds must not be blank"),
        () -> "expected a blank lightNeeds problem, got " + exception.problems());
  }

  @Test
  void testValidate_RejectsDuplicateName_WhenTrimmedCaseInsensitiveNamesMatch() {
    List<DatasetSeedEntry> plants = validPlants();
    String duplicate = "  " + plants.get(0).scientificName().toUpperCase() + " ";
    plants.set(7, EntryBuilder.from(plants.get(7)).scientificName(duplicate).build());

    DatasetValidationException exception = assertInvalid(fileWith(plants));

    assertTrue(
        exception.problems().stream()
            .anyMatch(problem -> problem.contains("plant[7].scientificName is duplicated")),
        () -> "expected a duplicate scientific name problem, got " + exception.problems());
  }

  @Test
  void testValidate_RejectsWaterFrequency_WhenOutsideOneToNinety() {
    List<DatasetSeedEntry> plants = validPlants();
    plants.set(10, EntryBuilder.from(plants.get(10)).waterFrequencyDays(91).build());

    DatasetValidationException exception = assertInvalid(fileWith(plants));

    assertTrue(
        exception
            .problems()
            .contains("plant[10].waterFrequencyDays must be between 1 and 90, was 91"),
        () -> "expected an out-of-range water frequency problem, got " + exception.problems());
  }

  @Test
  void testValidate_RejectsNullWaterFrequency_WhenMissing() {
    List<DatasetSeedEntry> plants = validPlants();
    plants.set(10, EntryBuilder.from(plants.get(10)).waterFrequencyDays(null).build());

    DatasetValidationException exception = assertInvalid(fileWith(plants));

    assertTrue(
        exception.problems().contains("plant[10].waterFrequencyDays must not be null"),
        () -> "expected a null water frequency problem, got " + exception.problems());
  }

  @Test
  void testValidate_RejectsBlankToxicitySource_WhenPetsReferenceIsBlank() {
    List<DatasetSeedEntry> plants = validPlants();
    plants.set(2, EntryBuilder.from(plants.get(2)).toxicToPetsSource("").build());

    DatasetValidationException exception = assertInvalid(fileWith(plants));

    assertTrue(
        exception.problems().contains("plant[2].toxicToPetsSource must not be blank"),
        () -> "expected a blank pet source problem, got " + exception.problems());
  }

  @Test
  void testValidate_RejectsNullToxicityBoolean_WhenHumanFlagIsMissing() {
    List<DatasetSeedEntry> plants = validPlants();
    plants.set(4, EntryBuilder.from(plants.get(4)).toxicToHumans(null).build());

    DatasetValidationException exception = assertInvalid(fileWith(plants));

    assertTrue(
        exception.problems().contains("plant[4].toxicToHumans must not be null"),
        () -> "expected a null human toxicity problem, got " + exception.problems());
  }

  @Test
  void testValidate_RejectsBareGenus_WhenScientificNameHasOneToken() {
    List<DatasetSeedEntry> plants = validPlants();
    plants.set(6, EntryBuilder.from(plants.get(6)).scientificName("Philodendron").build());

    DatasetValidationException exception = assertInvalid(fileWith(plants));

    assertTrue(
        exception.problems().stream()
            .anyMatch(problem -> problem.contains("plant[6].scientificName must be a binomial")),
        () -> "expected a binomial problem, got " + exception.problems());
  }

  @Test
  void testValidate_RejectsInfraspecificName_WhenScientificNameHasThreeTokens() {
    List<DatasetSeedEntry> plants = validPlants();
    plants.set(6, EntryBuilder.from(plants.get(6)).scientificName("Genus species subsp").build());

    DatasetValidationException exception = assertInvalid(fileWith(plants));

    assertTrue(
        exception.problems().stream()
            .anyMatch(
                problem ->
                    problem.contains("plant[6].scientificName must be a binomial of exactly two")),
        () -> "expected an exactly-two-token binomial problem, got " + exception.problems());
  }

  @Test
  void testValidate_RejectsUnknownDifficulty_WhenNotInAllowedSet() {
    List<DatasetSeedEntry> plants = validPlants();
    plants.set(9, EntryBuilder.from(plants.get(9)).difficulty("Expert").build());

    DatasetValidationException exception = assertInvalid(fileWith(plants));

    assertTrue(
        exception.problems().stream()
            .anyMatch(problem -> problem.contains("plant[9].difficulty must be one of")),
        () -> "expected a difficulty problem, got " + exception.problems());
  }

  @Test
  void testValidate_RejectsEmptyTags_WhenTagListIsEmpty() {
    List<DatasetSeedEntry> plants = validPlants();
    plants.set(11, EntryBuilder.from(plants.get(11)).tags(List.of()).build());

    DatasetValidationException exception = assertInvalid(fileWith(plants));

    assertTrue(
        exception.problems().contains("plant[11].tags must not be empty"),
        () -> "expected an empty tags problem, got " + exception.problems());
  }

  @Test
  void testValidate_RejectsBlankTag_WhenOneTagIsWhitespace() {
    List<DatasetSeedEntry> plants = validPlants();
    plants.set(12, EntryBuilder.from(plants.get(12)).tags(List.of("low-light", " ")).build());

    DatasetValidationException exception = assertInvalid(fileWith(plants));

    assertTrue(
        exception.problems().contains("plant[12].tags[1] must not be blank"),
        () -> "expected a blank tag problem, got " + exception.problems());
  }

  @Test
  void testValidate_RejectsWrongPlantCount_WhenFewerThanFifty() {
    List<DatasetSeedEntry> plants = validPlants().subList(0, PLANT_COUNT - 1);

    DatasetValidationException exception = assertInvalid(fileWith(plants));

    assertTrue(
        exception.problems().contains("plants must contain exactly 50 entries, was 49"),
        () -> "expected a plant-count problem, got " + exception.problems());
  }

  @Test
  void testValidate_ReportsEveryProblem_WhenSeveralEntriesAreBroken() {
    List<DatasetSeedEntry> plants = validPlants();
    plants.set(0, EntryBuilder.from(plants.get(0)).lightNeeds("").build());
    plants.set(1, EntryBuilder.from(plants.get(1)).waterFrequencyDays(0).build());
    plants.set(2, EntryBuilder.from(plants.get(2)).difficulty("Impossible").build());

    DatasetValidationException exception = assertInvalid(fileWith(plants));

    assertEquals(3, exception.problems().size(), "all three problems must be reported");
    assertTrue(exception.getMessage().contains("plant[0].lightNeeds"));
    assertTrue(exception.getMessage().contains("plant[1].waterFrequencyDays"));
    assertTrue(exception.getMessage().contains("plant[2].difficulty"));
  }

  private static DatasetValidationException assertInvalid(DatasetSeedFile file) {
    return assertThrows(DatasetValidationException.class, () -> DatasetValidator.validate(file));
  }

  private static DatasetSeedFile validFile() {
    return fileWith(validPlants());
  }

  private static DatasetSeedFile fileWith(List<DatasetSeedEntry> plants) {
    return new DatasetSeedFile(DatasetValidator.SUPPORTED_VERSION, plants);
  }

  private static List<DatasetSeedEntry> validPlants() {
    List<DatasetSeedEntry> plants = new ArrayList<>(PLANT_COUNT);
    for (int i = 0; i < PLANT_COUNT; i++) {
      plants.add(validEntry(i));
    }
    return plants;
  }

  private static DatasetSeedEntry validEntry(int index) {
    return new DatasetSeedEntry(
        "Common " + index,
        "Genus species" + index,
        "Family",
        "A curated description for plant " + index + ".",
        List.of("tag-one", "tag-two"),
        "Bright indirect light",
        7,
        "Average",
        "18-24C",
        Boolean.FALSE,
        "https://example.com/pets/" + index,
        Boolean.FALSE,
        "https://example.com/humans/" + index,
        "Easy",
        "Test region",
        "30-60 cm");
  }

  /** Mutable copy helper so each test can break exactly one field of a valid entry. */
  private static final class EntryBuilder {

    private String commonName;
    private String scientificName;
    private String family;
    private String description;
    private List<String> tags;
    private String lightNeeds;
    private Integer waterFrequencyDays;
    private String humidity;
    private String tempRange;
    private Boolean toxicToPets;
    private String toxicToPetsSource;
    private Boolean toxicToHumans;
    private String toxicToHumansSource;
    private String difficulty;
    private String nativeRegion;
    private String matureSize;

    private EntryBuilder(DatasetSeedEntry entry) {
      this.commonName = entry.commonName();
      this.scientificName = entry.scientificName();
      this.family = entry.family();
      this.description = entry.description();
      this.tags = entry.tags();
      this.lightNeeds = entry.lightNeeds();
      this.waterFrequencyDays = entry.waterFrequencyDays();
      this.humidity = entry.humidity();
      this.tempRange = entry.tempRange();
      this.toxicToPets = entry.toxicToPets();
      this.toxicToPetsSource = entry.toxicToPetsSource();
      this.toxicToHumans = entry.toxicToHumans();
      this.toxicToHumansSource = entry.toxicToHumansSource();
      this.difficulty = entry.difficulty();
      this.nativeRegion = entry.nativeRegion();
      this.matureSize = entry.matureSize();
    }

    private static EntryBuilder from(DatasetSeedEntry entry) {
      return new EntryBuilder(entry);
    }

    private EntryBuilder scientificName(String value) {
      this.scientificName = value;
      return this;
    }

    private EntryBuilder lightNeeds(String value) {
      this.lightNeeds = value;
      return this;
    }

    private EntryBuilder waterFrequencyDays(Integer value) {
      this.waterFrequencyDays = value;
      return this;
    }

    private EntryBuilder toxicToPetsSource(String value) {
      this.toxicToPetsSource = value;
      return this;
    }

    private EntryBuilder toxicToHumans(Boolean value) {
      this.toxicToHumans = value;
      return this;
    }

    private EntryBuilder difficulty(String value) {
      this.difficulty = value;
      return this;
    }

    private EntryBuilder tags(List<String> value) {
      this.tags = value;
      return this;
    }

    private DatasetSeedEntry build() {
      return new DatasetSeedEntry(
          commonName,
          scientificName,
          family,
          description,
          tags,
          lightNeeds,
          waterFrequencyDays,
          humidity,
          tempRange,
          toxicToPets,
          toxicToPetsSource,
          toxicToHumans,
          toxicToHumansSource,
          difficulty,
          nativeRegion,
          matureSize);
    }
  }
}
