package com.dendi.peony.dataset;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * The versioned curated dataset packaged on the classpath.
 *
 * <p>The file is read from {@code classpath:dataset/plants-seed.json}, which keeps the loader
 * working both in a Gradle run and inside the packaged jar/container. A missing or malformed file
 * is a hard failure: the curated catalogue must never be silently skipped.
 *
 * @param version seed-file format version; {@code 1} is the only supported version
 * @param plants curated entries
 */
public record DatasetSeedFile(int version, List<DatasetSeedEntry> plants) {

  /** Classpath location of the curated seed file. */
  public static final String RESOURCE_PATH = "dataset/plants-seed.json";

  private static final ObjectMapper JSON = new ObjectMapper();

  /**
   * Loads the curated dataset from the classpath.
   *
   * @return the parsed seed file
   * @throws DatasetValidationException when the resource is missing or cannot be parsed
   */
  public static DatasetSeedFile load() {
    ClassPathResource resource = new ClassPathResource(RESOURCE_PATH);
    try (InputStream in = resource.getInputStream()) {
      DatasetSeedFile file = JSON.readValue(in, DatasetSeedFile.class);
      if (file == null) {
        throw new DatasetValidationException(
            "The curated dataset at classpath:" + RESOURCE_PATH + " is empty", null);
      }
      return file;
    } catch (IOException e) {
      throw new DatasetValidationException(
          "Could not read the curated dataset at classpath:" + RESOURCE_PATH, e);
    } catch (JacksonException e) {
      throw new DatasetValidationException(
          "Could not parse the curated dataset at classpath:" + RESOURCE_PATH, e);
    }
  }
}
