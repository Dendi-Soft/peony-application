package com.dendi.peony.service.plant;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.imageio.ImageIO;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okio.Buffer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests the allowlisted PlantNet image downloader (issue #45, ADR-008): origin pinning against
 * SSRF, redirect refusal, the JPEG content-type requirement, and the 5 MiB cap.
 */
class PlantNetImageDownloadClientTest {

  private MockWebServer server;

  @BeforeEach
  void setUp() throws IOException {
    server = new MockWebServer();
    server.start();
  }

  @AfterEach
  void tearDown() throws IOException {
    server.shutdown();
  }

  /** A JPEG from the allowlisted origin is returned byte for byte, without credentials. */
  @Test
  void testDownload_ReturnsBody_WhenOriginAllowedAndJpeg() throws Exception {
    byte[] jpeg = jpegOf(32, 24);
    server.enqueue(jpegResponse(jpeg));
    PlantNetImageDownloadClient client = client();

    byte[] downloaded = client.download(server.url("/image/m/abc").toString());

    assertArrayEquals(jpeg, downloaded);
    var recorded = server.takeRequest();
    assertEquals("/image/m/abc", recorded.getPath());
    assertNull(recorded.getRequestUrl().query(), "image URLs must never carry an API key");
  }

  /** An image URL on any other origin is refused before a request is made. */
  @Test
  void testDownload_RejectsForeignOrigin_WhenUrlPointsElsewhere() throws Exception {
    PlantNetImageDownloadClient client = client();

    assertThrows(
        IOException.class,
        () -> client.download("https://evil.example/image/m/abc"),
        "only the configured origin may be downloaded from");
    assertEquals(0, server.getRequestCount(), "a foreign URL must not reach the network");
  }

  /** A relative or non-HTTP URL cannot be parsed as an absolute image URL. */
  @Test
  void testDownload_RejectsRelativeUrl_WhenUrlIsNotAbsolute() {
    PlantNetImageDownloadClient client = client();

    assertThrows(IOException.class, () -> client.download("/image/m/abc"));
  }

  /** A non-success status is a failed download, and redirects are never followed. */
  @Test
  void testDownload_RejectsRedirect_WhenServerAnswersWithLocation() {
    server.enqueue(
        new MockResponse().setResponseCode(302).setHeader("Location", "http://localhost/elsewhere"));
    PlantNetImageDownloadClient client = client();

    assertThrows(IOException.class, () -> client.download(server.url("/image/m/abc").toString()));
    assertEquals(1, server.getRequestCount(), "a redirect must not become a second request");
  }

  /** A 200 response carrying something other than image/jpeg is refused. */
  @Test
  void testDownload_RejectsNonJpegContentType_WhenServerReturnsPng() {
    server.enqueue(
        new MockResponse()
            .setHeader("Content-Type", "image/png")
            .setBody(new Buffer().write(new byte[] {1, 2, 3})));
    PlantNetImageDownloadClient client = client();

    assertThrows(IOException.class, () -> client.download(server.url("/image/m/abc").toString()));
  }

  /** A missing content type is refused as well. */
  @Test
  void testDownload_RejectsMissingContentType_WhenHeaderAbsent() {
    server.enqueue(new MockResponse().setBody(new Buffer().write(new byte[] {1, 2, 3})));
    PlantNetImageDownloadClient client = client();

    assertThrows(IOException.class, () -> client.download(server.url("/image/m/abc").toString()));
  }

  /** A body larger than the 5 MiB cap is refused, not buffered indefinitely. */
  @Test
  void testDownload_RejectsOversizedBody_WhenOverFiveMebibytes() {
    byte[] oversized = new byte[PlantNetImageDownloadClient.MAX_BYTES + 1];
    server.enqueue(jpegResponse(oversized));
    PlantNetImageDownloadClient client = client();

    assertThrows(IOException.class, () -> client.download(server.url("/image/m/abc").toString()));
  }

  /** An empty body carries no image. */
  @Test
  void testDownload_RejectsEmptyBody_WhenServerReturnsNoBytes() {
    server.enqueue(jpegResponse(new byte[0]));
    PlantNetImageDownloadClient client = client();

    assertThrows(IOException.class, () -> client.download(server.url("/image/m/abc").toString()));
  }

  private PlantNetImageDownloadClient client() {
    return new PlantNetImageDownloadClient(server.url("/").toString());
  }

  private static MockResponse jpegResponse(byte[] bytes) {
    return new MockResponse()
        .setHeader("Content-Type", "image/jpeg")
        .setBody(new Buffer().write(bytes));
  }

  private static byte[] jpegOf(int width, int height) throws IOException {
    BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
    Graphics2D graphics = image.createGraphics();
    try {
      graphics.setColor(Color.GREEN);
      graphics.fillRect(0, 0, width, height);
    } finally {
      graphics.dispose();
    }
    try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      ImageIO.write(image, "jpeg", out);
      return out.toByteArray();
    }
  }
}
