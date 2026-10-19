package com.dendi.peony.dto.plantrecognition;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Data;

/** Response DTO for plant recognition results from PlantNet API. */
@Data
public class PlantRecognitionResult {

  @JsonProperty("successful")
  private boolean successful = true;

  /** Hits list containing prediction results */
  @JsonProperty("hits")
  private List<HitResponse> hits;

  /** PlantNet API version (e.g., "2025-01-17") - used to track which model/version was used */
  @JsonProperty("api_version")
  private String apiVersion = "";

  /** Remaining identification requests for the day (quota monitoring) */
  @JsonProperty("remaining_identification_requests")
  private Integer remainingRequests;

  /** Set hits list. */
  public void setHits(List<HitResponse> hits) {
    this.hits = hits;
  }

  // Legacy hit field for compatibility with existing code that uses .hit() method
  public HitResponse hit() {
    if (hits != null && !hits.isEmpty()) {
      return hits.get(0);
    }
    return null;
  }

  /** Set API version. */
  public void setApiVersion(String apiVersion) {
    this.apiVersion = apiVersion != null ? apiVersion : "";
  }

  /** Set remaining requests. */
  public void setRemainingRequests(Integer remainingRequests) {
    this.remainingRequests = remainingRequests;
  }
}
