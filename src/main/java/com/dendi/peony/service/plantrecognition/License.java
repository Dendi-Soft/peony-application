package com.dendi.peony.service.plantrecognition;

import com.dendi.peony.service.OptionalText;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Licences accepted for PlantNet species images (issue #45, ADR-008).
 *
 * <p>Only licences that allow commercial reuse and adaptation are accepted: CC0, public domain, CC
 * BY, and CC BY-SA. The upstream value may carry a version suffix (for example {@code
 * cc-by-sa-4.0}); the version is accepted after normalization but the stored, canonical value is
 * the unversioned licence. Non-commercial, no-derivatives, unknown, and blank values are rejected.
 */
public enum License {
  CC0("cc0", "cc0-1.0"),
  PUBLIC_DOMAIN("public-domain"),
  CC_BY("cc-by", "cc-by-1.0", "cc-by-2.0", "cc-by-2.5", "cc-by-3.0", "cc-by-4.0"),
  CC_BY_SA(
      "cc-by-sa", "cc-by-sa-1.0", "cc-by-sa-2.0", "cc-by-sa-2.5", "cc-by-sa-3.0", "cc-by-sa-4.0");

  private final String canonical;

  private final Set<String> acceptedValues;

  License(String canonical, String... versionedValues) {
    this.canonical = canonical;
    this.acceptedValues = acceptedValues(canonical, versionedValues);
  }

  /**
   * Returns the unversioned canonical licence stored with an image and sent on the wire.
   *
   * @return the canonical licence, e.g. {@code cc-by-sa}
   */
  public String canonical() {
    return canonical;
  }

  /**
   * Parses an upstream licence value.
   *
   * <p>The value is trimmed, lowercased, and has underscore/whitespace runs normalized to hyphens
   * before an exact match against the accepted values; there is no prefix matching, so a malformed
   * NC/ND value cannot slip through.
   *
   * @param raw upstream licence value
   * @return the accepted licence, or empty when the value is blank, unknown, or not permitted
   */
  public static Optional<License> parse(String raw) {
    String normalized = normalize(raw);
    if (normalized == null) {
      return Optional.empty();
    }
    for (License license : values()) {
      if (license.acceptedValues.contains(normalized)) {
        return Optional.of(license);
      }
    }
    return Optional.empty();
  }

  private static String normalize(String raw) {
    String value = OptionalText.trimToNull(raw);
    return value == null ? null : value.toLowerCase(Locale.ROOT).replaceAll("[_\\s]+", "-");
  }

  private static Set<String> acceptedValues(String canonical, String... versionedValues) {
    Set<String> values = new HashSet<>();
    values.add(canonical);
    values.addAll(List.of(versionedValues));
    return Set.copyOf(values);
  }
}
