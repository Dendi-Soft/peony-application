package com.dendi.peony.dataset;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Downloads one curated catalogue image (issue #9 follow-up).
 *
 * <p>Only absolute URLs whose origin equals the configured allowlist are fetched, redirects are
 * refused, and the response must be a JPEG, PNG, or WebP no larger than 32 MiB. Both the declared
 * content type and the body's magic bytes are checked, so an allowlisted URL cannot turn into an
 * SSRF primitive or smuggle a non-image payload. No credentials are attached: Wikimedia image URLs
 * are public.
 */
public class CatalogueImageDownloader {

  /** Maximum accepted image size on the wire. */
  public static final int MAX_BYTES = 32 * 1024 * 1024;

  /**
   * Descriptive User-Agent required by Wikimedia. Its image servers reject generic HTTP clients
   * (OkHttp's default agent gets HTTP 403), so every catalogue-image request identifies the project
   * and its purpose.
   */
  static final String USER_AGENT =
      "PeonyCatalogueImageSeeder/1.0 (https://github.com/Dendi-Soft/peony; educational plant dataset)";

  private static final int CONNECT_TIMEOUT_SECONDS = 15;

  private static final int READ_TIMEOUT_SECONDS = 30;

  private final OkHttpClient httpClient;

  private final HttpUrl allowedOrigin;

  /**
   * Creates a downloader restricted to one image origin.
   *
   * @param allowedOrigin absolute origin, e.g. {@code https://upload.wikimedia.org}; tests pass a
   *     MockWebServer origin
   */
  public CatalogueImageDownloader(String allowedOrigin) {
    this.allowedOrigin = HttpUrl.parse(allowedOrigin);
    if (this.allowedOrigin == null) {
      throw new IllegalArgumentException("Unparseable catalogue image origin: " + allowedOrigin);
    }
    this.httpClient =
        new OkHttpClient.Builder()
            .followRedirects(false)
            .followSslRedirects(false)
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build();
  }

  /**
   * Fetches the bytes of one catalogue image.
   *
   * @param imageUrl absolute URL from the committed selection file
   * @return the image bytes, never larger than {@link #MAX_BYTES}
   * @throws IOException when the URL is not allowed, the request fails, the response is not a
   *     supported image, or the body exceeds the size cap
   */
  public byte[] download(String imageUrl) throws IOException {
    HttpUrl url = HttpUrl.parse(imageUrl);
    if (url == null || !sameOrigin(url, allowedOrigin)) {
      throw new IOException("Catalogue image URL is outside the allowlisted origin");
    }

    Request request = new Request.Builder().url(url).header("User-Agent", USER_AGENT).get().build();
    try (Response response = httpClient.newCall(request).execute()) {
      if (!response.isSuccessful()) {
        throw new IOException("Catalogue image download failed with status " + response.code());
      }
      ResponseBody body = response.body();
      if (body == null) {
        throw new IOException("Catalogue image download returned no body");
      }
      ImageType declaredType = imageType(body.contentType());
      if (declaredType == null) {
        throw new IOException("Catalogue image is not a JPEG, PNG, or WebP response");
      }
      if (body.contentLength() > MAX_BYTES) {
        throw new IOException("Catalogue image exceeds the size cap");
      }
      byte[] bytes = readCapped(body);
      if (bytes.length == 0) {
        throw new IOException("Catalogue image download returned no bytes");
      }
      if (imageTypeOf(bytes) != declaredType) {
        throw new IOException("Catalogue image body does not match its declared content type");
      }
      return bytes;
    }
  }

  private static byte[] readCapped(ResponseBody body) throws IOException {
    try (InputStream input = body.byteStream()) {
      byte[] bytes = input.readNBytes(MAX_BYTES + 1);
      if (bytes.length > MAX_BYTES) {
        throw new IOException("Catalogue image exceeds the size cap");
      }
      return bytes;
    }
  }

  /** Supported image encodings; the manifest only carries these. */
  private enum ImageType {
    JPEG,
    PNG,
    WEBP
  }

  private static ImageType imageType(MediaType contentType) {
    if (contentType == null || !"image".equalsIgnoreCase(contentType.type())) {
      return null;
    }
    return switch (contentType.subtype().toLowerCase(Locale.ROOT)) {
      case "jpeg", "jpg" -> ImageType.JPEG;
      case "png" -> ImageType.PNG;
      case "webp" -> ImageType.WEBP;
      default -> null;
    };
  }

  private static ImageType imageTypeOf(byte[] bytes) {
    if (startsWith(bytes, 0xFF, 0xD8, 0xFF)) {
      return ImageType.JPEG;
    }
    if (startsWith(bytes, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)) {
      return ImageType.PNG;
    }
    if (bytes.length >= 12
        && bytes[0] == 'R'
        && bytes[1] == 'I'
        && bytes[2] == 'F'
        && bytes[3] == 'F'
        && bytes[8] == 'W'
        && bytes[9] == 'E'
        && bytes[10] == 'B'
        && bytes[11] == 'P') {
      return ImageType.WEBP;
    }
    return null;
  }

  private static boolean startsWith(byte[] bytes, int... signature) {
    if (bytes.length < signature.length) {
      return false;
    }
    for (int i = 0; i < signature.length; i++) {
      if ((bytes[i] & 0xFF) != signature[i]) {
        return false;
      }
    }
    return true;
  }

  private static boolean sameOrigin(HttpUrl url, HttpUrl allowedOrigin) {
    return url.scheme().equalsIgnoreCase(allowedOrigin.scheme())
        && url.host().equalsIgnoreCase(allowedOrigin.host())
        && url.port() == allowedOrigin.port();
  }
}
