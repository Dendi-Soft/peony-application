package com.dendi.peony.service.plantrecognition;

import com.dendi.peony.api.model.HitResponse;
import com.dendi.peony.api.model.PlantRecognitionResult;
import com.dendi.peony.api.model.Species;
import com.dendi.peony.entity.UserTier;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Shapes recognition results according to the caller's access tier.
 *
 * <p>Free-tier accounts receive basic results: only the top hit, with the species reduced to its
 * scientific name and common names. Premium-tier accounts receive the complete result from the
 * PlantNet API. Both responses carry the tier name and the tier's capability flags so clients can
 * adapt to what they are entitled to.
 */
@Component
public class RecognitionTierShaper {

  /**
   * Shapes a full recognition result for the given tier.
   *
   * @param full the complete result from the PlantNet client
   * @param tier the caller's access tier
   * @return the tier-appropriate result
   */
  public PlantRecognitionResult shape(PlantRecognitionResult full, UserTier tier) {
    if (tier == UserTier.PREMIUM) {
      full.setTier(PlantRecognitionResult.TierEnum.PREMIUM);
      full.setCapabilities(capabilities(tier));
      return full;
    }
    return basicResult(full);
  }

  /** Builds the basic (free-tier) view of a full result. */
  private PlantRecognitionResult basicResult(PlantRecognitionResult full) {
    PlantRecognitionResult basic = new PlantRecognitionResult();
    basic.setSuccessful(full.getSuccessful());
    basic.setApiVersion(full.getApiVersion());
    basic.setHits(basicHits(full.getHits()));
    basic.setTier(PlantRecognitionResult.TierEnum.FREE);
    basic.setCapabilities(capabilities(UserTier.FREE));
    return basic;
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
    HitResponse basicHit = new HitResponse();
    basicHit.setScore(top.getScore());
    basicHit.setSpecies(basicSpecies(top.getSpecies()));
    // The generated free-shape fields (gbif, powo) stay null, so the fields are omitted from
    // the JSON entirely
    return List.of(basicHit);
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
