package com.dendi.peony.service.plantrecognition;

import com.dendi.peony.api.model.CatalogDraft;
import com.dendi.peony.api.model.HitResponse;
import com.dendi.peony.api.model.PlantRecognitionResult;
import com.dendi.peony.api.model.RecognitionRequest;
import com.dendi.peony.entity.UserTier;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

/**
 * Runs the recognition pipeline for one uploaded image (issues #45 and #52).
 *
 * <p>The service owns the PlantNet identify call, the best-effort staging of the related-image
 * candidates, the response-ledger update, the tier shaping of the result, and the automatic
 * catalogue registration of the top hit. Staging and the ledger happen before shaping because the
 * free-tier shape drops every hit except the top one; the public recognition contract is unchanged.
 * The catalogue write uses the shaped result's draft, which is exactly the draft the client
 * receives for both tiers.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PlantRecognitionService {

  private final PlantNetClient plantNetClient;

  private final PlantNetStagingService plantNetStagingService;

  private final PlantNetResponseLedger plantNetResponseLedger;

  private final ScanCatalogueWriter scanCatalogueWriter;

  private final RecognitionTierShaper recognitionTierShaper;

  /**
   * Recognizes the uploaded image and returns the tier-shaped result.
   *
   * @param request image bytes plus upload options
   * @param tier tier of the calling account (shapes the response)
   * @return the recognition result with hits ordered best match first
   * @throws IOException if the request cannot be completed after all retry attempts
   */
  public PlantRecognitionResult recognize(RecognitionRequest request, UserTier tier)
      throws IOException {
    RecognitionOutcome outcome = plantNetClient.recognizeWithRelatedImages(request);
    stageCandidates(outcome);
    recordResponse(outcome);
    PlantRecognitionResult shaped = recognitionTierShaper.shape(outcome.result(), tier);
    addTopHitToCatalogue(shaped);
    return shaped;
  }

  /**
   * Stages accepted species-image candidates without letting a database problem fail a successful
   * recognition. The quota has already been counted and the response is still useful without a
   * staged fallback.
   */
  private void stageCandidates(RecognitionOutcome outcome) {
    bestEffort(
        "Could not stage the PlantNet species images",
        () -> plantNetStagingService.stage(outcome.relatedImages()));
  }

  /**
   * Records the recognition in the response ledger without letting a database problem fail a
   * successful recognition. The ledger is internal telemetry: the caller's response is useful even
   * when the aggregate row cannot be written.
   */
  private void recordResponse(RecognitionOutcome outcome) {
    bestEffort(
        "Could not record the PlantNet recognition in the response ledger",
        () -> plantNetResponseLedger.record(outcome.result()));
  }

  /**
   * Adds the recognized species to the catalogue without letting a database problem fail a
   * successful recognition. The draft comes from the shaped result, so it is exactly the draft the
   * client receives; no draft (no hits, score below the threshold, or no derivable binomial) means
   * there is no species to add.
   */
  private void addTopHitToCatalogue(PlantRecognitionResult shaped) {
    CatalogDraft draft = topCatalogDraft(shaped);
    if (draft == null) {
      return;
    }
    bestEffort(
        "Could not add the recognized species to the catalogue",
        () -> scanCatalogueWriter.record(draft));
  }

  /** Returns the top hit's catalogue draft, or null when the shaped result carries none. */
  private static CatalogDraft topCatalogDraft(PlantRecognitionResult result) {
    if (result.getHits() == null || result.getHits().isEmpty()) {
      return null;
    }
    HitResponse top = result.getHits().get(0);
    return top == null ? null : top.getCatalogDraft();
  }

  /**
   * Runs a non-essential side effect, logging a database failure instead of failing recognition.
   */
  private void bestEffort(String message, Runnable action) {
    try {
      action.run();
    } catch (DataAccessException exception) {
      log.warn(message, exception);
    }
  }
}
