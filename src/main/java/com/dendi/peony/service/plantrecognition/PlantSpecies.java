package com.dendi.peony.service.plantrecognition;

import lombok.Data;

/** Species details from PlantNet API. */
@Data
public class PlantSpecies {

  private String taxonId;
  private String name;
  private String family;
}
