package com.dendi.peony;

import java.io.IOException;
import java.io.InputStream;

/** Shared helper for loading test fixtures from {@code src/test/resources}. */
public final class TestFixtures {

  private TestFixtures() {}

  /**
   * Reads a test fixture from the test resources root.
   *
   * @param path fixture path relative to the test resources root (e.g.,
   *     "fixtures/webp-fixture.webp")
   * @return the fixture bytes
   * @throws IllegalStateException if the fixture is missing or cannot be read
   */
  public static byte[] read(String path) {
    try (InputStream in = TestFixtures.class.getResourceAsStream("/" + path)) {
      if (in == null) {
        throw new IllegalStateException("Missing test fixture: " + path);
      }
      return in.readAllBytes();
    } catch (IOException e) {
      throw new IllegalStateException("Failed to read test fixture: " + path, e);
    }
  }
}
