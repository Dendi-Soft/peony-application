package com.dendi.peony.controller.plantrecognition;

import com.dendi.peony.dto.plantrecognition.HitResponse;
import com.dendi.peony.dto.plantrecognition.RecognitionResponse;
import com.dendi.peony.dto.plantrecognition.SearchResponse;
import com.dendi.peony.dto.plantrecognition.SpeciesResponse;
import com.dendi.peony.service.plantrecognition.PlantNetClient;
import java.util.Base64;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for plant recognition endpoints using PlantNet API with caching.
 *
 * <p>This controller implements backend caching to avoid hitting PlantNet's 5 requests/minute rate
 * limit. Species data is cached and returned when available.
 */
@RestController
@RequestMapping("/api/plantrecognition")
@RequiredArgsConstructor
public class PlantRecognitionController {

  private final PlantNetClient plantNetClient;

  /**
   * Uploads an image and gets plant recognition results.
   *
   * @param imageData Base64-encoded image data
   * @param format Image MIME type (default: "image/jpeg")
   * @return Recognition result with top prediction from cache or fresh API call
   */
  @PostMapping(value = "/recognize", produces = "application/json")
  public ResponseEntity<RecognitionResponse> recognize(
      @RequestParam("imageData") String imageData,
      @RequestParam(defaultValue = "image/jpeg") String format) {

    byte[] imageBytes;
    try {
      imageBytes = Base64.getDecoder().decode(imageData);
    } catch (Exception e) {
      throw new RuntimeException("Invalid Base64 image data: " + e.getMessage(), e);
    }

    PlantNetClient.PlantRecognitionResult result;
    try {
      result = plantNetClient.recognize(imageBytes, format);
    } catch (Exception e) {
      throw new RuntimeException("PlantNet recognition failed: " + e.getMessage(), e);
    }

    return ResponseEntity.ok(
        new RecognitionResponse(
            result.query(),
            result.hits().stream()
                .map(hit -> new HitResponse(hit.genus(), hit.getScoreDouble()))
                .toList(),
            result.isSuccessful()));
  }

  /**
   * Gets species information by taxon ID. Data is retrieved from cache (caffeine/redis/database) if
   * available, otherwise makes a fresh API call and caches the result.
   *
   * @param taxonId Taxon identifier from PlantNet API
   * @return Species details from cache or null if not found
   */
  @GetMapping(value = "/species/{taxonId}", produces = "application/json")
  public ResponseEntity<SpeciesResponse> getSpecies(@PathVariable String taxonId) {
    try {
      PlantNetClient.PlantSpecies species = plantNetClient.getSpecies(taxonId);

      if (species != null && !species.scientificName().isBlank()) {
        return ResponseEntity.ok(
            new SpeciesResponse(species.scientificName(), species.genus(), taxonId));
      }

      return ResponseEntity.ok(new SpeciesResponse(null, species.genus(), taxonId));

    } catch (Exception e) {
      throw new RuntimeException("Failed to get species info: " + e.getMessage(), e);
    }
  }

  /**
   * Searches for taxa by name using PlantNet search API. Data is retrieved from cache
   * (caffeine/redis/database) if available, otherwise makes a fresh API call and caches the result.
   *
   * @param query Search term (Latin or common name)
   * @return List of matching taxa with confidence scores
   */
  @GetMapping(value = "/search", produces = "application/json")
  public ResponseEntity<SearchResponse> searchTaxa(@RequestParam("q") String query) {
    try {
      PlantNetClient.TaxonSearchResults results = plantNetClient.searchTaxa(query);

      if (results != null && !results.results().isEmpty()) {
        return ResponseEntity.ok(
            new SearchResponse(
                results.results().size(),
                query,
                results.results().stream()
                    .map(hit -> new HitResponse(hit.genus(), hit.getScoreDouble()))
                    .toList()));
      }

      return ResponseEntity.ok(new SearchResponse(0, query, List.of()));

    } catch (Exception e) {
      throw new RuntimeException("Search failed: " + e.getMessage(), e);
    }
  }
}
