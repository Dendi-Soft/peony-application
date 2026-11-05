package com.dendi.peony.config;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Personal photo storage configuration (issue #44, ADR-008).
 *
 * <p>{@code peony.user-images.dir} names the directory holding the display and thumbnail JPEGs of
 * the caller's individuals. The default in {@code application.yml} is {@code user-images}, relative
 * to the working directory; production overrides it with {@code PEONY_USER_IMAGES_DIR}.
 *
 * @param dir directory holding the personal photo files
 */
@ConfigurationProperties(prefix = "peony.user-images")
public record UserImageProperties(String dir) {

  /**
   * Returns the configured directory as an absolute, normalized path.
   *
   * @return the normalized directory that every stored file must resolve beneath
   */
  public Path directory() {
    return Path.of(dir).toAbsolutePath().normalize();
  }
}
