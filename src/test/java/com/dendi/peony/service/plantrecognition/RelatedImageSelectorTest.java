package com.dendi.peony.service.plantrecognition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** Tests the selection rules for PlantNet related images (organ, licence, URL, species key). */
class RelatedImageSelectorTest {

  /** A helper that runs the selector over the JSON of a PlantNet {@code results} array. */
  private static List<RelatedImage> select(String resultsJson) {
    JsonNode root = JsonUtil.readTree("{\"results\":" + resultsJson + "}");
    return RelatedImageSelector.select(root.get("results"));
  }

  /** Builds one related-image JSON object. */
  private static String image(String organ, String license, String mediumUrl) {
    String organField = organ == null ? "" : "\"organ\":\"" + organ + "\",";
    String licenseField = license == null ? "" : "\"license\":\"" + license + "\",";
    String urlField =
        mediumUrl == null
            ? "\"url\":{\"o\":\"https://bs.plantnet.org/image/o/x\"},"
            : "\"url\":{\"m\":\"" + mediumUrl + "\"},";
    return """
        {%s"author":"A. Author",%s"citation":"Cite this",%s"date":{"timestamp":1}}
        """
        .formatted(organField, licenseField, urlField);
  }

  private static String hit(double score, String scientificNameWithoutAuthor, String imagesJson) {
    String images = imagesJson == null ? "" : ",\"images\":" + imagesJson;
    return """
        {"score":%s,"species":{"scientificNameWithoutAuthor":"%s"}%s}
        """
        .formatted(score, scientificNameWithoutAuthor, images);
  }

  private static String array(String... images) {
    return "[" + String.join(",", images) + "]";
  }

  private static final String MEDIUM = "https://bs.plantnet.org/image/m/abc";

  /** A whole-plant image wins over leaf and other organs. */
  @Test
  void testSelect_PrefersHabit_WhenHabitImagePresent() {
    String results =
        hit(
            0.9,
            "Ajuga reptans",
            array(
                image("leaf", "cc-by-sa", MEDIUM + "1"),
                image("habit", "cc-by-sa", MEDIUM + "2"),
                image("flower", "cc-by-sa", MEDIUM + "3")));

    List<RelatedImage> selected = select("[" + results + "]");

    assertEquals(1, selected.size());
    assertEquals("habit", selected.get(0).organ());
    assertTrue(selected.get(0).mediumUrl().endsWith("2"));
  }

  /** A leaf image wins when no habit image exists. */
  @Test
  void testSelect_PrefersLeaf_WhenHabitMissing() {
    String results =
        hit(
            0.9,
            "Ajuga reptans",
            array(
                image("bark", "cc-by-sa", MEDIUM + "1"), image("leaf", "cc-by-sa", MEDIUM + "2")));

    assertEquals("leaf", select("[" + results + "]").get(0).organ());
  }

  /** Any other organ is acceptable when neither habit nor leaf is available. */
  @Test
  void testSelect_AcceptsAnyOtherOrgan_WhenHabitAndLeafMissing() {
    String results = hit(0.9, "Ajuga reptans", array(image("flower", "cc-by-sa", MEDIUM + "1")));

    assertEquals("flower", select("[" + results + "]").get(0).organ());
  }

  /** An image without an organ is acceptable as the last resort. */
  @Test
  void testSelect_AcceptsUnknownOrgan_WhenNoRankedOrganExists() {
    String results = hit(0.9, "Ajuga reptans", array(image(null, "cc-by-sa", MEDIUM)));

    assertEquals(1, select("[" + results + "]").size());
  }

  /** Equal-ranked images keep the upstream order. */
  @Test
  void testSelect_KeepsUpstreamOrder_WhenOrganRanksTie() {
    String results =
        hit(
            0.9,
            "Ajuga reptans",
            array(
                image("leaf", "cc-by-sa", MEDIUM + "first"),
                image("leaf", "cc-by-sa", MEDIUM + "second")));

    assertTrue(select("[" + results + "]").get(0).mediumUrl().endsWith("first"));
  }

  /** Accepted licences are stored in canonical lowercase hyphenated form. */
  @Test
  void testSelect_StoresCanonicalLicense_WhenLicenseIsAcceptedVariant() {
    String results =
        """
        [%s,%s,%s,%s]
        """
            .formatted(
                hit(0.9, "Ajuga reptans", array(image("leaf", "CC BY-SA", MEDIUM + "1"))),
                hit(0.9, "Buxus sempervirens", array(image("leaf", "cc_by", MEDIUM + "2"))),
                hit(0.9, "Carex flacca", array(image("leaf", "public domain", MEDIUM + "3"))),
                hit(0.9, "Dianthus carthusianorum", array(image("leaf", "CC0", MEDIUM + "4"))));

    Map<String, String> licenses =
        select(results).stream()
            .collect(Collectors.toMap(RelatedImage::scientificName, RelatedImage::license));

    assertEquals("cc-by-sa", licenses.get("ajuga reptans"));
    assertEquals("cc-by", licenses.get("buxus sempervirens"));
    assertEquals("public-domain", licenses.get("carex flacca"));
    assertEquals("cc0", licenses.get("dianthus carthusianorum"));
  }

  /** NC, ND, unknown, and blank licences are rejected. */
  @Test
  void testSelect_RejectsDisallowedLicenses_WhenNoCandidatePasses() {
    String results =
        """
        [%s,%s,%s,%s,%s]
        """
            .formatted(
                hit(0.9, "Ajuga reptans", array(image("leaf", "cc-by-nc", MEDIUM + "1"))),
                hit(0.9, "Buxus sempervirens", array(image("leaf", "cc-by-nd", MEDIUM + "2"))),
                hit(0.9, "Carex flacca", array(image("leaf", "all-rights-reserved", MEDIUM + "3"))),
                hit(0.9, "Dianthus carthusianorum", array(image("leaf", "cc-by-sa-4.0", MEDIUM))),
                hit(0.9, "Erica tetralix", array(image("leaf", "", MEDIUM))));

    assertTrue(select(results).isEmpty());
  }

  /** An image without a medium URL is ignored, even when its licence is acceptable. */
  @Test
  void testSelect_RejectsImage_WhenMediumUrlMissing() {
    String results = hit(0.9, "Ajuga reptans", array(image("habit", "cc-by-sa", null)));

    assertTrue(select("[" + results + "]").isEmpty());
  }

  /** Only absolute Web URLs are usable; other schemes and relative URLs are ignored. */
  @Test
  void testSelect_RejectsImage_WhenUrlIsNotAnAbsoluteWebUrl() {
    String results =
        """
        [%s,%s,%s]
        """
            .formatted(
                hit(
                    0.9,
                    "Ajuga reptans",
                    array(image("leaf", "cc-by-sa", "ftp://bs.plantnet.org/x.jpg"))),
                hit(0.9, "Buxus sempervirens", array(image("leaf", "cc-by-sa", "/image/m/x"))),
                hit(0.9, "Carex flacca", array(image("leaf", "cc-by-sa", "javascript:alert(1)"))));

    assertTrue(select(results).isEmpty());
  }

  /** A hit below the catalogue-draft threshold cannot stage a species image. */
  @Test
  void testSelect_RejectsHit_WhenScoreBelowDraftThreshold() {
    assertEquals(
        List.of(),
        select("[" + hit(0.29, "Ajuga reptans", array(image("leaf", "cc-by-sa", MEDIUM))) + "]"));
    assertEquals(
        1,
        select(
                "["
                    + hit(0.30, "Buxus sempervirens", array(image("leaf", "cc-by-sa", MEDIUM)))
                    + "]")
            .size());
  }

  /** Missing, empty, and non-array image collections are tolerated. */
  @Test
  void testSelect_ReturnsNoCandidates_WhenImagesAreAbsentEmptyOrMalformed() {
    String results =
        """
        [%s,%s,%s,%s]
        """
            .formatted(
                "{\"score\":0.9,\"species\":{\"scientificNameWithoutAuthor\":\"Ajuga reptans\"}}",
                hit(0.9, "Buxus sempervirens", "[]"),
                hit(0.9, "Carex flacca", "\"not-an-array\""),
                hit(0.9, "Dianthus carthusianorum", "[null,42,\"text\",{}]"));

    assertTrue(select(results).isEmpty());
  }

  /** Only one candidate per species is returned; a later hit replaces an earlier one. */
  @Test
  void testSelect_KeepsLastCandidate_WhenSpeciesAppearsTwice() {
    String results =
        """
        [%s,%s]
        """
            .formatted(
                hit(0.9, "Ajuga reptans", array(image("leaf", "cc-by-sa", MEDIUM + "first"))),
                hit(0.8, "Ajuga reptans", array(image("habit", "cc-by", MEDIUM + "second"))));

    List<RelatedImage> selected = select(results);

    assertEquals(1, selected.size());
    assertEquals("habit", selected.get(0).organ());
    assertEquals("cc-by", selected.get(0).license());
    assertTrue(selected.get(0).mediumUrl().endsWith("second"));
  }

  /** The staging key is the normalized binomial, falling back to the full name. */
  @Test
  void testSelect_NormalizesBinomial_WhenSpeciesHasAuthorship() {
    String results =
        """
        {"score":0.9,"species":{"scientificName":"Ajuga Reptans L."},"images":[%s]}
        """
            .formatted(image("leaf", "cc-by-sa", MEDIUM));

    assertEquals("ajuga reptans", select("[" + results + "]").get(0).scientificName());
  }
}
