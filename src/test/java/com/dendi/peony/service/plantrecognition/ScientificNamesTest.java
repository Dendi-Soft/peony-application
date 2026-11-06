package com.dendi.peony.service.plantrecognition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.dendi.peony.api.model.Species;
import org.junit.jupiter.api.Test;

/** Tests the shared scientific-name normalization used by recognition, staging, and from-scan. */
class ScientificNamesTest {

  /** Normalization trims, collapses internal whitespace, and lowercases with the root locale. */
  @Test
  void testNormalize_TrimsCollapsesAndLowercases_WhenNameHasDecorations() {
    assertEquals("caladium bicolor", ScientificNames.normalize("  Caladium \t  Bicolor  "));
    assertEquals("ajuga reptans l.", ScientificNames.normalize("Ajuga Reptans L."));
  }

  /** A missing or blank name has no normalized form. */
  @Test
  void testNormalize_ReturnsNull_WhenNameIsMissingOrBlank() {
    assertNull(ScientificNames.normalize(null));
    assertNull(ScientificNames.normalize("   "));
  }

  /**
   * The binomial is the first two whitespace-separated tokens of the author-free name, so an
   * infraspecific name reduces to the species pair.
   */
  @Test
  void testBinomial_ReducesInfraspecificName_WhenAuthorFreeNameHasSubspecies() {
    Species species = new Species();
    species.setScientificName("Caladium bicolor subsp. bicolor (Aiton) Vent.");
    species.setScientificNameWithoutAuthor("Caladium bicolor subsp. bicolor");

    assertEquals("Caladium bicolor", ScientificNames.binomial(species));
  }

  /** The binomial falls back to the full name when the API omits the author-free name. */
  @Test
  void testBinomial_FallsBackToFullName_WhenAuthorFreeNameMissing() {
    Species species = new Species();
    species.setScientificName("Ajuga reptans L.");

    assertEquals("Ajuga reptans", ScientificNames.binomial(species));
  }

  /** A single-token name yields no binomial. */
  @Test
  void testBinomial_ReturnsNull_WhenNameHasFewerThanTwoTokens() {
    Species species = new Species();
    species.setScientificName("Ajuga");

    assertNull(ScientificNames.binomial(species));
    assertNull(ScientificNames.binomial("Ajuga"));
  }

  /** The string overload accepts a full name with authorship and drops the trailing tokens. */
  @Test
  void testBinomial_DropsAuthorship_WhenFullNameCarriesAuthor() {
    assertEquals("Ajuga reptans", ScientificNames.binomial("Ajuga reptans L."));
    assertEquals("Ajuga reptans", ScientificNames.binomial(null, "Ajuga reptans L."));
    assertEquals("Ajuga reptans", ScientificNames.binomial("", "Ajuga reptans L."));
  }
}
