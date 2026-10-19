package com.dendi.peony.dto.plantrecognition;

import com.dendi.peony.entity.UserCapability;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Data;

/**
 * Recognition result returned to API consumers, mapped from the PlantNet API response and shaped
 * for the caller's tier by the tier shaper.
 *
 * <p>Null fields are omitted from the JSON, which is how the free-tier basic shape drops the
 * premium-only fields.
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PlantRecognitionResult {

  /** Whether the PlantNet API reported the identification as successful. */
  @JsonProperty("successful")
  private boolean successful = true;

  /** Prediction results, best match first. */
  @JsonProperty("hits")
  @JsonAlias("results")
  private List<HitResponse> hits;

  /** PlantNet API/model version used for the identification (e.g., "2025-01-17"). */
  @JsonProperty("api_version")
  private String apiVersion = "";

  /** Remaining identification requests for the current period (quota monitoring). */
  @JsonProperty("remaining_identification_requests")
  private Integer remainingRequests;

  /** Access tier of the calling account, in lowercase ({@code free} or {@code premium}). */
  @JsonProperty("tier")
  private String tier;

  /** Capability flags granted to the calling account's tier. */
  @JsonProperty("capabilities")
  private List<UserCapability> capabilities;

  /**
   * Sets the API version, normalizing null to an empty string.
   *
   * @param apiVersion API version from the PlantNet response
   */
  public void setApiVersion(String apiVersion) {
    this.apiVersion = apiVersion != null ? apiVersion : "";
  }
}
