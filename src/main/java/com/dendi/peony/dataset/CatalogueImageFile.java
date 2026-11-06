package com.dendi.peony.dataset;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * The committed catalogue image selection packaged on the classpath (issue #9 follow-up).
 *
 * <p>The file is read from {@code classpath:dataset/catalogue-images.json} by default. The location
 * is configurable through {@code peony.dataset.images.file} so tests can point at a small fixture.
 * A missing or malformed file is a hard failure: the curated images must never be silently skipped.
 *
 * @param source upstream image source, {@code Wikimedia Commons}
 * @param generatedAt date the selection was generated
 * @param licenses licence families accepted by the selection
 * @param species one entry per seeded species, in seed-file order
 */
public record CatalogueImageFile(
    String source, String generatedAt, List<String> licenses, List<CatalogueImageEntry> species) {

  /** Default classpath location of the catalogue image selection. */
  public static final String RESOURCE_PATH = "dataset/catalogue-images.json";

  private static final ObjectMapper JSON = new ObjectMapper();

  /**
   * Loads the catalogue image selection from the classpath.
   *
   * @param resourcePath classpath-relative location of the selection file
   * @return the parsed selection
   * @throws DatasetValidationException when the resource is missing or cannot be parsed
   */
  public static CatalogueImageFile load(String resourcePath) {
    if (resourcePath == null || resourcePath.isBlank()) {
      throw new DatasetValidationException(
          "The catalogue image selection location must not be blank", null);
    }
    ClassPathResource resource = new ClassPathResource(resourcePath);
    try (InputStream in = resource.getInputStream()) {
      CatalogueImageFile file = JSON.readValue(in, CatalogueImageFile.class);
      if (file == null) {
        throw new DatasetValidationException(
            "The catalogue image selection at classpath:" + resourcePath + " is empty", null);
      }
      return file;
    } catch (IOException e) {
      throw new DatasetValidationException(
          "Could not read the catalogue image selection at classpath:" + resourcePath, e);
    } catch (JacksonException e) {
      throw new DatasetValidationException(
          "Could not parse the catalogue image selection at classpath:" + resourcePath, e);
    }
  }
}
