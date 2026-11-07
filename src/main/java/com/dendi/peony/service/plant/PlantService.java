package com.dendi.peony.service.plant;

import com.dendi.peony.api.model.CreatePlantFromScanRequest;
import com.dendi.peony.api.model.PlantResponse;
import com.dendi.peony.api.model.UserPlantResponse;
import com.dendi.peony.entity.Plant;
import com.dendi.peony.entity.PlantImage;
import com.dendi.peony.entity.UserTier;
import com.dendi.peony.exception.PlantNotFoundException;
import com.dendi.peony.exception.RoomNotFoundException;
import com.dendi.peony.repository.PlantImageRepository;
import com.dendi.peony.repository.PlantRepository;
import com.dendi.peony.service.OptionalText;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Plant catalog operations for the v1 plants endpoints (issues #10 Phase 1 and #52).
 *
 * <p>Responses honor the tier field visibility from docs/api-tier-contracts.md: free-tier responses
 * omit the premium-only fields (null fields are absent from the JSON through the generated models'
 * NON_NULL serialization), premium-tier responses carry the full plant object. Species enter the
 * catalogue only through recognition (#52); scan registration always creates one individual for the
 * caller.
 */
@Service
@RequiredArgsConstructor
public class PlantService {

  private final PlantRepository plantRepository;
  private final PlantImageRepository plantImageRepository;
  private final UserPlantService userPlantService;
  private final Clock clock;

  /**
   * Typahead search over names, scientific names, and tags.
   *
   * @param q query text; blank or null returns the whole catalog
   * @param tier tier of the calling account
   * @return tier-filtered matches ordered by name
   */
  @Transactional(readOnly = true)
  public List<PlantResponse> search(String q, UserTier tier) {
    String query = q == null || q.isBlank() ? null : q.trim();
    List<Plant> plants = plantRepository.searchPlants(query);
    Map<Long, List<PlantImage>> imagesByPlant = imagesByPlant(plants);
    return plants.stream()
        .map(
            plant ->
                PlantResponseMapper.toModel(
                    plant, tier, imagesByPlant.getOrDefault(plant.getId(), List.of())))
        .toList();
  }

  /**
   * Registers a plant identified by a scan and always creates a new individual for the caller.
   *
   * <p>The catalog entry is upserted by scientific name in one statement, so concurrent scans of
   * the same species cannot surface a unique-constraint failure. A newly created row records
   * identity and taxonomy only with {@code source = PLANTNET_SCAN}; an existing row keeps its care
   * fields and source, with only blank common-name/family values filled. The catalog entry and the
   * individual are written in one transaction.
   *
   * @param request scan registration payload
   * @param userId id of the registering account
   * @param tier tier of the registering account (shapes the response)
   * @return the created individual
   * @throws IllegalArgumentException when scientificName is missing/blank, the nickname is too
   *     long, or lastWateredAt is malformed
   * @throws RoomNotFoundException when the room is missing or outside the caller's houses
   */
  @Transactional
  public UserPlantResponse registerFromScan(
      CreatePlantFromScanRequest request, Long userId, UserTier tier) {
    RequiredText.requireText(request.getScientificName(), "scientificName");
    String scientificName = request.getScientificName().trim();
    plantRepository.upsertScanCatalogueEntry(
        scientificName,
        OptionalText.trimToNull(request.getCommonName()),
        OptionalText.trimToNull(request.getFamily()),
        LocalDateTime.now(clock));
    Plant plant =
        plantRepository
            .findByScientificName(scientificName)
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Scan catalogue upsert produced no plant for " + scientificName));
    return userPlantService.createIndividual(
        plant,
        userId,
        tier,
        request.getNickname(),
        request.getRoomId(),
        request.getLastWateredAt());
  }

  /**
   * Loads a plant for the detail endpoint.
   *
   * @param plantId catalog plant id
   * @param tier tier of the calling account
   * @return the tier-filtered plant
   * @throws PlantNotFoundException when no plant exists with the id
   */
  @Transactional(readOnly = true)
  public PlantResponse get(Long plantId, UserTier tier) {
    Plant plant = findEntity(plantId);
    return PlantResponseMapper.toModel(
        plant, tier, plantImageRepository.findByPlantIdOrderByPositionAsc(plantId));
  }

  /** Batch-loads the images of the search results, grouped by plant id in gallery order. */
  private Map<Long, List<PlantImage>> imagesByPlant(List<Plant> plants) {
    if (plants.isEmpty()) {
      return Map.of();
    }
    List<Long> plantIds = plants.stream().map(Plant::getId).toList();
    return plantImageRepository.findByPlantIdInOrderByPlantIdAscPositionAsc(plantIds).stream()
        .collect(Collectors.groupingBy(PlantImage::getPlantId));
  }

  private Plant findEntity(Long plantId) {
    return plantRepository.findById(plantId).orElseThrow(() -> new PlantNotFoundException(plantId));
  }
}
