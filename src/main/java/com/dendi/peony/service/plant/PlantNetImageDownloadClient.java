package com.dendi.peony.service.plant;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Downloads one PlantNet species image (issue #45, ADR-008).
 *
 * <p>Only absolute URLs whose origin equals the configured allowlist are fetched, redirects are
 * refused, and the response must be a JPEG no larger than 5 MiB. These rules stop an
 * upstream-controlled image URL from turning into an SSRF primitive or an unbounded download. No
 * API key is ever attached: PlantNet image URLs are public.
 */
public class PlantNetImageDownloadClient {

  /** Maximum accepted image size on the wire. */
  public static final int MAX_BYTES = 5 * 1024 * 1024;

  private static final int CONNECT_TIMEOUT_SECONDS = 10;

  private static final int READ_TIMEOUT_SECONDS = 10;

  private final OkHttpClient httpClient;

  private final HttpUrl allowedOrigin;

  /**
   * Creates a client restricted to one download origin.
   *
   * @param allowedOrigin absolute origin, e.g. {@code https://bs.plantnet.org}
   */
  public PlantNetImageDownloadClient(String allowedOrigin) {
    this.allowedOrigin = HttpUrl.parse(allowedOrigin);
    if (this.allowedOrigin == null) {
      throw new IllegalArgumentException("Unparseable PlantNet image origin: " + allowedOrigin);
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
   * Fetches the bytes of one species image.
   *
   * @param imageUrl absolute URL of the medium-size image
   * @return the JPEG bytes, never larger than {@link #MAX_BYTES}
   * @throws IOException when the URL is not allowed, the request fails, the response is not a JPEG,
   *     or the body exceeds the size cap
   */
  public byte[] download(String imageUrl) throws IOException {
    HttpUrl url = HttpUrl.parse(imageUrl);
    if (url == null || !sameOrigin(url, allowedOrigin)) {
      throw new IOException("PlantNet image URL is outside the allowlisted origin");
    }

    Request request = new Request.Builder().url(url).get().build();
    try (Response response = httpClient.newCall(request).execute()) {
      if (!response.isSuccessful()) {
        throw new IOException("PlantNet image download failed with status " + response.code());
      }
      ResponseBody body = response.body();
      if (body == null) {
        throw new IOException("PlantNet image download returned no body");
      }
      MediaType contentType = body.contentType();
      if (!isJpeg(contentType)) {
        throw new IOException("PlantNet image is not a JPEG response");
      }
      if (body.contentLength() > MAX_BYTES) {
        throw new IOException("PlantNet image exceeds the size cap");
      }
      byte[] bytes = readCapped(body);
      if (bytes.length == 0) {
        throw new IOException("PlantNet image download returned no bytes");
      }
      return bytes;
    }
  }

  private static byte[] readCapped(ResponseBody body) throws IOException {
    try (InputStream input = body.byteStream()) {
      byte[] bytes = input.readNBytes(MAX_BYTES + 1);
      if (bytes.length > MAX_BYTES) {
        throw new IOException("PlantNet image exceeds the size cap");
      }
      return bytes;
    }
  }

  private static boolean isJpeg(MediaType contentType) {
    return contentType != null
        && "image".equalsIgnoreCase(contentType.type())
        && "jpeg".equalsIgnoreCase(contentType.subtype());
  }

  private static boolean sameOrigin(HttpUrl url, HttpUrl allowedOrigin) {
    return url.scheme().equalsIgnoreCase(allowedOrigin.scheme())
        && url.host().equalsIgnoreCase(allowedOrigin.host())
        && url.port() == allowedOrigin.port();
  }
}
