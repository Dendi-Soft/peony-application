package com.dendi.peony.dataset;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;

/**
 * Validates the catalogue image selection before the seeder downloads anything (issue #9
 * follow-up).
 *
 * <p>Every rule is checked in one pass and all violations are reported together. Exact coverage (50
 * seeded species, in seed-file order) is asserted by the committed-file contract test and the
 * Python {@code --check}; this validator enforces the structural and safety rules so a test fixture
 * with fewer species is still valid.
 */
public final class CatalogueImageValidator {

  /** The only host a catalogue image may be downloaded from. */
  public static final String CATALOGUE_IMAGE_HOST = "upload.wikimedia.org";

  /** Allowed canonical licence families. */
  public static final Set<String> ALLOWED_LICENSES =
      Set.of("cc0", "public-domain", "cc-by", "cc-by-sa");

  /** Required number of entries in the committed selection. */
  public static final int EXPECTED_SPECIES = 50;

  private static final int NO_EXPECTED_COUNT = 0;

  private static final int HTTPS_PORT = 443;

  private static final List<TextField> ENTRY_FIELDS =
      List.of(
          new TextField("scientificName", CatalogueImageEntry::scientificName),
          new TextField("commonName", CatalogueImageEntry::commonName));

  private static final List<ImageField> IMAGE_FIELDS =
      List.of(
          new ImageField("title", CatalogueImage::title),
          new ImageField("url", CatalogueImage::url),
          new ImageField("descriptionUrl", CatalogueImage::descriptionUrl),
          new ImageField("author", CatalogueImage::author),
          new ImageField("license", CatalogueImage::license),
          new ImageField("licenseUrl", CatalogueImage::licenseUrl));

  private CatalogueImageValidator() {}

  /**
   * Validates the selection and fails with every problem when any rule is violated.
   *
   * @param file parsed selection file
   * @throws DatasetValidationException when the file is missing species or any entry is invalid
   */
  public static void validate(CatalogueImageFile file) {
    validate(file, NO_EXPECTED_COUNT);
  }

  /**
   * Validates the selection against an expected entry count.
   *
   * <p>The committed-file contract test passes {@link #EXPECTED_SPECIES}; the seeder passes no
   * expectation so a small test fixture remains valid.
   *
   * @param file parsed selection file
   * @param expectedSpecies exact number of required entries, or a non-positive value to skip the
   *     count check
   * @throws DatasetValidationException when the file is missing species or any rule is violated
   */
  public static void validate(CatalogueImageFile file, int expectedSpecies) {
    List<String> problems = collectProblems(file, expectedSpecies);
    if (!problems.isEmpty()) {
      throw new DatasetValidationException(problems);
    }
  }

  /**
   * Collects every rule violation without throwing.
   *
   * @param file parsed selection file, possibly null
   * @return all violations in detection order; empty when the selection is valid
   */
  public static List<String> collectProblems(CatalogueImageFile file) {
    return collectProblems(file, NO_EXPECTED_COUNT);
  }

  /**
   * Collects every rule violation and optionally checks the entry count.
   *
   * @param file parsed selection file, possibly null
   * @param expectedSpecies exact number of required entries, or a non-positive value to skip the
   *     count check
   * @return all violations in detection order; empty when the selection is valid
   */
  public static List<String> collectProblems(CatalogueImageFile file, int expectedSpecies) {
    List<String> problems = new ArrayList<>();
    if (file == null) {
      problems.add("the catalogue image selection is missing");
      return problems;
    }
    if (file.species() == null) {
      problems.add("species must be present");
      return problems;
    }
    if (file.species().isEmpty()) {
      problems.add("species must contain at least one entry");
      return problems;
    }
    if (expectedSpecies > 0 && file.species().size() != expectedSpecies) {
      problems.add(
          "species must contain exactly "
              + expectedSpecies
              + " entries, was "
              + file.species().size());
    }

    Set<String> seenNames = new HashSet<>();
    for (int i = 0; i < file.species().size(); i++) {
      CatalogueImageEntry entry = file.species().get(i);
      if (entry == null) {
        problems.add("species[" + i + "] must not be null");
        continue;
      }
      validateEntry(i, entry, seenNames, problems);
    }
    return problems;
  }

  private static void validateEntry(
      int index, CatalogueImageEntry entry, Set<String> seenNames, List<String> problems) {
    for (TextField field : ENTRY_FIELDS) {
      if (isBlank(field.accessor().apply(entry))) {
        problems.add("species[" + index + "]." + field.name() + " must not be blank");
      }
    }
    String name = entry.scientificName();
    if (!isBlank(name) && !seenNames.add(name.trim().toLowerCase(Locale.ROOT))) {
      problems.add("species[" + index + "].scientificName is duplicated: " + name.trim());
    }

    CatalogueImage image = entry.image();
    if (image == null) {
      problems.add("species[" + index + "].image must be present");
      return;
    }
    for (ImageField field : IMAGE_FIELDS) {
      if (isBlank(field.accessor().apply(image))) {
        problems.add("species[" + index + "].image." + field.name() + " must not be blank");
      }
    }
    validateHttps(index, "descriptionUrl", image.descriptionUrl(), problems);
    validateHttps(index, "licenseUrl", image.licenseUrl(), problems);
    validateImageUrl(index, image.url(), problems);
    if (!isBlank(image.license()) && !ALLOWED_LICENSES.contains(image.license())) {
      problems.add(
          "species["
              + index
              + "].image.license is not an allowed licence: '"
              + image.license()
              + "' (allowed: "
              + ALLOWED_LICENSES
              + ")");
    }
  }

  private static void validateImageUrl(int index, String url, List<String> problems) {
    if (isBlank(url)) {
      return;
    }
    URI uri = parse(index, "url", url, problems);
    if (uri == null) {
      return;
    }
    if (!"https".equalsIgnoreCase(uri.getScheme())) {
      problems.add("species[" + index + "].image.url must use https: " + url);
      return;
    }
    if (!CATALOGUE_IMAGE_HOST.equalsIgnoreCase(uri.getHost())) {
      problems.add(
          "species[" + index + "].image.url host must be " + CATALOGUE_IMAGE_HOST + ": " + url);
    }
    if (uri.getPort() != -1 && uri.getPort() != HTTPS_PORT) {
      problems.add("species[" + index + "].image.url must use the standard https port: " + url);
    }
  }

  private static void validateHttps(
      int index, String fieldName, String url, List<String> problems) {
    if (isBlank(url)) {
      return;
    }
    URI uri = parse(index, fieldName, url, problems);
    if (uri != null && !"https".equalsIgnoreCase(uri.getScheme())) {
      problems.add("species[" + index + "].image." + fieldName + " must use https: " + url);
    }
  }

  private static URI parse(int index, String fieldName, String url, List<String> problems) {
    try {
      return new URI(url);
    } catch (URISyntaxException e) {
      problems.add("species[" + index + "].image." + fieldName + " is not a valid URL: " + url);
      return null;
    }
  }

  /** One required entry text field and the accessor that reads it. */
  private record TextField(String name, Function<CatalogueImageEntry, String> accessor) {}

  /** One required image text field and the accessor that reads it. */
  private record ImageField(String name, Function<CatalogueImage, String> accessor) {}

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
