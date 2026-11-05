package com.dendi.peony.service.plant;

import com.dendi.peony.api.model.ImageSource;
import com.dendi.peony.api.model.PlantFields;
import com.dendi.peony.api.model.PlantImageCredit;
import com.dendi.peony.api.model.PlantResponse;
import com.dendi.peony.api.model.PlantSource;
import com.dendi.peony.entity.Plant;
import com.dendi.peony.entity.PlantImage;
import com.dendi.peony.entity.UserTier;
import java.util.Comparator;
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
   * Maps a plant to a standalone plant response with its image attribution.
   *
   * @param plant catalog plant
   * @param tier tier of the calling account
   * @param images catalog images of the plant, in any order
   * @return the tier-shaped plant response with credits ascending by position
   */
  public static PlantResponse toModel(Plant plant, UserTier tier, List<PlantImage> images) {
    PlantResponse model = new PlantResponse();
    applyPlantFields(model, plant, tier, images);
    return model;
  }

  /**
   * Maps one catalog image to its wire credit.
   *
   * @param image catalog image row
   * @return the credit, or null when there is no image
   */
  public static PlantImageCredit toCredit(PlantImage image) {
    if (image == null) {
      return null;
    }
    PlantImageCredit credit = new PlantImageCredit();
    credit.setPosition(image.getPosition());
    credit.setSource(
        image.getSource() == null ? null : ImageSource.fromValue(image.getSource().name()));
    credit.setAuthor(image.getAuthor());
    credit.setLicense(image.getLicense());
    credit.setCitation(image.getCitation());
    return credit;
  }

  /**
   * Maps catalog images to wire credits, ascending by position.
   *
   * @param images catalog image rows
   * @return the credits, or null when there are none so the field is omitted
   */
  public static List<PlantImageCredit> toCredits(List<PlantImage> images) {
    if (images == null || images.isEmpty()) {
      return null;
    }
    return images.stream()
        .sorted(Comparator.comparing(PlantImage::getPosition))
        .map(PlantResponseMapper::toCredit)
        .toList();
  }

  /**
   * Returns the wire value of a catalog row's creation origin.
   *
   * @param plant catalog plant
   * @return {@code PLANTNET_SCAN}, {@code MANUAL}, {@code SEED}, or null when unrecorded
   */
  private static PlantSource toWireSource(Plant plant) {
    return plant.getSource() == null ? null : PlantSource.fromValue(plant.getSource().name());
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
    model.setSource(toWireSource(plant));
    model.setCommonName(plant.getName());
    model.setScientificName(plant.getScientificName());
    model.setFamily(plant.getFamily());
    model.setTags(plant.getTags() == null ? null : List.of(plant.getTags()));
    model.setLightNeeds(plant.getLightNeeds());
    model.setWaterFrequency(plant.getWateringFrequency());
    model.setToPets(plant.getToPets());
    model.setToHumans(plant.getToHumans());
    // The generated list field starts as an empty list; clear it so NON_NULL omits it.
    model.setImageCredits(null);
    if (tier == UserTier.PREMIUM) {
      model.setHumidity(plant.getHumidity());
      model.setTempRange(plant.getTempRange());
      model.setDifficulty(plant.getDifficulty());
      model.setNativeRegion(plant.getNativeRegion());
      model.setMatureSize(plant.getMatureSize());
    }
    return model;
  }

  /**
   * Applies the tier-shaped plant fields and the catalog image attribution.
   *
   * @param model response model to fill
   * @param plant catalog plant
   * @param tier tier of the calling account
   * @param images catalog images of the plant, in any order
   * @param <T> response type exposing the shared plant fields
   * @return the filled model
   */
  public static <T extends PlantFields> T applyPlantFields(
      T model, Plant plant, UserTier tier, List<PlantImage> images) {
    applyPlantFields(model, plant, tier);
    model.setImageCredits(toCredits(images));
    return model;
  }
}
