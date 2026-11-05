package com.dendi.peony.service.plantrecognition;

import com.dendi.peony.api.model.Species;
import com.dendi.peony.service.OptionalText;
import java.util.Locale;

/**
 * Shared scientific-name handling for recognition, species-image staging, and catalogue lookup.
 *
 * <p>PlantNet returns names with authorship and infraspecific ranks. The catalogue and the staging
 * table are keyed by the binomial, so both the recognition shaper and the image pipeline reduce
 * names through this class instead of duplicating the reduction rules.
 */
public final class ScientificNames {

  private ScientificNames() {}

  /**
   * Normalizes a name for use as a lookup key.
   *
   * <p>The value is trimmed, internal whitespace is collapsed to single spaces, and the result is
   * lowercased with {@link Locale#ROOT} so "Ajuga Reptans" and "ajuga reptans" match.
   *
   * @param value raw scientific name
   * @return the normalized name, or null when the value is missing or blank
   */
  public static String normalize(String value) {
    String trimmed = OptionalText.trimToNull(value);
    if (trimmed == null) {
      return null;
    }
    return trimmed.replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
  }

  /**
   * Returns the binomial (genus and species epithet) of a PlantNet species hit.
   *
   * @param species PlantNet species detail
   * @return the binomial without authorship, or null when no binomial can be derived
   */
  public static String binomial(Species species) {
    if (species == null) {
      return null;
    }
    return binomial(species.getScientificNameWithoutAuthor(), species.getScientificName());
  }

  /**
   * Returns the binomial of a name, preferring the author-free form when supplied.
   *
   * @param withoutAuthor PlantNet's author-free name, or null
   * @param fullName full scientific name with authorship, or null
   * @return the binomial without authorship, or null when no binomial can be derived
   */
  public static String binomial(String withoutAuthor, String fullName) {
    String name = OptionalText.trimToNull(withoutAuthor);
    if (name == null) {
      name = OptionalText.trimToNull(fullName);
    }
    if (name == null) {
      return null;
    }
    String[] tokens = name.split("\\s+");
    return tokens.length < 2 ? null : tokens[0] + " " + tokens[1];
  }

  /**
   * Returns the binomial of a full scientific name.
   *
   * @param fullName full scientific name, with or without authorship
   * @return the binomial without authorship, or null when no binomial can be derived
   */
  public static String binomial(String fullName) {
    return binomial(null, fullName);
  }
}
