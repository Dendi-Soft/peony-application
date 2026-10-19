package com.dendi.peony.service.plant;

/**
 * Shared guard for required text fields in the plant and location services.
 *
 * <p>Failures surface as {@link IllegalArgumentException}, which the shared exception advice maps
 * to a 400 response.
 */
public final class RequiredText {

  private RequiredText() {}

  /**
   * Fails when the value is missing or blank.
   *
   * @param value the supplied value
   * @param field the field name used in the error message
   * @throws IllegalArgumentException when the value is missing or blank
   */
  public static void requireText(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(field + " is required");
    }
  }
}
