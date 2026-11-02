package com.dendi.peony.service;

/**
 * Normalizes optional text received from the wire or the catalogue.
 *
 * <p>Null, empty, and whitespace-only values all mean "unknown" and become {@code null}; every
 * other value is returned trimmed.
 */
public final class OptionalText {

  private OptionalText() {}

  /**
   * Returns the trimmed value, or {@code null} when the value is missing or blank.
   *
   * @param value optional text
   * @return the trimmed text, or {@code null} when absent or blank
   */
  public static String trimToNull(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }
}
