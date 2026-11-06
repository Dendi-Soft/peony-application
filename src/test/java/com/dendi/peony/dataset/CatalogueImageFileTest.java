package com.dendi.peony.dataset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Validates the committed catalogue image selection packaged on the classpath.
 *
 * <p>This is the catalogue contract for the issue #9 follow-up: exactly 50 entries in the same
 * order as the plant seed file, one whole-plant image per species, and only https URLs on the
 * Wikimedia media host with a whitelisted licence.
 */
class CatalogueImageFileTest {

  private static final String CATALOGUE_RESOURCE = "dataset/catalogue-images.json";

  @Test
  void testLoadCatalogue_HasOneEntryPerSeedSpeciesInOrder() {
    CatalogueImageFile catalogue = CatalogueImageFile.load(CATALOGUE_RESOURCE);
    DatasetSeedFile seed = DatasetSeedFile.load();

    assertEquals(50, catalogue.species().size(), "the catalogue selection must contain 50 species");
    List<String> catalogueNames =
        catalogue.species().stream().map(CatalogueImageEntry::scientificName).toList();
    List<String> seedNames = seed.plants().stream().map(DatasetSeedEntry::scientificName).toList();
    assertEquals(
        seedNames, catalogueNames, "species and their order must match the plant seed file");
  }

  @Test
  void testLoadCatalogue_PopulatesEveryFieldAndLicence() {
    CatalogueImageFile catalogue = CatalogueImageFile.load(CATALOGUE_RESOURCE);

    assertFalse(catalogue.source().isBlank(), "the source must be named");
    assertTrue(
        catalogue.licenses().containsAll(CatalogueImageValidator.ALLOWED_LICENSES),
        "the licence summary must list every accepted family");
    for (CatalogueImageEntry entry : catalogue.species()) {
      String name = entry.scientificName();
      assertFalse(entry.commonName().isBlank(), name + " has a blank commonName");
      CatalogueImage image = entry.image();
      assertFalse(image.title().isBlank(), name + " has a blank title");
      assertFalse(image.url().isBlank(), name + " has a blank url");
      assertFalse(image.descriptionUrl().isBlank(), name + " has a blank descriptionUrl");
      assertFalse(image.author().isBlank(), name + " has a blank author");
      assertTrue(
          CatalogueImageValidator.ALLOWED_LICENSES.contains(image.license()),
          name + " has a licence outside the whitelist: " + image.license());
      assertFalse(image.licenseUrl().isBlank(), name + " has a blank licenseUrl");
    }
  }

  @Test
  void testLoadCatalogue_UsesHttpsWikimediaMediaUrls() {
    CatalogueImageFile catalogue = CatalogueImageFile.load(CATALOGUE_RESOURCE);

    for (CatalogueImageEntry entry : catalogue.species()) {
      CatalogueImage image = entry.image();
      URI url = URI.create(image.url());
      assertEquals("https", url.getScheme(), entry.scientificName() + " url must be https");
      assertEquals(
          CatalogueImageValidator.CATALOGUE_IMAGE_HOST,
          url.getHost(),
          entry.scientificName() + " must use the Wikimedia media host");
      assertTrue(
          image.descriptionUrl().startsWith("https://"),
          entry.scientificName() + " descriptionUrl must be https");
      assertTrue(
          image.licenseUrl().startsWith("https://"),
          entry.scientificName() + " licenseUrl must be https");
    }
  }

  @Test
  void testValidate_AcceptsTheCommittedSelection() {
    CatalogueImageValidator.validate(
        CatalogueImageFile.load(CATALOGUE_RESOURCE), CatalogueImageValidator.EXPECTED_SPECIES);
  }
}
