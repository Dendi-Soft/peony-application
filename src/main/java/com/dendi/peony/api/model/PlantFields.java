package com.dendi.peony.api.model;

import java.util.List;

/**
 * Plant fields shared by the tier-shaped plant responses.
 *
 * <p>Both {@link PlantResponse} and {@link UserPlantResponse} implement this interface (declared
 * through the spec's {@code x-implements} extension) so the tier visibility rules can be applied
 * by one mapper instead of being duplicated per response type. The interface only exposes the
 * setters the mapper needs; it is not a wire contract of its own.
 */
public interface PlantFields {

  /**
   * Sets the plant id.
   *
   * @param id plant id
   */
  void setId(Long id);

  /**
   * Sets the common name.
   *
   * @param commonName common name
   */
  void setCommonName(String commonName);

  /**
   * Sets the scientific name.
   *
   * @param scientificName scientific name
   */
  void setScientificName(String scientificName);

  /**
   * Sets the botanical family.
   *
   * @param family botanical family
   */
  void setFamily(String family);

  /**
   * Sets the searchable tags.
   *
   * @param tags searchable tags
   */
  void setTags(List<String> tags);

  /**
   * Sets the light needs.
   *
   * @param lightNeeds light needs
   */
  void setLightNeeds(String lightNeeds);

  /**
   * Sets the watering frequency.
   *
   * @param waterFrequency days between waterings
   */
  void setWaterFrequency(Integer waterFrequency);

  /**
   * Sets the pet toxicity flag.
   *
   * @param toPets whether the plant is toxic to pets
   */
  void setToPets(Boolean toPets);

  /**
   * Sets the human toxicity flag.
   *
   * @param toHumans whether the plant is toxic to humans
   */
  void setToHumans(Boolean toHumans);

  /**
   * Sets the humidity preference (premium tier only).
   *
   * @param humidity humidity preference
   */
  void setHumidity(String humidity);

  /**
   * Sets the temperature range (premium tier only).
   *
   * @param tempRange temperature range
   */
  void setTempRange(String tempRange);

  /**
   * Sets the care difficulty (premium tier only).
   *
   * @param difficulty care difficulty
   */
  void setDifficulty(String difficulty);

  /**
   * Sets the native region (premium tier only).
   *
   * @param nativeRegion native region
   */
  void setNativeRegion(String nativeRegion);

  /**
   * Sets the mature size (premium tier only).
   *
   * @param matureSize mature size
   */
  void setMatureSize(String matureSize);
}
