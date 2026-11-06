package com.dendi.peony.service.plantrecognition;

import com.dendi.peony.api.model.PlantRecognitionResult;
import java.util.List;

/**
 * The result of one PlantNet identify call: the public recognition shape plus the acceptable
 * related images that can become the species fallback.
 *
 * @param result recognition result served to the caller
 * @param relatedImages acceptable related images, one per normalized binomial
 */
public record RecognitionOutcome(PlantRecognitionResult result, List<RelatedImage> relatedImages) {}
