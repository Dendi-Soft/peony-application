package com.dendi.peony.dataset;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Tests the catalogue image selection validator (issue #9 follow-up): missing fields, unsafe or
 * foreign URLs, unwhitelisted licences, and empty or duplicate species lists are rejected before
 * the seeder downloads anything.
 */
class CatalogueImageValidatorTest {

  private static final String VALID_URL =
      "https://upload.wikimedia.org/wikipedia/commons/1/1a/Plant.jpg";

  @Test
  void testValidate_AcceptsCompleteSelection() {
    CatalogueImageValidator.validate(catalogue(entry("Dracaena trifasciata", image())));
  }

  @Test
  void testValidate_RejectsMissingFile() {
    List<String> problems = CatalogueImageValidator.collectProblems(null);

    assertTrue(problems.stream().anyMatch(problem -> problem.contains("missing")));
  }

  @Test
  void testValidate_RejectsEmptySpeciesList() {
    List<String> problems =
        CatalogueImageValidator.collectProblems(
            new CatalogueImageFile("Wikimedia Commons", "2026-11-06", List.of("cc-by"), List.of()));

    assertTrue(problems.stream().anyMatch(problem -> problem.contains("at least one")));
  }

  @Test
  void testValidate_RejectsWrongSpeciesCount() {
    CatalogueImageFile file =
        catalogue(entry("Dracaena trifasciata", image()), entry("Zamioculcas zamiifolia", image()));

    List<String> problems =
        CatalogueImageValidator.collectProblems(file, CatalogueImageValidator.EXPECTED_SPECIES);

    assertTrue(problems.stream().anyMatch(problem -> problem.contains("exactly 50")));
  }

  @Test
  void testValidate_RejectsDuplicateSpecies() {
    CatalogueImageFile file =
        catalogue(entry("Dracaena trifasciata", image()), entry("dracaena trifasciata", image()));

    List<String> problems = CatalogueImageValidator.collectProblems(file);

    assertTrue(problems.stream().anyMatch(problem -> problem.contains("duplicated")));
  }

  @Test
  void testValidate_RejectsBlankRequiredField() {
    CatalogueImageFile file =
        catalogue(
            entry(
                "Dracaena trifasciata",
                new CatalogueImage(
                    "File:x.jpg",
                    VALID_URL,
                    "https://commons.wikimedia.org/wiki/File:x.jpg",
                    " ",
                    "cc-by",
                    "https://creativecommons.org/licenses/by/4.0")));

    List<String> problems = CatalogueImageValidator.collectProblems(file);

    assertTrue(problems.stream().anyMatch(problem -> problem.contains("author")));
  }

  @Test
  void testValidate_RejectsNonHttpsImageUrl() {
    CatalogueImageFile file =
        catalogue(entry("Dracaena trifasciata", withUrl("http://upload.wikimedia.org/x.jpg")));

    List<String> problems = CatalogueImageValidator.collectProblems(file);

    assertTrue(problems.stream().anyMatch(problem -> problem.contains("must use https")));
  }

  @Test
  void testValidate_RejectsForeignImageHost() {
    CatalogueImageFile file =
        catalogue(entry("Dracaena trifasciata", withUrl("https://evil.example/x.jpg")));

    List<String> problems = CatalogueImageValidator.collectProblems(file);

    assertTrue(problems.stream().anyMatch(problem -> problem.contains("upload.wikimedia.org")));
  }

  @Test
  void testValidate_RejectsLicenceOutsideWhitelist() {
    CatalogueImageFile file = catalogue(entry("Dracaena trifasciata", withLicence("cc-by-nc")));

    List<String> problems = CatalogueImageValidator.collectProblems(file);

    assertTrue(problems.stream().anyMatch(problem -> problem.contains("licence")));
  }

  @Test
  void testValidate_ThrowsWithEveryProblem() {
    CatalogueImageFile file =
        catalogue(
            entry("Dracaena trifasciata", withLicence("gfdl")),
            entry("Zamioculcas zamiifolia", withUrl("ftp://upload.wikimedia.org/x.jpg")));

    DatasetValidationException exception =
        assertThrows(
            DatasetValidationException.class, () -> CatalogueImageValidator.validate(file));

    assertTrue(exception.problems().size() >= 2);
  }

  private static CatalogueImageFile catalogue(CatalogueImageEntry... entries) {
    return new CatalogueImageFile(
        "Wikimedia Commons",
        "2026-11-06",
        List.of("cc0", "public-domain", "cc-by", "cc-by-sa"),
        List.of(entries));
  }

  private static CatalogueImageEntry entry(String scientificName, CatalogueImage image) {
    return new CatalogueImageEntry(scientificName, "Common name", image);
  }

  private static CatalogueImage image() {
    return new CatalogueImage(
        "File:Plant.jpg",
        VALID_URL,
        "https://commons.wikimedia.org/wiki/File:Plant.jpg",
        "Ada Lovelace",
        "cc-by-sa",
        "https://creativecommons.org/licenses/by-sa/4.0");
  }

  private static CatalogueImage withUrl(String url) {
    CatalogueImage base = image();
    return new CatalogueImage(
        base.title(), url, base.descriptionUrl(), base.author(), base.license(), base.licenseUrl());
  }

  private static CatalogueImage withLicence(String licence) {
    CatalogueImage base = image();
    return new CatalogueImage(
        base.title(), base.url(), base.descriptionUrl(), base.author(), licence, base.licenseUrl());
  }
}
