package com.dendi.peony.service.plant;

import com.dendi.peony.api.model.CreatePlantFromScanRequest;
import com.dendi.peony.api.model.UserPlantResponse;
import com.dendi.peony.entity.UserTier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Orchestrates scan-based registration and the best-effort species-image fill (issue #45).
 *
 * <p>The catalogue and individual are written in {@link PlantService}'s transaction, which commits
 * before this method asks {@link SpeciesImageService} to fill an image-less catalogue entry. That
 * split matters: the writer's separate transaction can see the committed plant row, and the scan
 * never holds a transaction open across a network download. When the fill succeeds, the individual
 * is re-read so the response carries the new image credit; any fill failure leaves the created
 * individual untouched.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PlantScanService {

  private final PlantService plantService;

  private final UserPlantService userPlantService;

  private final SpeciesImageService speciesImageService;

  /**
   * Registers a scan result and fills a missing catalogue image when possible.
   *
   * @param request scan registration payload
   * @param userId id of the registering account
   * @param tier tier of the registering account (shapes the response)
   * @return the created individual, re-read when an image was filled
   */
  public UserPlantResponse registerFromScan(
      CreatePlantFromScanRequest request, Long userId, UserTier tier) {
    UserPlantResponse created = plantService.registerFromScan(request, userId, tier);
    try {
      if (speciesImageService.fillIfMissing(created.getPlantId())) {
        return userPlantService.get(created.getId(), userId, tier);
      }
    } catch (RuntimeException exception) {
      log.warn(
          "Species-image fill failed after registering plant {}; returning the scan result",
          created.getPlantId(),
          exception);
    }
    return created;
  }
}
