package com.dendi.peony.dto.plantrecognition;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Response DTO for plant recognition results. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RecognitionResponse {

  private String query;
  private List<HitResponse> predictions;
  private boolean successful;
}
