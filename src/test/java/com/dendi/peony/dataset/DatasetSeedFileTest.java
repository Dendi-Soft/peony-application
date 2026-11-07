package com.dendi.peony.dataset;

import static com.dendi.peony.dataset.DatasetValidator.ALLOWED_DIFFICULTIES;
import static com.dendi.peony.dataset.DatasetValidator.EXPECTED_PLANT_COUNT;
import static com.dendi.peony.dataset.DatasetValidator.REQUIRED_TEXT_FIELDS;
import static com.dendi.peony.dataset.DatasetValidator.SUPPORTED_VERSION;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Validates the curated plant seed file packaged on the classpath.
 *
 * <p>This test is the dataset contract for issue #9: exactly 50 fully populated species entries,
 * unique accepted binomials, real HTTPS references for every toxicity boolean, and a fully
 * populated care profile. It fails loudly while the seed file is missing or malformed.
 */
class DatasetSeedFileTest {

  private static final ObjectMapper JSON = new ObjectMapper();
  private static final String SEED_RESOURCE = "/dataset/plants-seed.json";

  private static final List<String> PRIORITY_COMMON_NAMES =
      List.of(
          "Snake Plant",
          "ZZ Plant",
          "Pothos",
          "Spider Plant",
          "Peace Lily",
          "Rubber Plant",
          "Aloe Vera",
          "Monstera",
          "Fiddle Leaf Fig",
          "Calathea");

  private static final List<String> RESOLVED_SPECIES =
      List.of(
          "Goeppertia orbifolia",
          "Philodendron hederaceum",
          "Hoya carnosa",
          "Tillandsia ionantha",
          "Anthurium andraeanum",
          "Echeveria elegans",
          "Haworthiopsis fasciata",
          "Opuntia ficus-indica",
          "Yucca gigantea",
          "Dieffenbachia seguine",
          "Aglaonema commutatum",
          "Tradescantia zebrina",
          "Begonia rex",
          "Alocasia macrorrhizos",
          "Spathiphyllum wallisii",
          "Phalaenopsis amabilis",
          "Adiantum raddianum");

  private static final Set<String> BARE_GENERA =
      Set.of(
          "Calathea",
          "Philodendron",
          "Hoya",
          "Tillandsia",
          "Anthurium",
          "Echeveria",
          "Haworthia",
          "Opuntia",
          "Yucca",
          "Dieffenbachia",
          "Aglaonema",
          "Tradescantia",
          "Begonia",
          "Alocasia",
          "Spathiphyllum",
          "Phalaenopsis",
          "Adiantum");

  private static final Pattern KEBAB_CASE = Pattern.compile("[a-z0-9]+(?:-[a-z0-9]+)*");
  private static final Pattern WHITESPACE = Pattern.compile("\\s+");

  @Test
  void testLoadSeedFile_ContainsExpectedVersionAndPlantCount() {
    JsonNode root = loadSeedFile();

    JsonNode version = root.get("version");
    assertNotNull(version, "seed file must declare a version");
    assertEquals(SUPPORTED_VERSION, version.asInt(), "seed file version must be supported");

    JsonNode plants = root.get("plants");
    assertNotNull(plants, "seed file must contain a plants array");
    assertTrue(plants.isArray(), "plants must be a JSON array");
    assertEquals(
        EXPECTED_PLANT_COUNT,
        plants.size(),
        "the curated dataset must contain exactly " + EXPECTED_PLANT_COUNT + " plants");
  }

  @Test
  void testLoadSeedFile_HasUniqueTrimmedScientificNames() {
    JsonNode plants = plants();
    Set<String> seen = new HashSet<>();
    List<String> duplicates = new ArrayList<>();
    for (JsonNode plant : plants) {
      String name = trimmedText(plant, "scientificName");
      if (name == null) {
        continue;
      }
      if (!seen.add(name.toLowerCase(Locale.ROOT))) {
        duplicates.add(name);
      }
    }
    assertTrue(duplicates.isEmpty(), "duplicate scientific names: " + duplicates);
  }

  @Test
  void testLoadSeedFile_PopulatesEveryRequiredField() {
    JsonNode plants = plants();
    List<String> problems = new ArrayList<>();
    for (int i = 0; i < plants.size(); i++) {
      JsonNode plant = plants.get(i);
      for (String field : REQUIRED_TEXT_FIELDS) {
        if (isBlank(text(plant, field))) {
          problems.add("plant[" + i + "] has a blank " + field);
        }
      }
      assertBooleanField(plant, i, "toxicToPets", problems);
      assertBooleanField(plant, i, "toxicToHumans", problems);
    }
    assertTrue(problems.isEmpty(), String.join(System.lineSeparator(), problems));
  }

  @Test
  void testLoadSeedFile_HasValidWateringFrequencyAndDifficulty() {
    JsonNode plants = plants();
    List<String> problems = new ArrayList<>();
    for (int i = 0; i < plants.size(); i++) {
      JsonNode plant = plants.get(i);
      JsonNode frequency = plant.get("waterFrequencyDays");
      if (frequency == null || !frequency.isInt()) {
        problems.add("plant[" + i + "] waterFrequencyDays must be an integer");
      } else if (frequency.asInt() < 1 || frequency.asInt() > 90) {
        problems.add("plant[" + i + "] waterFrequencyDays must be 1-90, was " + frequency.asInt());
      }
      String difficulty = text(plant, "difficulty");
      if (difficulty == null || !ALLOWED_DIFFICULTIES.contains(difficulty)) {
        problems.add(
            "plant["
                + i
                + "] difficulty must be one of "
                + ALLOWED_DIFFICULTIES
                + ", was "
                + difficulty);
      }
    }
    assertTrue(problems.isEmpty(), String.join(System.lineSeparator(), problems));
  }

  @Test
  void testLoadSeedFile_UsesBinomialScientificNames() {
    JsonNode plants = plants();
    List<String> problems = new ArrayList<>();
    for (int i = 0; i < plants.size(); i++) {
      String name = trimmedText(plants.get(i), "scientificName");
      if (name == null) {
        continue;
      }
      String[] tokens = WHITESPACE.split(name);
      if (tokens.length != 2) {
        problems.add("plant[" + i + "] scientific name is not a two-token binomial: " + name);
      }
      if (BARE_GENERA.contains(tokens[0]) && tokens.length == 1) {
        problems.add("plant[" + i + "] still uses a bare genus: " + name);
      }
    }
    assertTrue(problems.isEmpty(), String.join(System.lineSeparator(), problems));
  }

  @Test
  void testLoadSeedFile_ContainsTheTenPriorityPlants() {
    Set<String> commonNames = new HashSet<>();
    for (JsonNode plant : plants()) {
      String commonName = trimmedText(plant, "commonName");
      if (commonName != null) {
        commonNames.add(commonName);
      }
    }
    for (String priority : PRIORITY_COMMON_NAMES) {
      assertTrue(commonNames.contains(priority), "missing priority plant: " + priority);
    }
  }

  @Test
  void testLoadSeedFile_ResolvesGenusLevelEntriesToSpecies() {
    Set<String> scientificNames = new HashSet<>();
    for (JsonNode plant : plants()) {
      String name = trimmedText(plant, "scientificName");
      if (name != null) {
        scientificNames.add(name);
      }
    }
    for (String species : RESOLVED_SPECIES) {
      assertTrue(scientificNames.contains(species), "missing curated species: " + species);
    }
  }

  @Test
  void testLoadSeedFile_HasHttpsToxicitySourcesForEveryBoolean() {
    JsonNode plants = plants();
    List<String> problems = new ArrayList<>();
    for (int i = 0; i < plants.size(); i++) {
      assertHttpsSource(plants.get(i), i, "toxicToPetsSource", problems);
      assertHttpsSource(plants.get(i), i, "toxicToHumansSource", problems);
    }
    assertTrue(problems.isEmpty(), String.join(System.lineSeparator(), problems));
  }

  @Test
  void testLoadSeedFile_HasTwoToSixKebabCaseTags() {
    JsonNode plants = plants();
    List<String> problems = new ArrayList<>();
    for (int i = 0; i < plants.size(); i++) {
      JsonNode tags = plants.get(i).get("tags");
      if (tags == null || !tags.isArray()) {
        problems.add("plant[" + i + "] tags must be an array");
        continue;
      }
      if (tags.size() < 2 || tags.size() > 6) {
        problems.add("plant[" + i + "] tags must contain 2-6 entries, had " + tags.size());
      }
      for (JsonNode tag : tags) {
        if (!tag.isString() || !KEBAB_CASE.matcher(tag.asString()).matches()) {
          problems.add("plant[" + i + "] has a non-kebab-case tag: " + tag);
        }
      }
    }
    assertTrue(problems.isEmpty(), String.join(System.lineSeparator(), problems));
  }

  private static void assertBooleanField(
      JsonNode plant, int index, String field, List<String> problems) {
    JsonNode value = plant.get(field);
    if (value == null || !value.isBoolean()) {
      problems.add("plant[" + index + "] " + field + " must be a boolean");
    }
  }

  private static void assertHttpsSource(
      JsonNode plant, int index, String field, List<String> problems) {
    String source = text(plant, field);
    if (isBlank(source)) {
      problems.add("plant[" + index + "] " + field + " must not be blank");
    } else if (!source.startsWith("https://")) {
      problems.add("plant[" + index + "] " + field + " must use https: " + source);
    }
  }

  private static JsonNode loadSeedFile() {
    try (InputStream in = DatasetSeedFileTest.class.getResourceAsStream(SEED_RESOURCE)) {
      if (in == null) {
        throw new IllegalStateException("Missing classpath seed file: " + SEED_RESOURCE);
      }
      return JSON.readTree(in);
    } catch (IOException e) {
      throw new IllegalStateException("Failed to read " + SEED_RESOURCE, e);
    }
  }

  private static JsonNode plants() {
    JsonNode plants = loadSeedFile().get("plants");
    if (plants == null || !plants.isArray()) {
      throw new IllegalStateException("seed file has no plants array");
    }
    return plants;
  }

  private static String text(JsonNode node, String field) {
    JsonNode value = node.get(field);
    return value != null && value.isString() ? value.asString() : null;
  }

  private static String trimmedText(JsonNode node, String field) {
    String value = text(node, field);
    return value == null ? null : value.trim();
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
