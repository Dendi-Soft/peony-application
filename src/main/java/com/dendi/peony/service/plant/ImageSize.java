package com.dendi.peony.service.plant;

/** Which stored copy of a personal photo to load (issue #44, ADR-008). */
public enum ImageSize {

  /** The display image, bounded at a 1280 px long edge and served to the individual hero. */
  DISPLAY,

  /**
   * The server-generated thumbnail, bounded at a 256 px long edge and served to the My Plants row.
   */
  THUMBNAIL
}
