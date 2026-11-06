package com.dendi.peony.service.plantrecognition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Tests the accepted PlantNet image licences and their canonical wire values (ADR-008). */
class LicenseTest {

  /** Every accepted spelling maps to the canonical licence stored with the image. */
  @Test
  void testParse_MapsAcceptedFormsToCanonicalLicense() {
    Map<String, License> accepted =
        Map.ofEntries(
            Map.entry("cc0", License.CC0),
            Map.entry("CC0 1.0", License.CC0),
            Map.entry("cc0-1.0", License.CC0),
            Map.entry("public-domain", License.PUBLIC_DOMAIN),
            Map.entry("Public Domain", License.PUBLIC_DOMAIN),
            Map.entry("cc-by", License.CC_BY),
            Map.entry("cc-by-1.0", License.CC_BY),
            Map.entry("cc-by-2.0", License.CC_BY),
            Map.entry("cc-by-2.5", License.CC_BY),
            Map.entry("cc-by-3.0", License.CC_BY),
            Map.entry("cc-by-4.0", License.CC_BY),
            Map.entry("CC BY 4.0", License.CC_BY),
            Map.entry("cc-by-sa", License.CC_BY_SA),
            Map.entry("cc-by-sa-1.0", License.CC_BY_SA),
            Map.entry("cc-by-sa-2.0", License.CC_BY_SA),
            Map.entry("cc-by-sa-2.5", License.CC_BY_SA),
            Map.entry("cc-by-sa-3.0", License.CC_BY_SA),
            Map.entry("cc-by-sa-4.0", License.CC_BY_SA),
            Map.entry("cc_by_sa_3.0", License.CC_BY_SA));

    accepted.forEach(
        (raw, expected) ->
            assertEquals(
                Optional.of(expected), License.parse(raw), "expected " + raw + " to be accepted"));
  }

  /** Non-commercial, no-derivatives, unknown, and blank values are rejected. */
  @Test
  void testParse_RejectsDisallowedLicenses() {
    List<String> rejected =
        List.of(
            "cc-by-nc",
            "cc-by-nd",
            "cc-by-nc-sa",
            "cc-by-nc-nd",
            "cc-by-nc-4.0",
            "cc-by-nd-4.0",
            "cc-by-nc-sa-4.0",
            "cc-by-nc-nd-4.0",
            "all-rights-reserved",
            "proprietary",
            "cc-by-5.0",
            "cc-by-sa-4.0-extra",
            "",
            "   ");

    rejected.forEach(
        raw -> assertTrue(License.parse(raw).isEmpty(), "expected " + raw + " to be rejected"));
  }

  /** A missing licence is rejected without an exception. */
  @Test
  void testParse_ReturnsEmpty_WhenNull() {
    assertTrue(License.parse(null).isEmpty());
  }

  /** The canonical value is the lowercase, hyphenated licence stored with the image. */
  @Test
  void testCanonical_ReturnsStoredValue() {
    assertEquals("cc0", License.CC0.canonical());
    assertEquals("public-domain", License.PUBLIC_DOMAIN.canonical());
    assertEquals("cc-by", License.CC_BY.canonical());
    assertEquals("cc-by-sa", License.CC_BY_SA.canonical());
    assertFalse(License.CC_BY.canonical().isEmpty());
  }
}
