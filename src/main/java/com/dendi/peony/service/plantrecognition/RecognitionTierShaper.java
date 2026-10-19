package com.dendi.peony.service.plantrecognition;

import com.dendi.peony.dto.plantrecognition.HitResponse;
import com.dendi.peony.dto.plantrecognition.PlantRecognitionResult;
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
      full.setTier("premium");
      full.setCapabilities(UserTier.PREMIUM.getCapabilities());
      return full;
    }
    return basicResult(full);
  }

  /** Builds the basic (free-tier) view of a full result. */
  private PlantRecognitionResult basicResult(PlantRecognitionResult full) {
    PlantRecognitionResult basic = new PlantRecognitionResult();
    basic.setSuccessful(full.isSuccessful());
    basic.setApiVersion(full.getApiVersion());
    basic.setHits(basicHits(full.getHits()));
    basic.setTier("free");
    basic.setCapabilities(UserTier.FREE.getCapabilities());
    return basic;
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
    // The free shape carries no external IDs or taxonomic wrappers; drop the defaults so the
    // fields are omitted from the JSON entirely
    basicHit.setGbif(null);
    basicHit.setPowo(null);
    return List.of(basicHit);
  }

  /** Keeps only the scientific name and common names of the species. */
  private HitResponse.Species basicSpecies(HitResponse.Species fullSpecies) {
    HitResponse.Species basic = new HitResponse.Species();
    if (fullSpecies != null) {
      basic.setScientificName(fullSpecies.getScientificName());
      basic.setCommonNames(fullSpecies.getCommonNames());
    }
    // Drop the taxonomic wrappers so the fields are omitted from the JSON entirely
    basic.setGenus(null);
    basic.setFamily(null);
    return basic;
  }
}
