package com.dendi.peony;

import com.dendi.peony.entity.UserPlantImage;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.UUID;
import javax.imageio.ImageIO;

/** Shared test helpers for image fixtures: generated JPEG bytes and unsaved image rows. */
public final class TestImages {

  private TestImages() {}

  /**
   * Encodes a solid green JPEG of the given dimensions.
   *
   * @param width image width in pixels
   * @param height image height in pixels
   * @return the JPEG bytes
   * @throws IOException when the JPEG cannot be encoded
   */
  public static byte[] jpegOf(int width, int height) throws IOException {
    BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
    Graphics2D graphics = image.createGraphics();
    try {
      graphics.setColor(Color.GREEN);
      graphics.fillRect(0, 0, width, height);
    } finally {
      graphics.dispose();
    }
    try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      if (!ImageIO.write(image, "jpeg", out)) {
        throw new IllegalStateException("No JPEG writer is available");
      }
      return out.toByteArray();
    }
  }

  /**
   * Builds an unsaved personal-image row for one individual.
   *
   * <p>The file names follow the service's opaque convention; the caller persists the row and, when
   * needed, writes the matching files.
   *
   * @param userPlantId individual id
   * @param imageId immutable client-facing image id
   * @param createdAt stored creation timestamp
   * @return the image row, not yet persisted
   */
  public static UserPlantImage userPlantImage(
      Long userPlantId, UUID imageId, LocalDateTime createdAt) {
    UserPlantImage image = new UserPlantImage();
    image.setUserPlantId(userPlantId);
    image.setImageId(imageId);
    image.setFilename(imageId + ".jpg");
    image.setThumbFilename(imageId + "-thumb.jpg");
    image.setWidth(1280);
    image.setHeight(960);
    image.setByteSize(123456L);
    image.setCreatedAt(createdAt);
    return image;
  }
}
