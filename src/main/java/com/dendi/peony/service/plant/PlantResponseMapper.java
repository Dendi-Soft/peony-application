package com.dendi.peony.service.plant;

import com.dendi.peony.api.model.PlantFields;
import com.dendi.peony.api.model.PlantResponse;
import com.dendi.peony.entity.Plant;
import com.dendi.peony.entity.UserTier;
import java.util.List;

/**
 * Maps catalog plants to wire models, applying the tier field visibility from
 * docs/api-tier-contracts.md in one place.
 *
 * <p>Free-tier responses carry the free field set only; the five premium-only fields stay null and
 * the generated models' NON_NULL annotation omits them from the JSON. Premium-tier responses carry
 * the full plant object.
 */
public final class PlantResponseMapper {

  private PlantResponseMapper() {}

  /**
   * Maps a plant to a standalone plant response.
   *
   * @param plant catalog plant
   * @param tier tier of the calling account
   * @return the tier-shaped plant response
   */
  public static PlantResponse toModel(Plant plant, UserTier tier) {
    PlantResponse model = new PlantResponse();
    applyPlantFields(model, plant, tier);
    return model;
  }

  /**
   * Applies the tier-shaped plant fields to a response model.
   *
   * <p>The generic target lets both plant response types share the visibility rules without
   * duplicating the free/premium field split.
   *
   * @param model response model to fill
   * @param plant catalog plant
   * @param tier tier of the calling account
   * @param <T> response type exposing the shared plant fields
   * @return the filled model
   */
  public static <T extends PlantFields> T applyPlantFields(T model, Plant plant, UserTier tier) {
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
