package com.dendi.peony.service.plantrecognition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dendi.peony.TestcontainersConfiguration;
import com.dendi.peony.config.PlantNetStagingProperties;
import com.dendi.peony.entity.PlantNetSpeciesImage;
import com.dendi.peony.repository.PlantNetSpeciesImageRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

/**
 * Integration tests for the PlantNet species-image staging store (issue #45), against a real
 * PostgreSQL database: the upsert keeping one fresh candidate per normalized binomial, the TTL
 * prune driven by an injected clock, the empty-scan no-op, and lookup by name then binomial.
 *
 * <p>Each test runs in a rolled-back transaction. The service is constructed with a fixed clock so
 * expiry is deterministic without touching the shared application clock.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class PlantNetStagingServiceTest {

  private static final Instant NOW = Instant.parse("2026-06-01T10:00:00Z");

  private static final Duration TTL = Duration.ofDays(7);

  @Autowired private PlantNetSpeciesImageRepository repository;

  /** A staged candidate keeps every field the download pipeline needs. */
  @Test
  void testStage_StoresCandidate_WhenSpeciesNotStaged() {
    service(NOW)
        .stage(
            List.of(
                candidate(
                    "ajuga reptans",
                    "https://bs.plantnet.org/image/m/one",
                    "habit",
                    "Ada Lovelace",
                    "cc-by-sa",
                    "Ajuga reptans by Ada Lovelace")));

    PlantNetSpeciesImage staged = staged("ajuga reptans");

    assertEquals("https://bs.plantnet.org/image/m/one", staged.getImageUrl());
    assertEquals("habit", staged.getOrgan());
    assertEquals("Ada Lovelace", staged.getAuthor());
    assertEquals("cc-by-sa", staged.getLicense());
    assertEquals("Ajuga reptans by Ada Lovelace", staged.getCitation());
    assertEquals(at(NOW), staged.getStagedAt(), "the injected clock must stamp the row");
  }

  /** A later recognition of the same species overwrites rather than adds a second row. */
  @Test
  void testStage_OverwritesCandidate_WhenSpeciesStagedAgain() {
    service(NOW).stage(List.of(candidate("ajuga reptans", "https://bs.plantnet.org/m/one")));
    service(NOW.plus(Duration.ofHours(1)))
        .stage(
            List.of(
                candidate(
                    "ajuga reptans",
                    "https://bs.plantnet.org/m/two",
                    "leaf",
                    "Grace",
                    "cc0",
                    null)));

    assertEquals(
        1,
        repository.findAll().stream()
            .filter(row -> "ajuga reptans".equals(row.getScientificName()))
            .count(),
        "a species must hold one staged candidate");
    PlantNetSpeciesImage staged = staged("ajuga reptans");
    assertEquals("https://bs.plantnet.org/m/two", staged.getImageUrl());
    assertEquals("leaf", staged.getOrgan());
    assertEquals("Grace", staged.getAuthor());
    assertEquals("cc0", staged.getLicense());
    assertEquals(at(NOW.plus(Duration.ofHours(1))), staged.getStagedAt());
  }

  /** A scan without a candidate must not disturb a previously staged one. */
  @Test
  void testStage_KeepsExistingCandidate_WhenCandidateListIsEmpty() {
    service(NOW).stage(List.of(candidate("ajuga reptans", "https://bs.plantnet.org/m/one")));

    service(NOW.plus(Duration.ofDays(1))).stage(List.of());

    assertEquals("https://bs.plantnet.org/m/one", staged("ajuga reptans").getImageUrl());
  }

  /** A candidate past the TTL is ignored, pruned, and reported as absent. */
  @Test
  void testFindFreshForName_IgnoresExpiredCandidate_WhenTtlPassed() {
    service(NOW).stage(List.of(candidate("ajuga reptans", "https://bs.plantnet.org/m/one")));

    Optional<PlantNetSpeciesImage> fresh =
        service(NOW.plus(Duration.ofDays(8))).findFreshForName("Ajuga reptans");

    assertTrue(fresh.isEmpty(), "a candidate older than the TTL is not fresh");
    assertTrue(
        repository.findByScientificNameAndStagedAtAfter("ajuga reptans", before(NOW)).isEmpty(),
        "the expired row must be pruned");
  }

  /** Lookup matches case and whitespace because the staging key is normalized. */
  @Test
  void testFindFreshForName_MatchesNormalizedName_WhenInputDiffersInCaseAndSpacing() {
    service(NOW).stage(List.of(candidate("ajuga reptans", "https://bs.plantnet.org/m/one")));

    Optional<PlantNetSpeciesImage> fresh =
        service(NOW.plus(Duration.ofDays(1))).findFreshForName("  AJUGA   reptans  ");

    assertTrue(fresh.isPresent(), "normalized names must match the staging key");
  }

  /** A name carrying authorship falls back to its binomial. */
  @Test
  void testFindFreshForName_FallsBackToBinomial_WhenNameHasAuthorship() {
    service(NOW).stage(List.of(candidate("ajuga reptans", "https://bs.plantnet.org/m/one")));

    Optional<PlantNetSpeciesImage> fresh =
        service(NOW.plus(Duration.ofDays(1))).findFreshForName("Ajuga reptans L.");

    assertTrue(fresh.isPresent(), "the binomial of an authored name must match");
  }

  /** Pruning removes only rows older than the TTL. */
  @Test
  void testPruneExpired_DeletesOnlyExpiredRows() {
    service(NOW).stage(List.of(candidate("ajuga reptans", "https://bs.plantnet.org/m/old")));
    service(NOW.plus(Duration.ofDays(2)))
        .stage(List.of(candidate("ficus lyrata", "https://bs.plantnet.org/m/young")));

    long removed = service(NOW.plus(Duration.ofDays(8))).pruneExpired();

    assertEquals(1, removed, "only the row past the TTL is removed");
    assertTrue(
        repository.findByScientificNameAndStagedAtAfter("ajuga reptans", before(NOW)).isEmpty(),
        "the old row must be gone");
    assertTrue(
        repository
            .findByScientificNameAndStagedAtAfter(
                "ficus lyrata", before(NOW.plus(Duration.ofDays(2))))
            .isPresent(),
        "a fresh row must survive pruning");
  }

  /** Consuming a candidate removes exactly that species' staging row. */
  @Test
  void testConsume_DeletesStagedRow_WhenImagePersisted() {
    service(NOW).stage(List.of(candidate("ajuga reptans", "https://bs.plantnet.org/m/one")));
    PlantNetSpeciesImage staged = staged("ajuga reptans");

    service(NOW).consume(staged);

    assertEquals(
        Optional.empty(),
        repository.findById(staged.getId()),
        "the consumed candidate must be gone");
  }

  private PlantNetStagingService service(Instant now) {
    return new PlantNetStagingService(
        repository, new PlantNetStagingProperties(TTL), Clock.fixed(now, ZoneOffset.UTC));
  }

  private PlantNetSpeciesImage staged(String scientificName) {
    return repository
        .findByScientificNameAndStagedAtAfter(scientificName, LocalDateTime.of(2000, 1, 1, 0, 0))
        .orElseThrow(() -> new AssertionError("expected a staged candidate for " + scientificName));
  }

  private static LocalDateTime at(Instant instant) {
    return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
  }

  private static LocalDateTime before(Instant instant) {
    return at(instant).minusSeconds(1);
  }

  private static RelatedImage candidate(String scientificName, String mediumUrl) {
    return candidate(scientificName, mediumUrl, "habit", null, "cc-by", null);
  }

  private static RelatedImage candidate(
      String scientificName,
      String mediumUrl,
      String organ,
      String author,
      String license,
      String citation) {
    return new RelatedImage(scientificName, organ, author, license, citation, mediumUrl);
  }
}
