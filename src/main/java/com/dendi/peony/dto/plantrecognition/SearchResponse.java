package com.dendi.peony.dto.plantrecognition;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;

/** Response DTO for taxon search results. */
@Data
@AllArgsConstructor
public class SearchResponse {

  private int count;
  private String query;
  private List<HitResponse> results;
}
