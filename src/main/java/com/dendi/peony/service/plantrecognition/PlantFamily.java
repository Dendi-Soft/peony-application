package com.dendi.peony.service.plantrecognition;

import lombok.Data;

/** Family details from PlantNet API. */
@Data
public class PlantFamily {

  private String familyId;
  private String name;
}
