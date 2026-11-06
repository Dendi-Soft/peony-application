package com.dendi.peony.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dendi.peony.TestcontainersConfiguration;
import com.dendi.peony.entity.PlantNetResponse;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

/**
 * Integration tests for the PlantNet response ledger store (issue #9) against a real PostgreSQL
 * database: the single-statement upsert maintains the response count and running average, keeps the
 * first timestamp, advances the last timestamp, and fills only a blank common name.
 *
 * <p>Each test runs in a rolled-back transaction.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class PlantNetResponseRepositoryTest {

  private static final LocalDateTime FIRST_SEEN = LocalDateTime.of(2026, 6, 1, 10, 0);

  private static final LocalDateTime LAST_SEEN = FIRST_SEEN.plusHours(1);

  @Autowired private PlantNetResponseRepository repository;

  /** A species seen for the first time starts at one response with its own confidence. */
  @Test
  void testRecordResponse_InsertsOneRow_WhenNameNotSeenBefore() {
    int updated =
        repository.recordResponse("ajuga reptans", "Bugle", new BigDecimal("0.80000"), FIRST_SEEN);

    assertEquals(1, updated);
    PlantNetResponse row = row("ajuga reptans");
    assertEquals("ajuga reptans", row.getScientificName());
    assertEquals("Bugle", row.getCommonName());
    assertEquals(1L, row.getResponseCount());
    assertDecimalEquals("0.80000", row.getAvgConfidence());
    assertEquals(FIRST_SEEN, row.getFirstSeen());
    assertEquals(FIRST_SEEN, row.getLastSeen());
  }

  /** A second sighting increments the count and recomputes the confidence as an arithmetic mean. */
  @Test
  void testRecordResponse_IncrementsCountAndAveragesConfidence_WhenNameSeenAgain() {
    repository.recordResponse("ajuga reptans", "Bugle", new BigDecimal("0.80000"), FIRST_SEEN);

    repository.recordResponse("ajuga reptans", "Bugle", new BigDecimal("0.60000"), LAST_SEEN);

    PlantNetResponse row = row("ajuga reptans");
    assertEquals(2L, row.getResponseCount());
    assertDecimalEquals("0.70000", row.getAvgConfidence());
  }

  /** The first sighting timestamp is immutable; the last sighting timestamp advances. */
  @Test
  void testRecordResponse_KeepsFirstSeenAndAdvancesLastSeen_WhenNameSeenAgain() {
    repository.recordResponse("ajuga reptans", "Bugle", new BigDecimal("0.80000"), FIRST_SEEN);

    repository.recordResponse("ajuga reptans", "Bugle", new BigDecimal("0.60000"), LAST_SEEN);

    PlantNetResponse row = row("ajuga reptans");
    assertEquals(FIRST_SEEN, row.getFirstSeen());
    assertEquals(LAST_SEEN, row.getLastSeen());
  }

  /** An unknown common name from an earlier sighting is filled in when a later hit supplies one. */
  @Test
  void testRecordResponse_FillsBlankCommonName_WhenExistingNameBlank() {
    repository.recordResponse("ajuga reptans", "   ", new BigDecimal("0.80000"), FIRST_SEEN);

    repository.recordResponse("ajuga reptans", "Bugle", new BigDecimal("0.60000"), LAST_SEEN);

    assertEquals("Bugle", row("ajuga reptans").getCommonName());
  }

  /** A known common name is never replaced by a later, different common name. */
  @Test
  void testRecordResponse_PreservesNonBlankCommonName_WhenExistingNamePresent() {
    repository.recordResponse("ajuga reptans", "Bugle", new BigDecimal("0.80000"), FIRST_SEEN);

    repository.recordResponse(
        "ajuga reptans", "Common bugle", new BigDecimal("0.60000"), LAST_SEEN);

    assertEquals("Bugle", row("ajuga reptans").getCommonName());
  }

  /** Different normalized names are independent ledger rows. */
  @Test
  void testRecordResponse_KeepsRowsSeparate_WhenNamesDiffer() {
    repository.recordResponse("ajuga reptans", "Bugle", new BigDecimal("0.80000"), FIRST_SEEN);
    repository.recordResponse(
        "ficus lyrata", "Fiddle-leaf fig", new BigDecimal("0.90000"), FIRST_SEEN);

    assertEquals(2, repository.count());
    assertEquals(1L, row("ficus lyrata").getResponseCount());
  }

  private PlantNetResponse row(String scientificName) {
    return repository
        .findById(scientificName)
        .orElseThrow(() -> new AssertionError("expected ledger row for " + scientificName));
  }

  private static void assertDecimalEquals(String expected, BigDecimal actual) {
    assertTrue(
        new BigDecimal(expected).compareTo(actual) == 0,
        "expected " + expected + " but was " + actual);
  }
}
