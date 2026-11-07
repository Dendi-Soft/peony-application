package com.dendi.peony.service.plantrecognition;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.dendi.peony.api.model.CatalogDraft;
import com.dendi.peony.repository.PlantRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for the recognition catalogue writer (issue #52): only a draft with a usable binomial
 * reaches the scan upsert, and the optional fields are trimmed before they are stored.
 */
@ExtendWith(MockitoExtension.class)
class ScanCatalogueWriterTest {

  private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 8, 10, 0);

  private static final Clock CLOCK = Clock.fixed(NOW.toInstant(ZoneOffset.UTC), ZoneOffset.UTC);

  @Mock private PlantRepository plantRepository;

  /** No draft means no catalogue write. */
  @Test
  void testRecord_DoesNothing_WhenDraftIsNull() {
    writer().record(null);

    verifyNoInteractions(plantRepository);
  }

  /** A draft without a usable scientific name cannot key the catalogue row. */
  @Test
  void testRecord_DoesNothing_WhenScientificNameIsBlank() {
    CatalogDraft draft = new CatalogDraft();
    draft.setScientificName("   ");
    draft.setCommonName("Bugle");

    writer().record(draft);

    verifyNoInteractions(plantRepository);
  }

  /** The binomial and optional identity fields are trimmed before the upsert. */
  @Test
  void testRecord_UpsertsTrimmedDraft_WhenScientificNamePresent() {
    CatalogDraft draft = new CatalogDraft();
    draft.setScientificName("  Ajuga reptans ");
    draft.setCommonName("  Bugle ");
    draft.setFamily("  Lamiaceae ");

    writer().record(draft);

    verify(plantRepository).upsertScanCatalogueEntry("Ajuga reptans", "Bugle", "Lamiaceae", NOW);
  }

  /** Blank optional identity fields are stored as unknown, not as empty strings. */
  @Test
  void testRecord_PassesUnknownOptionalFields_WhenDraftFieldsBlank() {
    CatalogDraft draft = new CatalogDraft();
    draft.setScientificName("Ajuga reptans");
    draft.setCommonName("   ");
    draft.setFamily(null);

    writer().record(draft);

    verify(plantRepository).upsertScanCatalogueEntry("Ajuga reptans", null, null, NOW);
  }

  private ScanCatalogueWriter writer() {
    return new ScanCatalogueWriter(plantRepository, CLOCK);
  }
}
