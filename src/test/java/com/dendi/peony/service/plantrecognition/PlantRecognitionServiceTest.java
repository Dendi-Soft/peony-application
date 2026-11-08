package com.dendi.peony.service.plantrecognition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.dendi.peony.api.model.CatalogDraft;
import com.dendi.peony.api.model.HitResponse;
import com.dendi.peony.api.model.PlantRecognitionResult;
import com.dendi.peony.api.model.RecognitionRequest;
import com.dendi.peony.api.model.Species;
import com.dendi.peony.entity.UserTier;
import com.dendi.peony.service.plant.SpeciesImageService;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;

/**
 * Unit tests for the recognition service's best-effort side effects (issues #9 and #52): the ledger
 * and the catalogue writer run after a successful identify and shaping, but never change the
 * response or fail the recognition when the database is unavailable.
 */
@ExtendWith(MockitoExtension.class)
class PlantRecognitionServiceTest {

  @Mock private PlantNetClient plantNetClient;

  @Mock private PlantNetStagingService plantNetStagingService;

  @Mock private PlantNetResponseLedger plantNetResponseLedger;

  @Mock private ScanCatalogueWriter scanCatalogueWriter;

  @Mock private SpeciesImageService speciesImageService;

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
    verifyNoInteractions(scanCatalogueWriter);
  }

  /** A premium recognition adds the top hit's draft to the catalogue. */
  @Test
  void testRecognize_RecordsTopDraftInCatalogue_WhenPremiumTier() throws Exception {
    when(plantNetClient.recognizeWithRelatedImages(any(RecognitionRequest.class)))
        .thenReturn(new RecognitionOutcome(result(0.9, "Ajuga reptans"), List.of()));

    service().recognize(new RecognitionRequest(), UserTier.PREMIUM);

    ArgumentCaptor<CatalogDraft> draft = ArgumentCaptor.forClass(CatalogDraft.class);
    verify(scanCatalogueWriter).record(draft.capture());
    assertEquals("Ajuga reptans", draft.getValue().getScientificName());
    assertEquals("Bugle", draft.getValue().getCommonName());
  }

  /** The free-tier shape keeps the top hit's draft, so it is also added to the catalogue. */
  @Test
  void testRecognize_RecordsTopDraftInCatalogue_WhenFreeTier() throws Exception {
    when(plantNetClient.recognizeWithRelatedImages(any(RecognitionRequest.class)))
        .thenReturn(new RecognitionOutcome(result(0.9, "Ajuga reptans"), List.of()));

    service().recognize(new RecognitionRequest(), UserTier.FREE);

    ArgumentCaptor<CatalogDraft> draft = ArgumentCaptor.forClass(CatalogDraft.class);
    verify(scanCatalogueWriter).record(draft.capture());
    assertEquals("Ajuga reptans", draft.getValue().getScientificName());
    assertEquals("Bugle", draft.getValue().getCommonName());
  }

  /** A top hit below the draft threshold has no species to add to the catalogue. */
  @Test
  void testRecognize_DoesNotRecordCatalogueDraft_WhenTopHitBelowThreshold() throws Exception {
    when(plantNetClient.recognizeWithRelatedImages(any(RecognitionRequest.class)))
        .thenReturn(new RecognitionOutcome(result(0.29, "Ajuga reptans"), List.of()));

    service().recognize(new RecognitionRequest(), UserTier.PREMIUM);

    verifyNoInteractions(scanCatalogueWriter);
    verifyNoInteractions(speciesImageService);
  }

  /** A name that cannot be reduced to a binomial yields no draft and no catalogue write. */
  @Test
  void testRecognize_DoesNotRecordCatalogueDraft_WhenNoBinomialDerivable() throws Exception {
    when(plantNetClient.recognizeWithRelatedImages(any(RecognitionRequest.class)))
        .thenReturn(new RecognitionOutcome(result(0.9, "Caladium"), List.of()));

    service().recognize(new RecognitionRequest(), UserTier.PREMIUM);

    verifyNoInteractions(scanCatalogueWriter);
    verifyNoInteractions(speciesImageService);
  }

  /** A recognition without hits has no draft and writes nothing to the catalogue. */
  @Test
  void testRecognize_DoesNotRecordCatalogueDraft_WhenResultHasNoHits() throws Exception {
    PlantRecognitionResult result = new PlantRecognitionResult();
    result.setHits(List.of());
    when(plantNetClient.recognizeWithRelatedImages(any(RecognitionRequest.class)))
        .thenReturn(new RecognitionOutcome(result, List.of()));

    service().recognize(new RecognitionRequest(), UserTier.PREMIUM);

    verifyNoInteractions(scanCatalogueWriter);
    verifyNoInteractions(speciesImageService);
  }

  /**
   * A catalogue write failure is logged and swallowed; the recognition response is still returned.
   */
  @Test
  void testRecognize_ReturnsShapedResult_WhenCatalogueWriteFails() throws Exception {
    PlantRecognitionResult result = result(0.9, "Ajuga reptans");
    when(plantNetClient.recognizeWithRelatedImages(any(RecognitionRequest.class)))
        .thenReturn(new RecognitionOutcome(result, List.of()));
    doThrow(new DataAccessResourceFailureException("catalogue unavailable"))
        .when(scanCatalogueWriter)
        .record(any());

    PlantRecognitionResult shaped = service().recognize(new RecognitionRequest(), UserTier.PREMIUM);

    assertSame(result, shaped);
    verify(scanCatalogueWriter).record(any(CatalogDraft.class));
    verifyNoInteractions(speciesImageService);
  }

  /**
   * Once the catalogue row is committed, its image is filled so viewing care details straight after
   * a scan shows the PlantNet fallback without waiting for the user to add the plant.
   */
  @Test
  void testRecognize_FillsCatalogueImage_WhenTopDraftIsRecorded() throws Exception {
    when(plantNetClient.recognizeWithRelatedImages(any(RecognitionRequest.class)))
        .thenReturn(new RecognitionOutcome(result(0.9, "Ajuga reptans"), List.of()));
    when(scanCatalogueWriter.record(any(CatalogDraft.class))).thenReturn(Optional.of(42L));

    service().recognize(new RecognitionRequest(), UserTier.PREMIUM);

    verify(speciesImageService).fillIfMissing(42L);
  }

  /** No catalogue row means nothing to fill, so the image service is untouched. */
  @Test
  void testRecognize_DoesNotFillCatalogueImage_WhenWriterReturnsNoPlant() throws Exception {
    when(plantNetClient.recognizeWithRelatedImages(any(RecognitionRequest.class)))
        .thenReturn(new RecognitionOutcome(result(0.9, "Ajuga reptans"), List.of()));
    when(scanCatalogueWriter.record(any(CatalogDraft.class))).thenReturn(Optional.empty());

    service().recognize(new RecognitionRequest(), UserTier.PREMIUM);

    verify(speciesImageService, never()).fillIfMissing(any());
  }

  private PlantRecognitionService service() {
    return new PlantRecognitionService(
        plantNetClient,
        plantNetStagingService,
        plantNetResponseLedger,
        scanCatalogueWriter,
        new RecognitionTierShaper(),
        speciesImageService);
  }

  private static PlantRecognitionResult result() {
    return result(0.9, "Ajuga reptans");
  }

  private static PlantRecognitionResult result(double score, String scientificNameWithoutAuthor) {
    HitResponse hit = new HitResponse();
    hit.setScore(score);
    Species species = new Species();
    species.setScientificName(scientificNameWithoutAuthor + " L.");
    species.setScientificNameWithoutAuthor(scientificNameWithoutAuthor);
    species.setCommonNames(List.of("Bugle"));
    hit.setSpecies(species);
    PlantRecognitionResult result = new PlantRecognitionResult();
    result.setApiVersion("2025-01-17");
    result.setHits(List.of(hit));
    return result;
  }
}
