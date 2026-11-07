package com.dendi.peony.service.plantrecognition;

import com.dendi.peony.api.model.CatalogDraft;
import com.dendi.peony.repository.PlantRepository;
import com.dendi.peony.service.OptionalText;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Adds the recognised species to the catalogue without user interaction (issue #52).
 *
 * <p>Species enter the catalogue only through a successful scan. This writer upserts the top
 * recognition hit's catalogue draft by its binomial: a new row records identity and taxonomy only
 * with {@code source = PLANTNET_SCAN}, while an existing row keeps its care fields and source and
 * only has blank common-name/family values filled. The write is a non-essential side effect, so a
 * database failure propagates as a {@link DataAccessException} and the caller decides whether it
 * must stay non-fatal.
 */
@Service
@RequiredArgsConstructor
public class ScanCatalogueWriter {

  private final PlantRepository plantRepository;

  private final Clock clock;

  /**
   * Upserts the draft's species into the catalogue.
   *
   * <p>A null draft or a draft without a scientific name is a no-op because the binomial is the
   * catalogue key. Blank common name and family are treated as unknown.
   *
   * @param draft catalogue fields of the top recognition hit
   */
  // Issue #52 requires this write to run in a separate REQUIRES_NEW transaction (mirroring
  // PlantNetResponseLedger), isolating a catalogue failure from the recognition transaction.
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void record(CatalogDraft draft) {
    if (draft == null) {
      return;
    }
    String scientificName = OptionalText.trimToNull(draft.getScientificName());
    if (scientificName == null) {
      return;
    }
    plantRepository.upsertScanCatalogueEntry(
        scientificName,
        OptionalText.trimToNull(draft.getCommonName()),
        OptionalText.trimToNull(draft.getFamily()),
        LocalDateTime.now(clock));
  }
}
