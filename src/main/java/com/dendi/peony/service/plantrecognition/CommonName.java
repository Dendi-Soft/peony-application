package com.dendi.peony.service.plantrecognition;

import lombok.AllArgsConstructor;
import lombok.Data;

/** Common name record for plant species. */
@Data
@AllArgsConstructor
public class CommonName {

  private String language;
  private String name;

  @Override
  public String toString() {
    return "CommonName{" + "language='" + language + '\'' + ", name='" + name + '\'' + '}';
  }
}
