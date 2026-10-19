package com.dendi.peony.service.plant;

import com.dendi.peony.api.model.CreatePlantRequest;
import com.dendi.peony.api.model.Plant;
import com.dendi.peony.api.model.PlantNotes;
import com.dendi.peony.entity.UserPlant;
import com.dendi.peony.entity.UserTier;
import com.dendi.peony.exception.PlantNotFoundException;
import com.dendi.peony.repository.PlantRepository;
import com.dendi.peony.repository.UserPlantRepository;
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

  /** Maximum note length; user_plants.nickname is VARCHAR(255). */
  static final int NOTES_MAX_LENGTH = 255;

  private final PlantRepository plantRepository;
  private final UserPlantRepository userPlantRepository;

  /**
   * Typahead search over names, scientific names, and tags.
   *
   * @param q query text; blank or null returns the whole catalog
   * @param tier tier of the calling account
   * @return tier-filtered matches ordered by name
   */
  @Transactional(readOnly = true)
  public List<Plant> search(String q, UserTier tier) {
    String query = q == null || q.isBlank() ? null : q.trim();
    return plantRepository.searchPlants(query).stream().map(plant -> toModel(plant, tier)).toList();
  }

  /**
   * Manually registers a plant.
   *
   * <p>The catalog is keyed by scientific name: an existing entry is reused so the catalog stays
   * the single source of truth, and the registering user is always linked to the plant in
   * user_plants.
   *
   * @param request registration payload
   * @param userId id of the registering account
   * @param tier tier of the registering account (shapes the response)
   * @return the tier-filtered plant
   * @throws IllegalArgumentException when a required field is missing
   */
  @Transactional
  public Plant register(CreatePlantRequest request, Long userId, UserTier tier) {
    requireText(request.getCommonName(), "commonName");
    requireText(request.getScientificName(), "scientificName");
    if (request.getWaterFrequency() == null) {
      throw new IllegalArgumentException("waterFrequency is required");
    }
    com.dendi.peony.entity.Plant plant =
        plantRepository
            .findByScientificName(request.getScientificName())
            .orElseGet(() -> saveCatalogEntry(request));
    linkToUser(plant, userId);
    return toModel(plant, tier);
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
  public Plant get(Long plantId, UserTier tier) {
    return toModel(findEntity(plantId), tier);
  }

  /**
   * Updates the calling user's note for a plant (user_plants.nickname).
   *
   * <p>The per-user record is created on demand so notes also work for catalog plants the user has
   * not registered.
   *
   * @param plantId catalog plant id
   * @param userId id of the calling account
   * @param notes note text
   * @return the stored note
   * @throws PlantNotFoundException when no plant exists with the id
   * @throws IllegalArgumentException when the note is missing or too long
   */
  @Transactional
  public PlantNotes updateNotes(Long plantId, Long userId, String notes) {
    if (notes == null) {
      throw new IllegalArgumentException("notes is required");
    }
    if (notes.length() > NOTES_MAX_LENGTH) {
      throw new IllegalArgumentException(
          "Note must be at most " + NOTES_MAX_LENGTH + " characters");
    }
    com.dendi.peony.entity.Plant plant = findEntity(plantId);
    UserPlant link = linkToUser(plant, userId);
    link.setNickname(notes);
    link.setUpdatedAt(LocalDateTime.now());
    userPlantRepository.save(link);
    PlantNotes stored = new PlantNotes();
    stored.setNotes(notes);
    return stored;
  }

  /** Persists a new catalog entry from the registration payload. */
  private com.dendi.peony.entity.Plant saveCatalogEntry(CreatePlantRequest request) {
    com.dendi.peony.entity.Plant plant = new com.dendi.peony.entity.Plant();
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

  /** Creates the user's record for the plant when it does not exist yet. */
  private UserPlant linkToUser(com.dendi.peony.entity.Plant plant, Long userId) {
    return userPlantRepository
        .findByUserIdAndPlantId(userId, plant.getId())
        .orElseGet(
            () -> {
              UserPlant link = new UserPlant();
              LocalDateTime now = LocalDateTime.now();
              link.setUserId(userId);
              link.setPlantId(plant.getId());
              link.setNickname("");
              link.setLastWateredAt(now);
              link.setNextWaterDueAt(
                  now.plusDays(
                      plant.getWateringFrequency() == null ? 0 : plant.getWateringFrequency()));
              link.setCreatedAt(now);
              link.setUpdatedAt(now);
              return userPlantRepository.save(link);
            });
  }

  private com.dendi.peony.entity.Plant findEntity(Long plantId) {
    return plantRepository.findById(plantId).orElseThrow(() -> new PlantNotFoundException(plantId));
  }

  private static void requireText(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(field + " is required");
    }
  }

  /**
   * Maps the entity to the wire model, dropping the premium-only fields for free-tier callers: the
   * fields stay null and the NON_NULL annotation on the generated model omits them from the JSON.
   */
  private static Plant toModel(com.dendi.peony.entity.Plant plant, UserTier tier) {
    Plant model = new Plant();
    model.setId(plant.getId());
    model.setCommonName(plant.getName());
    model.setScientificName(plant.getScientificName());
    model.setFamily(plant.getFamily());
    model.setTags(plant.getTags() == null ? null : List.of(plant.getTags()));
    model.setLightNeeds(plant.getLightNeeds());
    model.setWaterFrequency(plant.getWateringFrequency());
    model.setToPets(plant.getToPets());
    model.setToHumans(plant.getToHumans());
    if (tier == UserTier.PREMIUM) {
      model.setHumidity(plant.getHumidity());
      model.setTempRange(plant.getTempRange());
      model.setDifficulty(plant.getDifficulty());
      model.setNativeRegion(plant.getNativeRegion());
      model.setMatureSize(plant.getMatureSize());
    }
    return model;
  }
}
