package com.dendi.peony.service.plantrecognition;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.dendi.peony.api.model.HitResponse;
import com.dendi.peony.api.model.PlantRecognitionResult;
import com.dendi.peony.api.model.RecognitionRequest;
import com.dendi.peony.api.model.Species;
import com.dendi.peony.entity.UserTier;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;

/**
 * Unit tests for the recognition service's ledger hook (issue #9): the ledger runs after a
 * successful identify and staging, but never changes the response or fails the recognition when the
 * database is unavailable.
 */
@ExtendWith(MockitoExtension.class)
class PlantRecognitionServiceTest {

  @Mock private PlantNetClient plantNetClient;

  @Mock private PlantNetStagingService plantNetStagingService;

  @Mock private PlantNetResponseLedger plantNetResponseLedger;

  /** A successful recognition records its result in the ledger. */
  @Test
  void testRecognize_RecordsResponse_WhenPlantNetSucceeds() throws Exception {
    PlantRecognitionResult result = result();
    when(plantNetClient.recognizeWithRelatedImages(any(RecognitionRequest.class)))
        .thenReturn(new RecognitionOutcome(result, List.of()));

    PlantRecognitionResult shaped = service().recognize(new RecognitionRequest(), UserTier.PREMIUM);

    assertSame(result, shaped);
    verify(plantNetResponseLedger).record(result);
  }

  /** A ledger write failure is logged and swallowed; the recognition response is still returned. */
  @Test
  void testRecognize_ReturnsShapedResult_WhenLedgerFails() throws Exception {
    PlantRecognitionResult result = result();
    when(plantNetClient.recognizeWithRelatedImages(any(RecognitionRequest.class)))
        .thenReturn(new RecognitionOutcome(result, List.of()));
    doThrow(new DataAccessResourceFailureException("ledger unavailable"))
        .when(plantNetResponseLedger)
        .record(any());

    PlantRecognitionResult shaped = service().recognize(new RecognitionRequest(), UserTier.PREMIUM);

    assertSame(result, shaped);
  }

  /** A staging failure is also non-fatal, and must not skip the ledger. */
  @Test
  void testRecognize_RecordsResponse_WhenStagingFails() throws Exception {
    PlantRecognitionResult result = result();
    when(plantNetClient.recognizeWithRelatedImages(any(RecognitionRequest.class)))
        .thenReturn(new RecognitionOutcome(result, List.of()));
    doThrow(new DataAccessResourceFailureException("staging unavailable"))
        .when(plantNetStagingService)
        .stage(any());

    service().recognize(new RecognitionRequest(), UserTier.PREMIUM);

    verify(plantNetResponseLedger).record(result);
  }

  /** A failed identify has no result to record, and the ledger must not be touched. */
  @Test
  void testRecognize_DoesNotRecord_WhenPlantNetClientThrows() throws Exception {
    when(plantNetClient.recognizeWithRelatedImages(any(RecognitionRequest.class)))
        .thenThrow(new IOException("api down"));

    assertThrows(
        IOException.class, () -> service().recognize(new RecognitionRequest(), UserTier.PREMIUM));

    verifyNoInteractions(plantNetResponseLedger);
  }

  private PlantRecognitionService service() {
    return new PlantRecognitionService(
        plantNetClient,
        plantNetStagingService,
        plantNetResponseLedger,
        new RecognitionTierShaper());
  }

  private static PlantRecognitionResult result() {
    HitResponse hit = new HitResponse();
    hit.setScore(0.9);
    Species species = new Species();
    species.setScientificName("Ajuga reptans L.");
    species.setScientificNameWithoutAuthor("Ajuga reptans");
    species.setCommonNames(List.of("Bugle"));
    hit.setSpecies(species);
    PlantRecognitionResult result = new PlantRecognitionResult();
    result.setApiVersion("2025-01-17");
    result.setHits(List.of(hit));
    return result;
  }
}
