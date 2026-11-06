package com.dendi.peony.service.plantrecognition;

import com.dendi.peony.api.model.PlantRecognitionResult;
import com.dendi.peony.api.model.RecognitionRequest;
import com.dendi.peony.entity.UserTier;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

/**
 * Runs the recognition pipeline for one uploaded image (issue #45).
 *
 * <p>The service owns the PlantNet identify call, the best-effort staging of the related-image
 * candidates, the response-ledger update, and the tier shaping of the result. Staging and the
 * ledger happen before shaping because the free-tier shape drops every hit except the top one; the
 * public recognition contract is unchanged.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PlantRecognitionService {

  private final PlantNetClient plantNetClient;

  private final PlantNetStagingService plantNetStagingService;

  private final PlantNetResponseLedger plantNetResponseLedger;

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
    return recognitionTierShaper.shape(outcome.result(), tier);
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
