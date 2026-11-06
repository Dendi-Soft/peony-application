package com.dendi.peony.service.plantrecognition;

import com.dendi.peony.api.model.CatalogDraft;
import com.dendi.peony.api.model.Family;
import com.dendi.peony.api.model.HitResponse;
import com.dendi.peony.api.model.PlantRecognitionResult;
import com.dendi.peony.api.model.Species;
import com.dendi.peony.entity.UserTier;
import com.dendi.peony.service.OptionalText;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Shapes recognition results according to the caller's access tier.
 *
 * <p>Free-tier accounts receive basic results: only the top hit, with the species reduced to its
 * scientific name and common names. Premium-tier accounts receive the complete result from the
 * PlantNet API. Both responses carry the tier name and the tier's capability flags so clients can
 * adapt to what they are entitled to.
 *
 * <p>Hits at or above the confidence threshold additionally carry a stateless catalogue draft
 * derived from the full payload; the draft is built before the free-tier shape drops the species'
 * taxonomy.
 */
@Component
public class RecognitionTierShaper {

  /** Hits scoring below this confidence carry no catalogue draft (the threshold is inclusive). */
  static final double CATALOG_DRAFT_MIN_SCORE = 0.30;

  /**
   * Shapes a full recognition result for the given tier.
   *
   * @param full the complete result from the PlantNet client
   * @param tier the caller's access tier
   * @return the tier-appropriate result
   */
  public PlantRecognitionResult shape(PlantRecognitionResult full, UserTier tier) {
    if (tier == UserTier.PREMIUM) {
      // Drafts are built from the full payload before any shaping drops taxonomy.
      applyCatalogDrafts(full.getHits());
      full.setTier(PlantRecognitionResult.TierEnum.PREMIUM);
      full.setCapabilities(capabilities(tier));
      return full;
    }

    // Free tier: extract only the fields we need without cloning
    PlantRecognitionResult result = new PlantRecognitionResult();
    result.setSuccessful(full.getSuccessful());
    result.setApiVersion(full.getApiVersion());
    result.setHits(basicHits(full.getHits()));
    result.setTier(PlantRecognitionResult.TierEnum.FREE);
    result.setCapabilities(capabilities(UserTier.FREE));
    return result;
  }

  /** Maps the tier's capability flags onto the contract's capability enum. */
  private static List<PlantRecognitionResult.CapabilitiesEnum> capabilities(UserTier tier) {
    return tier.getCapabilities().stream()
        .map(capability -> PlantRecognitionResult.CapabilitiesEnum.fromValue(capability.name()))
        .toList();
  }

  /** Reduces the hits to the top one, keeping only the score and the basic species detail. */
  private List<HitResponse> basicHits(List<HitResponse> fullHits) {
    if (fullHits == null || fullHits.isEmpty()) {
      return List.of();
    }
    HitResponse top = fullHits.get(0);
    // Compute the draft from the full hit before the species detail is reduced below.
    CatalogDraft draft = catalogDraft(top);
    HitResponse basicHit = new HitResponse();
    basicHit.setScore(top.getScore());
    basicHit.setSpecies(basicSpecies(top.getSpecies()));
    basicHit.setCatalogDraft(draft);
    // The generated free-shape fields (gbif, powo) stay null, so the fields are omitted from
    // the JSON entirely
    return List.of(basicHit);
  }

  /** Attaches a catalogue draft to every hit at or above the confidence threshold. */
  private static void applyCatalogDrafts(List<HitResponse> hits) {
    if (hits == null) {
      return;
    }
    for (HitResponse hit : hits) {
      CatalogDraft draft = catalogDraft(hit);
      if (draft != null) {
        hit.setCatalogDraft(draft);
      }
    }
  }

  /** Derives the stateless catalogue draft for a hit, or null when the hit cannot produce one. */
  private static CatalogDraft catalogDraft(HitResponse hit) {
    if (hit == null || hit.getScore() == null || hit.getScore() < CATALOG_DRAFT_MIN_SCORE) {
      return null;
    }
    Species species = hit.getSpecies();
    if (species == null) {
      return null;
    }
    String binomial = ScientificNames.binomial(species);
    if (binomial == null) {
      return null;
    }
    CatalogDraft draft = new CatalogDraft();
    draft.setScientificName(binomial);
    draft.setCommonName(firstNonBlank(species.getCommonNames()));
    draft.setFamily(familyName(species.getFamily()));
    return draft;
  }

  /** Takes the family's author-free name, falling back to the full family name. */
  private static String familyName(Family family) {
    if (family == null) {
      return null;
    }
    return authorFreeName(family.getScientificNameWithoutAuthor(), family.getScientificName());
  }

  /** Returns the author-free name when present, otherwise the full name; null when both unknown. */
  private static String authorFreeName(String withoutAuthor, String fullName) {
    String name = OptionalText.trimToNull(withoutAuthor);
    return name != null ? name : OptionalText.trimToNull(fullName);
  }

  private static String firstNonBlank(List<String> values) {
    if (values == null) {
      return null;
    }
    for (String value : values) {
      String trimmed = OptionalText.trimToNull(value);
      if (trimmed != null) {
        return trimmed;
      }
    }
    return null;
  }

  /** Keeps only the scientific name and common names of the species. */
  private Species basicSpecies(Species fullSpecies) {
    Species basic = new Species();
    if (fullSpecies != null) {
      basic.setScientificName(fullSpecies.getScientificName());
      basic.setCommonNames(fullSpecies.getCommonNames());
    }
    // The taxonomic wrappers (genus, family) stay null so the fields are omitted from the JSON
    // entirely
    return basic;
  }
}
