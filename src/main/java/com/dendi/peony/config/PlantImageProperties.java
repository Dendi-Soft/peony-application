package com.dendi.peony.config;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Catalogue plant-image storage configuration (issues #10 and #45).
 *
 * <p>{@code peony.plant-images.dir} names the directory holding curated seed images and the
 * PlantNet species fallbacks copied into our storage. The default in {@code application.yml} is
 * {@code plant-images}, relative to the working directory; production overrides it with {@code
 * PEONY_PLANT_IMAGES_DIR}.
 *
 * @param dir directory holding the catalogue image files
 */
@ConfigurationProperties(prefix = "peony.plant-images")
public record PlantImageProperties(String dir) {

  /**
   * Returns the configured directory as an absolute, normalized path.
   *
   * @return the normalized directory that every catalogue image file must resolve beneath
   */
  public Path directory() {
    return Path.of(dir).toAbsolutePath().normalize();
  }
}
