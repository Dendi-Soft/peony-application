package com.dendi.peony.service.plant;

import java.nio.file.Path;

/**
 * Resolves stored image file names beneath a configured image directory.
 *
 * <p>File names arrive from the database, but a corrupted or malicious row must never let a read or
 * delete escape the configured directory, so every consumer resolves through the same guard.
 */
public final class ImageDirectory {

  private ImageDirectory() {}

  /**
   * Resolves a stored file name beneath {@code root}, rejecting paths outside it.
   *
   * @param root absolute, normalized image directory
   * @param filename stored file name
   * @return the resolved path, or null when the name is null or resolves outside the root
   */
  public static Path resolve(Path root, String filename) {
    if (filename == null) {
      return null;
    }
    Path resolved = root.resolve(filename).normalize();
    return resolved.startsWith(root) ? resolved : null;
  }
}
