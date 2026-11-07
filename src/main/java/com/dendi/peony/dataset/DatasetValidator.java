package com.dendi.peony.dataset;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;

/**
 * Validates the curated dataset before any row is written.
 *
 * <p>Every rule is checked in one pass and all violations are reported together, so a malformed
 * seed file fails once with the complete list instead of one error per run. Validation happens
 * before the first upsert; the seeder also runs in a transaction, so a failure leaves the database
 * untouched.
 */
public final class DatasetValidator {

  /** Supported seed-file format version. */
  public static final int SUPPORTED_VERSION = 1;

  /** Required number of curated plants. */
  public static final int EXPECTED_PLANT_COUNT = 50;

  /** Allowed difficulty labels. */
  public static final Set<String> ALLOWED_DIFFICULTIES = Set.of("Easy", "Moderate", "Hard");

  private static final int MIN_WATER_FREQUENCY_DAYS = 1;
  private static final int MAX_WATER_FREQUENCY_DAYS = 90;

  private static final List<TextField> TEXT_FIELDS =
      List.of(
          new TextField("commonName", DatasetSeedEntry::commonName),
          new TextField("scientificName", DatasetSeedEntry::scientificName),
          new TextField("family", DatasetSeedEntry::family),
          new TextField("description", DatasetSeedEntry::description),
          new TextField("lightNeeds", DatasetSeedEntry::lightNeeds),
          new TextField("humidity", DatasetSeedEntry::humidity),
          new TextField("tempRange", DatasetSeedEntry::tempRange),
          new TextField("nativeRegion", DatasetSeedEntry::nativeRegion),
          new TextField("matureSize", DatasetSeedEntry::matureSize));

  /** Required non-blank text fields, in reporting order. */
  public static final List<String> REQUIRED_TEXT_FIELDS =
      TEXT_FIELDS.stream().map(TextField::name).toList();

  private DatasetValidator() {}

  /**
   * Validates the dataset and fails with every problem when any rule is violated.
   *
   * @param file parsed seed file
   * @throws DatasetValidationException when the file is missing entries or any entry is invalid
   */
  public static void validate(DatasetSeedFile file) {
    List<String> problems = collectProblems(file);
    if (!problems.isEmpty()) {
      throw new DatasetValidationException(problems);
    }
  }

  /**
   * Collects every rule violation without throwing.
   *
   * @param file parsed seed file, possibly null
   * @return all violations in detection order; empty when the dataset is valid
   */
  public static List<String> collectProblems(DatasetSeedFile file) {
    List<String> problems = new ArrayList<>();
    if (file == null) {
      problems.add("the seed file is missing");
      return problems;
    }
    if (file.version() != SUPPORTED_VERSION) {
      problems.add("version must be " + SUPPORTED_VERSION + ", was " + file.version());
    }

    List<DatasetSeedEntry> plants = file.plants();
    if (plants == null) {
      problems.add("plants must be present");
      return problems;
    }
    if (plants.size() != EXPECTED_PLANT_COUNT) {
      problems.add(
          "plants must contain exactly " + EXPECTED_PLANT_COUNT + " entries, was " + plants.size());
    }

    Set<String> seenNames = new HashSet<>();
    for (int i = 0; i < plants.size(); i++) {
      DatasetSeedEntry entry = plants.get(i);
      if (entry == null) {
        problems.add("plant[" + i + "] must not be null");
        continue;
      }
      validateEntry(i, entry, problems);
      validateScientificName(i, entry.scientificName(), seenNames, problems);
    }
    return problems;
  }

  private static void validateEntry(int index, DatasetSeedEntry entry, List<String> problems) {
    for (TextField field : TEXT_FIELDS) {
      if (isBlank(field.accessor().apply(entry))) {
        problems.add("plant[" + index + "]." + field.name() + " must not be blank");
      }
    }
    if (entry.waterFrequencyDays() == null) {
      problems.add("plant[" + index + "].waterFrequencyDays must not be null");
    } else if (entry.waterFrequencyDays() < MIN_WATER_FREQUENCY_DAYS
        || entry.waterFrequencyDays() > MAX_WATER_FREQUENCY_DAYS) {
      problems.add(
          "plant["
              + index
              + "].waterFrequencyDays must be between "
              + MIN_WATER_FREQUENCY_DAYS
              + " and "
              + MAX_WATER_FREQUENCY_DAYS
              + ", was "
              + entry.waterFrequencyDays());
    }
    if (entry.toxicToPets() == null) {
      problems.add("plant[" + index + "].toxicToPets must not be null");
    }
    if (entry.toxicToHumans() == null) {
      problems.add("plant[" + index + "].toxicToHumans must not be null");
    }
    if (isBlank(entry.toxicToPetsSource())) {
      problems.add("plant[" + index + "].toxicToPetsSource must not be blank");
    }
    if (isBlank(entry.toxicToHumansSource())) {
      problems.add("plant[" + index + "].toxicToHumansSource must not be blank");
    }
    if (entry.difficulty() == null || !ALLOWED_DIFFICULTIES.contains(entry.difficulty())) {
      problems.add(
          "plant["
              + index
              + "].difficulty must be one of "
              + ALLOWED_DIFFICULTIES
              + ", was '"
              + entry.difficulty()
              + "'");
    }
    if (entry.tags() == null || entry.tags().isEmpty()) {
      problems.add("plant[" + index + "].tags must not be empty");
    } else {
      for (int tagIndex = 0; tagIndex < entry.tags().size(); tagIndex++) {
        if (isBlank(entry.tags().get(tagIndex))) {
          problems.add("plant[" + index + "].tags[" + tagIndex + "] must not be blank");
        }
      }
    }
  }

  private static void validateScientificName(
      int index, String scientificName, Set<String> seenNames, List<String> problems) {
    if (isBlank(scientificName)) {
      return;
    }
    String normalized = scientificName.trim().toLowerCase(Locale.ROOT);
    if (!seenNames.add(normalized)) {
      problems.add("plant[" + index + "].scientificName is duplicated: " + scientificName.trim());
    }
    String[] tokens = scientificName.trim().split("\\s+");
    if (tokens.length != 2) {
      problems.add(
          "plant["
              + index
              + "].scientificName must be a binomial of exactly two tokens "
              + "(genus and species): "
              + scientificName.trim());
    }
  }

  /** One required text field and the accessor that reads it from an entry. */
  private record TextField(String name, Function<DatasetSeedEntry, String> accessor) {}

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
