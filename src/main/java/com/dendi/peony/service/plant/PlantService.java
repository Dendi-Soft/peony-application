package com.dendi.peony.service.plant;

import com.dendi.peony.api.model.CreatePlantRequest;
import com.dendi.peony.api.model.PlantResponse;
import com.dendi.peony.api.model.UserPlantResponse;
import com.dendi.peony.entity.Plant;
import com.dendi.peony.entity.UserTier;
import com.dendi.peony.exception.PlantNotFoundException;
import com.dendi.peony.repository.PlantRepository;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Plant catalog operations for the v1 plants endpoints (issue #10 Phase 1).
 *
 * <p>Responses honor the tier field visibility from docs/api-tier-contracts.md: free-tier responses
 * omit the premium-only fields (null fields are absent from the JSON through the generated models'
 * NON_NULL serialization), premium-tier responses carry the full plant object.
 */
@Service
@RequiredArgsConstructor
public class PlantService {

  private final PlantRepository plantRepository;
  private final UserPlantService userPlantService;

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
    return plantRepository.searchPlants(query).stream()
        .map(plant -> PlantResponseMapper.toModel(plant, tier))
        .toList();
  }

  /**
   * Manually registers a plant and always creates a new individual for the caller.
   *
   * <p>The catalog is keyed by scientific name: an existing entry is reused so the catalog stays
   * the single source of truth, while each registration still creates its own individual. The
   * returned identity is the individual, not the catalog plant.
   *
   * @param request registration payload
   * @param userId id of the registering account
   * @param tier tier of the registering account (shapes the response)
   * @return the created individual
   * @throws IllegalArgumentException when a required field is missing
   */
  @Transactional
  public UserPlantResponse register(CreatePlantRequest request, Long userId, UserTier tier) {
    RequiredText.requireText(request.getCommonName(), "commonName");
    RequiredText.requireText(request.getScientificName(), "scientificName");
    if (request.getWaterFrequency() == null) {
      throw new IllegalArgumentException("waterFrequency is required");
    }
    Plant plant =
        plantRepository
            .findByScientificName(request.getScientificName())
            .orElseGet(() -> saveCatalogEntry(request));
    return userPlantService.addRegisteredIndividual(plant, userId, tier);
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
    return PlantResponseMapper.toModel(findEntity(plantId), tier);
  }

  /** Persists a new catalog entry from the registration payload. */
  private Plant saveCatalogEntry(CreatePlantRequest request) {
    Plant plant = new Plant();
    plant.setName(request.getCommonName().trim());
    plant.setScientificName(request.getScientificName().trim());
    // description is not part of the API contract, but the column is NOT NULL
    plant.setDescription("");
    plant.setWateringFrequency(request.getWaterFrequency());
    plant.setFamily(request.getFamily());
    plant.setTags(request.getTags() == null ? null : request.getTags().toArray(new String[0]));
    plant.setLightNeeds(request.getLightNeeds());
    plant.setHumidity(request.getHumidity());
    plant.setTempRange(request.getTempRange());
    plant.setToPets(request.getToPets());
    plant.setToHumans(request.getToHumans());
    plant.setDifficulty(request.getDifficulty());
    plant.setNativeRegion(request.getNativeRegion());
    plant.setMatureSize(request.getMatureSize());
    plant.setCreatedAt(LocalDateTime.now());
    return plantRepository.save(plant);
  }

  private Plant findEntity(Long plantId) {
    return plantRepository.findById(plantId).orElseThrow(() -> new PlantNotFoundException(plantId));
  }
}
