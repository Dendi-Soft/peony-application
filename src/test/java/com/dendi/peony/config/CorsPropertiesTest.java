package com.dendi.peony.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Tests for the CORS allowlist properties (issue #24).
 *
 * <p>Covers the defensive normalization of the bound list (null, blank and untrimmed entries) and
 * relaxed binding of the comma-separated {@code PEONY_CORS_ALLOWED_ORIGINS} environment-variable
 * shape.
 */
class CorsPropertiesTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner().withUserConfiguration(CorsConfig.class);

  /** A missing property binds as an empty list, which disables CORS. */
  @Test
  void testConstructor_NormalizesNullToEmptyList() {
    assertEquals(List.of(), new CorsProperties(null).allowedOrigins());
  }

  /** Blank entries are dropped and real entries are trimmed. */
  @Test
  void testConstructor_TrimsEntriesAndDropsBlankOnes() {
    CorsProperties properties =
        new CorsProperties(List.of("  http://one.example  ", "", "   ", "http://two.example"));

    assertEquals(List.of("http://one.example", "http://two.example"), properties.allowedOrigins());
  }

  /** The bound list is immutable, so callers cannot mutate the allowlist after startup. */
  @Test
  void testConstructor_ReturnsImmutableList() {
    CorsProperties properties = new CorsProperties(List.of("http://one.example"));

    assertThrows(
        UnsupportedOperationException.class,
        () -> properties.allowedOrigins().add("http://two.example"));
  }

  /** A comma-separated value binds to one entry per origin. */
  @Test
  void testBinding_BindsCommaSeparatedOrigins() {
    contextRunner
        .withPropertyValues("peony.cors.allowed-origins=http://one.example,http://two.example")
        .run(
            context -> {
              CorsProperties properties = context.getBean(CorsProperties.class);

              assertEquals(
                  List.of("http://one.example", "http://two.example"), properties.allowedOrigins());
            });
  }

  /** An empty environment value binds to an empty allowlist rather than a blank entry. */
  @Test
  void testBinding_EmptyValueBindsToEmptyList() {
    contextRunner
        .withPropertyValues("peony.cors.allowed-origins=")
        .run(
            context -> {
              CorsProperties properties = context.getBean(CorsProperties.class);

              assertEquals(List.of(), properties.allowedOrigins());
            });
  }
}
