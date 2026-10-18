package com.dendi.peony.dto.plantrecognition;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Response DTO for species information. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SpeciesResponse {

  private String scientificName;
  private String genus;
  private String taxonId;
}
