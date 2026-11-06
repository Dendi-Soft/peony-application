package com.dendi.peony.dataset;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
 * Tests the catalogue image downloader (issue #9 follow-up): origin pinning against SSRF, redirect
 * refusal, the JPEG/PNG/WebP content-type and magic-byte requirements, and the 32 MiB cap.
 */
class CatalogueImageDownloaderTest {

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

  /** A JPEG from the allowlisted origin is returned byte for byte. */
  @Test
  void testDownload_ReturnsBody_WhenOriginAllowedAndJpeg() throws Exception {
    byte[] jpeg = jpegOf(32, 24);
    server.enqueue(imageResponse("image/jpeg", jpeg));
    CatalogueImageDownloader client = client();

    byte[] downloaded = client.download(server.url("/catalogue/snake.jpg").toString());

    assertArrayEquals(jpeg, downloaded);
    assertEquals("/catalogue/snake.jpg", server.takeRequest().getPath());
  }

  /**
   * Wikimedia rejects generic HTTP clients, so every request must carry the project's descriptive
   * User-Agent; OkHttp's default agent returns HTTP 403.
   */
  @Test
  void testDownload_SendsDescriptiveUserAgent_WhenFetchingImage() throws Exception {
    server.enqueue(imageResponse("image/jpeg", jpegOf(8, 8)));
    CatalogueImageDownloader client = client();

    client.download(server.url("/catalogue/plant.jpg").toString());

    assertEquals(
        CatalogueImageDownloader.USER_AGENT, server.takeRequest().getHeader("User-Agent"));
  }

  /** PNG and WebP bodies are accepted because the manifest can carry either. */
  @Test
  void testDownload_ReturnsBody_WhenOriginAllowedAndPngOrWebp() throws Exception {
    byte[] png = pngOf(16, 12);
    server.enqueue(imageResponse("image/png", png));
    CatalogueImageDownloader client = client();

    assertArrayEquals(png, client.download(server.url("/catalogue/plant.png").toString()));
  }

  /** An image URL on any other origin is refused before a request is made. */
  @Test
  void testDownload_RejectsForeignOrigin_WhenUrlPointsElsewhere() throws Exception {
    CatalogueImageDownloader client = client();

    assertThrows(
        IOException.class,
        () -> client.download("https://evil.example/catalogue/plant.jpg"),
        "only the configured origin may be downloaded from");
    assertEquals(0, server.getRequestCount(), "a foreign URL must not reach the network");
  }

  /** A plain-http URL is refused when the allowlisted origin is https. */
  @Test
  void testDownload_RejectsHttpUrl_WhenAllowedOriginIsHttps() {
    CatalogueImageDownloader client = new CatalogueImageDownloader("https://upload.wikimedia.org");

    assertThrows(
        IOException.class,
        () -> client.download("http://upload.wikimedia.org/catalogue/plant.jpg"),
        "the scheme must match the allowlisted https origin");
  }

  /** A non-success status is a failed download, and redirects are never followed. */
  @Test
  void testDownload_RejectsRedirect_WhenServerAnswersWithLocation() {
    server.enqueue(
        new MockResponse()
            .setResponseCode(302)
            .setHeader("Location", "http://localhost/elsewhere"));
    CatalogueImageDownloader client = client();

    assertThrows(
        IOException.class, () -> client.download(server.url("/catalogue/plant.jpg").toString()));
    assertEquals(1, server.getRequestCount(), "a redirect must not become a second request");
  }

  /** A 200 response carrying something other than an image type is refused. */
  @Test
  void testDownload_RejectsNonImageContentType_WhenServerReturnsHtml() {
    server.enqueue(
        new MockResponse()
            .setHeader("Content-Type", "text/html")
            .setBody("<html>not an image</html>"));
    CatalogueImageDownloader client = client();

    assertThrows(
        IOException.class, () -> client.download(server.url("/catalogue/plant.jpg").toString()));
  }

  /** A declared image type with a non-image body fails the magic-byte check. */
  @Test
  void testDownload_RejectsNonImageBody_WhenContentTypeIsMisleading() {
    server.enqueue(imageResponse("image/jpeg", "not really a jpeg".getBytes()));
    CatalogueImageDownloader client = client();

    assertThrows(
        IOException.class, () -> client.download(server.url("/catalogue/plant.jpg").toString()));
  }

  /** A body larger than the 32 MiB cap is refused, not buffered indefinitely. */
  @Test
  void testDownload_RejectsOversizedBody_WhenOverTwentyMebibytes() {
    byte[] oversized = new byte[CatalogueImageDownloader.MAX_BYTES + 1];
    server.enqueue(imageResponse("image/jpeg", oversized));
    CatalogueImageDownloader client = client();

    assertThrows(
        IOException.class, () -> client.download(server.url("/catalogue/plant.jpg").toString()));
  }

  /** An empty body carries no image. */
  @Test
  void testDownload_RejectsEmptyBody_WhenServerReturnsNoBytes() {
    server.enqueue(imageResponse("image/jpeg", new byte[0]));
    CatalogueImageDownloader client = client();

    assertThrows(
        IOException.class, () -> client.download(server.url("/catalogue/plant.jpg").toString()));
  }

  private CatalogueImageDownloader client() {
    return new CatalogueImageDownloader(server.url("/").toString());
  }

  private static MockResponse imageResponse(String contentType, byte[] bytes) {
    return new MockResponse()
        .setHeader("Content-Type", contentType)
        .setBody(new Buffer().write(bytes));
  }

  private static byte[] jpegOf(int width, int height) throws IOException {
    return encodedOf(width, height, "jpeg");
  }

  private static byte[] pngOf(int width, int height) throws IOException {
    return encodedOf(width, height, "png");
  }

  private static byte[] encodedOf(int width, int height, String format) throws IOException {
    BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
    Graphics2D graphics = image.createGraphics();
    try {
      graphics.setColor(Color.GREEN);
      graphics.fillRect(0, 0, width, height);
    } finally {
      graphics.dispose();
    }
    try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      ImageIO.write(image, format, out);
      return out.toByteArray();
    }
  }
}
