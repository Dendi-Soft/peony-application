package com.dendi.peony.service.plantrecognition;

import com.dendi.peony.service.OptionalText;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;

/**
 * Selects the PlantNet related image to stage for each probable species.
 *
 * <p>Only hits that carry a catalogue draft can produce a species image. For each such hit one
 * image is chosen by organ preference ({@code habit}, then {@code leaf}, then any other organ, then
 * unknown), the licence must map to an accepted value, and the medium URL must be an absolute
 * HTTP(S) URL. Duplicate hits for the same binomial keep the better organ (ties keep the first
 * candidate), so at most one candidate per species is staged.
 */
public final class RelatedImageSelector {

  private static final Logger log = LoggerFactory.getLogger(RelatedImageSelector.class);

  private static final int ORGAN_RANK_HABIT = 0;
  private static final int ORGAN_RANK_LEAF = 1;
  private static final int ORGAN_RANK_OTHER = 2;
  private static final int ORGAN_RANK_UNKNOWN = 3;

  private RelatedImageSelector() {}

  /**
   * Selects one acceptable related image per draft-bearing species.
   *
   * @param results the PlantNet response's {@code results} array, or null
   * @return the candidates keyed by normalized binomial, in first-seen species order
   */
  public static List<RelatedImage> select(JsonNode results) {
    if (results == null || !results.isArray()) {
      return List.of();
    }
    Map<String, RelatedImage> bySpecies = new LinkedHashMap<>();
    for (JsonNode hit : results) {
      if (!isDraftBearing(hit)) {
        continue;
      }
      String normalized = normalizedBinomial(hit.get("species"));
      if (normalized == null) {
        continue;
      }
      RelatedImage candidate = chooseImage(hit.get("images"), normalized);
      if (candidate != null && isBetterThanStaged(candidate, bySpecies.get(normalized))) {
        bySpecies.put(normalized, candidate);
      }
    }
    return List.copyOf(bySpecies.values());
  }

  /**
   * A duplicate hit replaces an already chosen candidate only when its organ ranks better. This
   * prevents a later, worse hit from overwriting a better image for the same binomial; ties keep
   * the first-seen candidate, matching the within-hit tie rule.
   */
  private static boolean isBetterThanStaged(RelatedImage candidate, RelatedImage staged) {
    return staged == null || organRank(candidate.organ()) < organRank(staged.organ());
  }

  /** A hit can carry a species image only when it also carries a catalogue draft. */
  private static boolean isDraftBearing(JsonNode hit) {
    if (hit == null || !hit.isObject()) {
      return false;
    }
    JsonNode score = hit.get("score");
    return score != null
        && score.isNumber()
        && score.asDouble() >= RecognitionTierShaper.CATALOG_DRAFT_MIN_SCORE;
  }

  private static String normalizedBinomial(JsonNode species) {
    if (species == null || !species.isObject()) {
      return null;
    }
    String binomial =
        ScientificNames.binomial(
            text(species, "scientificNameWithoutAuthor"), text(species, "scientificName"));
    return ScientificNames.normalize(binomial);
  }

  private static RelatedImage chooseImage(JsonNode images, String normalizedBinomial) {
    if (images == null || !images.isArray()) {
      return null;
    }
    JsonNode best = null;
    License bestLicense = null;
    int bestRank = Integer.MAX_VALUE;
    for (JsonNode image : images) {
      if (image == null || !image.isObject()) {
        continue;
      }
      Optional<License> license = License.parse(text(image, "license"));
      if (license.isEmpty()) {
        log.debug(
            "Rejected PlantNet related image for {} with licence '{}'",
            normalizedBinomial,
            text(image, "license"));
        continue;
      }
      String mediumUrl = mediumUrl(image);
      if (mediumUrl == null) {
        continue;
      }
      int rank = organRank(text(image, "organ"));
      // Ties keep the upstream array order: only a strictly better rank replaces the choice.
      if (rank < bestRank) {
        best = image;
        bestLicense = license.get();
        bestRank = rank;
      }
    }
    if (best == null) {
      return null;
    }
    return new RelatedImage(
        normalizedBinomial,
        text(best, "organ"),
        text(best, "author"),
        bestLicense,
        text(best, "citation"),
        mediumUrl(best));
  }

  private static int organRank(String organ) {
    if (organ == null) {
      return ORGAN_RANK_UNKNOWN;
    }
    return switch (organ.toLowerCase(Locale.ROOT)) {
      case "habit" -> ORGAN_RANK_HABIT;
      case "leaf" -> ORGAN_RANK_LEAF;
      default -> ORGAN_RANK_OTHER;
    };
  }

  private static String mediumUrl(JsonNode image) {
    JsonNode url = image.get("url");
    if (url == null || !url.isObject()) {
      return null;
    }
    String medium = text(url, "m");
    if (medium == null) {
      return null;
    }
    try {
      URI uri = new URI(medium);
      String scheme = uri.getScheme();
      boolean webScheme = "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
      return uri.isAbsolute() && uri.getHost() != null && webScheme ? medium : null;
    } catch (URISyntaxException exception) {
      return null;
    }
  }

  /** Returns a trimmed textual field, or null when the field is absent or not a string. */
  private static String text(JsonNode node, String field) {
    if (node == null) {
      return null;
    }
    JsonNode value = node.get(field);
    return value != null && value.isString() ? OptionalText.trimToNull(value.asString()) : null;
  }
}
