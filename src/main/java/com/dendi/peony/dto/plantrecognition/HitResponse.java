package com.dendi.peony.dto.plantrecognition;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Individual prediction result. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class HitResponse {

  private String genus;
  private double score;
}
