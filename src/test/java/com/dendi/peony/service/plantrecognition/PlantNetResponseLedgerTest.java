package com.dendi.peony.service.plantrecognition;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.dendi.peony.api.model.HitResponse;
import com.dendi.peony.api.model.PlantRecognitionResult;
import com.dendi.peony.api.model.Species;
import com.dendi.peony.repository.PlantNetResponseRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for the PlantNet response ledger's hit interpretation (issue #9): which hit is
 * recorded, how the scientific name is normalized, which common name wins, and which inputs are
 * skipped.
 */
@ExtendWith(MockitoExtension.class)
class PlantNetResponseLedgerTest {

  private static final Instant NOW = Instant.parse("2026-06-01T10:00:00Z");

  private static final LocalDateTime NOW_LOCAL = LocalDateTime.ofInstant(NOW, ZoneOffset.UTC);

  @Mock private PlantNetResponseRepository repository;

  /** Only the best (first) hit is recorded; lower-ranked hits are out of scope. */
  @Test
  void testRecord_RecordsTopHit_WhenResultHasSeveralHits() {
    PlantRecognitionResult result =
        result(hit("Ajuga reptans", "Bugle", 0.9), hit("Ficus lyrata", "Fiddle-leaf fig", 0.4));

    ledger().record(result);

    verify(repository)
        .recordResponse(
            eq("ajuga reptans"), eq("Bugle"), eq(BigDecimal.valueOf(0.9)), eq(NOW_LOCAL));
  }

  /** A name with extra whitespace and mixed case is normalized to the ledger key. */
  @Test
  void testRecord_NormalizesScientificName_WhenNameHasExtraWhitespaceAndCase() {
    ledger().record(result(hit("  Ajuga   Reptans  ", null, 0.9)));

    verify(repository)
        .recordResponse(eq("ajuga reptans"), isNull(), eq(BigDecimal.valueOf(0.9)), eq(NOW_LOCAL));
  }

  /** When PlantNet omits the author-free name, the binomial of the full name is used. */
  @Test
  void testRecord_FallsBackToBinomial_WhenAuthorFreeNameMissing() {
    HitResponse hit = hit(null, null, 0.9);
    hit.getSpecies().setScientificName("Ajuga reptans L.");

    ledger().record(result(hit));

    verify(repository)
        .recordResponse(eq("ajuga reptans"), isNull(), eq(BigDecimal.valueOf(0.9)), eq(NOW_LOCAL));
  }

  /**
   * An infraspecific name is keyed by its binomial, so it folds into the same row as the
   * catalogue/draft species name instead of creating a subspecies row.
   */
  @Test
  void testRecord_KeysOnBinomial_WhenNameCarriesInfraspecificRank() {
    HitResponse hit = hit("Caladium bicolor subsp. bicolor", null, 0.9);

    ledger().record(result(hit));

    verify(repository)
        .recordResponse(
            eq("caladium bicolor"), isNull(), eq(BigDecimal.valueOf(0.9)), eq(NOW_LOCAL));
  }

  /** A hit without a derivable binomial is not recorded. */
  @Test
  void testRecord_SkipsRecord_WhenNameCannotBeDerived() {
    ledger().record(result(hit("   ", null, 0.9)));

    verifyNoInteractions(repository);
  }

  /** The first non-blank common name is recorded. */
  @Test
  void testRecord_UsesFirstNonBlankCommonName_WhenCommonNamesContainBlanks() {
    HitResponse hit = hit("Ajuga reptans", null, 0.9);
    hit.getSpecies().setCommonNames(List.of("", "  ", "Bugle", "Common bugle"));

    ledger().record(result(hit));

    verify(repository)
        .recordResponse(
            eq("ajuga reptans"), eq("Bugle"), eq(BigDecimal.valueOf(0.9)), eq(NOW_LOCAL));
  }

  /** A hit with no usable common name is recorded with a null common name. */
  @Test
  void testRecord_RecordsNullCommonName_WhenNoCommonNamePresent() {
    HitResponse hit = hit("Ajuga reptans", null, 0.9);
    hit.getSpecies().setCommonNames(List.of(" ", ""));

    ledger().record(result(hit));

    verify(repository)
        .recordResponse(eq("ajuga reptans"), isNull(), eq(BigDecimal.valueOf(0.9)), eq(NOW_LOCAL));
  }

  /** A non-finite score cannot be stored in the numeric column and is skipped. */
  @Test
  void testRecord_SkipsRecord_WhenScoreIsNotFinite() {
    ledger().record(result(hit("Ajuga reptans", "Bugle", Double.NaN)));

    verifyNoInteractions(repository);
  }

  /** Confidence is defined on 0..1; an out-of-range score is skipped rather than clamped. */
  @Test
  void testRecord_SkipsRecord_WhenScoreOutOfRange() {
    ledger().record(result(hit("Ajuga reptans", "Bugle", 1.5)));

    verifyNoInteractions(repository);
  }

  /** An empty hit list means PlantNet recognized nothing; nothing is recorded. */
  @Test
  void testRecord_SkipsRecord_WhenResultHasNoHits() {
    PlantRecognitionResult result = new PlantRecognitionResult();
    result.setHits(List.of());

    ledger().record(result);

    verifyNoInteractions(repository);
  }

  /** Boundary scores of exactly 0 and 1 are valid confidence values. */
  @Test
  void testRecord_RecordsBoundaryScore_WhenScoreIsZero() {
    ledger().record(result(hit("Ajuga reptans", "Bugle", 0.0)));

    verify(repository)
        .recordResponse(
            eq("ajuga reptans"), eq("Bugle"), eq(BigDecimal.valueOf(0.0)), eq(NOW_LOCAL));
  }

  private PlantNetResponseLedger ledger() {
    return new PlantNetResponseLedger(repository, Clock.fixed(NOW, ZoneOffset.UTC));
  }

  private static PlantRecognitionResult result(HitResponse... hits) {
    PlantRecognitionResult result = new PlantRecognitionResult();
    result.setHits(List.of(hits));
    return result;
  }

  private static HitResponse hit(
      String scientificNameWithoutAuthor, String commonName, double score) {
    HitResponse hit = new HitResponse();
    hit.setScore(score);
    Species species = new Species();
    species.setScientificNameWithoutAuthor(scientificNameWithoutAuthor);
    species.setScientificName(scientificNameWithoutAuthor);
    species.setCommonNames(commonName == null ? List.of() : List.of(commonName));
    hit.setSpecies(species);
    return hit;
  }
}
