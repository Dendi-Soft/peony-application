package com.dendi.peony.api.model;

import java.util.List;

/**
 * Plant fields shared by the tier-shaped plant responses.
 *
 * <p>The Java generator flattens the spec's {@code allOf}, so {@link UserPlantResponse} does not
 * inherit {@link PlantResponse}'s fields. This hand-written interface is the compile-time link
 * between the two generated types: both declare it through {@code x-implements}, which lets one
 * mapper fill either response without duplicating the free/premium field rules. It exposes only the
 * setters that mapper needs and is not a wire contract of its own.
 */
public interface PlantFields {

  /**
   * Sets the catalog identifier that the response's plant fields describe.
   *
   * @param id value of {@code plants.id}
   */
  void setId(Long id);

  /**
   * Sets the display name shown to the caller.
   *
   * @param commonName value of {@code plants.name}
   */
  void setCommonName(String commonName);

  /**
   * Sets the scientific name of the catalog plant.
   *
   * @param scientificName value of {@code plants.scientific_name}
   */
  void setScientificName(String scientificName);

  /**
   * Sets the botanical family used to group the plant in search and detail views.
   *
   * @param family value of {@code plants.family}; null when the catalog row has none
   */
  void setFamily(String family);

  /**
   * Sets the searchable tags attached to the catalog plant.
   *
   * @param tags values of {@code plants.tags}
   */
  void setTags(List<String> tags);

  /**
   * Sets the light requirement shown in the plant's care information.
   *
   * @param lightNeeds value of {@code plants.light_needs}
   */
  void setLightNeeds(String lightNeeds);

  /**
   * Sets the watering interval used to schedule the next watering.
   *
   * @param waterFrequency days between waterings, from {@code plants.watering_frequency}
   */
  void setWaterFrequency(Integer waterFrequency);

  /**
   * Sets whether the plant is toxic to pets.
   *
   * @param toPets value of {@code plants.toxic_to_pets}
   */
  void setToPets(Boolean toPets);

  /**
   * Sets whether the plant is toxic to humans.
   *
   * @param toHumans value of {@code plants.toxic_to_humans}
   */
  void setToHumans(Boolean toHumans);

  /**
   * Sets the humidity preference; populated only in premium-tier responses.
   *
   * @param humidity value of {@code plants.humidity}
   */
  void setHumidity(String humidity);

  /**
   * Sets the temperature range; populated only in premium-tier responses.
   *
   * @param tempRange value of {@code plants.temp_range}
   */
  void setTempRange(String tempRange);

  /**
   * Sets the care difficulty; populated only in premium-tier responses.
   *
   * @param difficulty value of {@code plants.difficulty}
   */
  void setDifficulty(String difficulty);

  /**
   * Sets the native region; populated only in premium-tier responses.
   *
   * @param nativeRegion value of {@code plants.native_region}
   */
  void setNativeRegion(String nativeRegion);

  /**
   * Sets the mature size; populated only in premium-tier responses.
   *
   * @param matureSize value of {@code plants.mature_size}
   */
  void setMatureSize(String matureSize);
}
