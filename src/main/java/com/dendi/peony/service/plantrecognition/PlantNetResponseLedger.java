package com.dendi.peony.service.plantrecognition;

import com.dendi.peony.api.model.HitResponse;
import com.dendi.peony.api.model.PlantRecognitionResult;
import com.dendi.peony.api.model.Species;
import com.dendi.peony.repository.PlantNetResponseRepository;
import com.dendi.peony.service.OptionalText;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records the top hit of each successful PlantNet recognition into the response ledger (issue #9).
 *
 * <p>The ledger is an internal aggregate of taxonomy and confidence, not a response cache: ADR-002
 * still forbids retaining raw responses or photos. Only the best hit is recorded, keyed by the
 * normalized, author-free binomial so names carrying authorship, infraspecific ranks, or
 * inconsistent whitespace fold into the same row.
 */
@Service
public class PlantNetResponseLedger {

  private final PlantNetResponseRepository repository;

  private final Clock clock;

  /**
   * Creates the ledger service.
   *
   * @param repository ledger data access
   * @param clock clock used for the sighting timestamp
   */
  public PlantNetResponseLedger(PlantNetResponseRepository repository, Clock clock) {
    this.repository = repository;
    this.clock = clock;
  }

  /**
   * Records the top hit of a recognition, if it names a species with a valid confidence.
   *
   * <p>A result without hits, without a derivable binomial, or with a score outside 0..1 (including
   * NaN) is a no-op. A database failure propagates as a {@link
   * org.springframework.dao.DataAccessException} so the caller can decide whether the ledger must
   * stay non-fatal.
   *
   * @param result recognition result returned by the PlantNet client
   */
  // The caller treats a ledger failure as non-fatal. A separate transaction keeps such a failure
  // from marking an outer transaction rollback-only if the caller ever becomes transactional.
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void record(PlantRecognitionResult result) {
    if (result == null || result.getHits() == null || result.getHits().isEmpty()) {
      return;
    }
    HitResponse top = result.getHits().get(0);
    if (top == null) {
      return;
    }
    Species species = top.getSpecies();
    String scientificName = ledgerKey(species);
    if (scientificName == null) {
      return;
    }
    BigDecimal confidence = confidenceOf(top.getScore());
    if (confidence == null) {
      return;
    }
    repository.recordResponse(
        scientificName, firstCommonName(species), confidence, LocalDateTime.now(clock));
  }

  /** Reduces the species to the normalized, author-free binomial used as the ledger key. */
  private static String ledgerKey(Species species) {
    if (species == null) {
      return null;
    }
    return ScientificNames.normalize(
        ScientificNames.binomial(
            species.getScientificNameWithoutAuthor(), species.getScientificName()));
  }

  /** Takes the first non-blank common name, or null when the hit supplies none. */
  private static String firstCommonName(Species species) {
    if (species == null) {
      return null;
    }
    List<String> commonNames = species.getCommonNames();
    if (commonNames == null) {
      return null;
    }
    for (String commonName : commonNames) {
      String trimmed = OptionalText.trimToNull(commonName);
      if (trimmed != null) {
        return trimmed;
      }
    }
    return null;
  }

  /** Converts a finite score in 0..1 to a ledger confidence, or null when it is invalid. */
  private static BigDecimal confidenceOf(Double score) {
    if (score == null || !Double.isFinite(score) || score < 0.0 || score > 1.0) {
      return null;
    }
    return BigDecimal.valueOf(score);
  }
}
