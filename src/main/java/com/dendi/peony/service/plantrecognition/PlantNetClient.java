package com.dendi.peony.service.plantrecognition;

import com.dendi.peony.api.model.HitResponse;
import com.dendi.peony.api.model.PlantRecognitionResult;
import com.dendi.peony.api.model.RecognitionRequest;
import com.dendi.peony.api.model.Species;
import com.dendi.peony.exception.PlantNetApiException;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * HTTP client for the PlantNet v2 identify API.
 *
 * <p>Builds multipart identify requests against {@code /v2/identify/all}, retries rate-limit and
 * service-unavailable responses with exponential backoff, and maps the JSON response onto the
 * recognition result DTO.
 */
public class PlantNetClient {

  /** Base URL of the PlantNet v2 API. */
  public static final String DEFAULT_BASE_URL = "https://my-api.plantnet.org/v2";

  private static final Logger log = LoggerFactory.getLogger(PlantNetClient.class);

  private static final int DEFAULT_MAX_ATTEMPTS = 5;
  private static final long DEFAULT_BASE_DELAY_MS = 1000L;
  private static final long MAX_DELAY_MS = 30_000L;
  private static final int CONNECT_TIMEOUT_SECONDS = 30;
  private static final int READ_TIMEOUT_SECONDS = 30;

  // Query parameters matching the official PlantNet API request:
  // include-related-images=false&no-reject=false&nb-results=10&lang=en&detailed=false
  private static final int NB_RESULTS = 10;

  private final OkHttpClient httpClient;
  private final String baseUrl;
  private final int maxAttempts;
  private final long baseDelayMs;
  private final String apiKey;

  /**
   * Creates a client for the default PlantNet v2 endpoint with default retry settings.
   *
   * @param apiKey PlantNet API key, or null for the unauthenticated public endpoint
   */
  public PlantNetClient(String apiKey) {
    this(apiKey, DEFAULT_BASE_URL, DEFAULT_MAX_ATTEMPTS, DEFAULT_BASE_DELAY_MS);
  }

  /**
   * Creates a client with an explicit endpoint and retry settings.
   *
   * @param apiKey PlantNet API key, or null for the unauthenticated public endpoint
   * @param baseUrl base URL of the PlantNet v2 API (e.g., {@code https://my-api.plantnet.org/v2})
   * @param maxAttempts maximum number of attempts per request, including the first
   * @param baseDelayMs initial retry backoff delay in milliseconds, doubled on each retry
   */
  public PlantNetClient(String apiKey, String baseUrl, int maxAttempts, long baseDelayMs) {
    this.apiKey = apiKey;
    this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    this.maxAttempts = maxAttempts;
    this.baseDelayMs = baseDelayMs;

    OkHttpClient.Builder builder =
        new OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS);

    // PlantNet API v2 uses a query parameter for the API key, not a Bearer token header
    builder.addInterceptor(
        chain ->
            chain.proceed(
                chain.request().newBuilder().header("Accept", "application/json").build()));

    this.httpClient = builder.build();
  }

  /**
   * Sends the recognition request to the PlantNet API and returns the parsed result.
   *
   * @param request image bytes plus upload options
   * @return recognition result with hits ordered best match first
   * @throws IOException if the request cannot be completed after all retry attempts
   * @throws PlantNetApiException if the API responds with a non-retryable error status
   */
  public PlantRecognitionResult recognize(RecognitionRequest request) throws IOException {
    String json = executeWithRetry(buildRequest(request));
    return parseResponse(json);
  }

  private Request buildRequest(RecognitionRequest request) {
    MediaType mediaType = MediaType.parse(request.getFormat());
    if (mediaType == null) {
      throw new IllegalArgumentException("Unparseable image format: " + request.getFormat());
    }

    MultipartBody.Builder body = new MultipartBody.Builder().setType(MultipartBody.FORM);
    addImagePart(body, mediaType, "image1", request.getImage());

    if (request.getOrgans() != null && !request.getOrgans().isEmpty()) {
      body.addFormDataPart("organs", String.join(",", request.getOrgans()));
    } else {
      body.addFormDataPart("organs", "auto"); // Default to auto-detection
    }

    if (request.getLanguage() != null && !request.getLanguage().isEmpty()) {
      body.addFormDataPart("language", request.getLanguage());
    }

    return new Request.Builder().url(buildUrl()).post(body.build()).build();
  }

  private static void addImagePart(
      MultipartBody.Builder body, MediaType mediaType, String prefix, byte[] image) {
    if (image == null || image.length == 0) {
      return;
    }
    body.addFormDataPart(
        "images", prefix + "." + mediaType.subtype(), RequestBody.create(mediaType, image));
  }

  /**
   * Builds the identify URL with the documented query parameters and, when configured, the API key
   * as a query parameter.
   */
  private String buildUrl() {
    var url =
        new StringBuilder(baseUrl)
            .append("/identify/all?include-related-images=false")
            .append("&no-reject=false")
            .append("&nb-results=")
            .append(NB_RESULTS)
            .append("&lang=en")
            .append("&detailed=false");

    if (apiKey != null && !apiKey.isEmpty()) {
      url.append("&api-key=").append(JsonUtil.encodeUrl(apiKey));
    }

    return url.toString();
  }

  private String executeWithRetry(Request request) throws IOException {
    IOException lastNetworkError = null;

    for (int attempt = 1; attempt <= maxAttempts; attempt++) {
      try (Response response = httpClient.newCall(request).execute()) {
        String body = response.body() != null ? response.body().string() : "";

        if (response.isSuccessful()) {
          return body;
        }

        if (!isRetryable(response.code()) || attempt == maxAttempts) {
          throw new PlantNetApiException(response.code(), body);
        }

        log.warn(
            "PlantNet API returned {} on attempt {}/{} - retrying with backoff",
            response.code(),
            attempt,
            maxAttempts);
        sleepBeforeRetry(attempt);
      } catch (IOException e) {
        lastNetworkError = e;
        log.warn("Network error on attempt {}/{}: {}", attempt, maxAttempts, e.getMessage());
        if (attempt < maxAttempts) {
          sleepBeforeRetry(attempt);
        }
      }
    }

    throw new IOException(
        "PlantNet API request failed after " + maxAttempts + " attempts", lastNetworkError);
  }

  private static boolean isRetryable(int statusCode) {
    return statusCode == 429 || statusCode == 503;
  }

  private void sleepBeforeRetry(int attempt) throws IOException {
    long delay = Math.min(baseDelayMs * (1L << (attempt - 1)), MAX_DELAY_MS);
    try {
      Thread.sleep(delay);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IOException("Interrupted while waiting to retry PlantNet API request", e);
    }
  }

  private PlantRecognitionResult parseResponse(String json) throws IOException {
    PlantRecognitionResult result = JsonUtil.fromJson(json, PlantRecognitionResult.class);
    if (result == null) {
      throw new IOException("PlantNet API returned an empty response body");
    }
    // The contract pins api_version to a non-null string; normalize a missing value to ""
    if (result.getApiVersion() == null) {
      result.setApiVersion("");
    }
    normalizeHits(result);
    return result;
  }

  /** Fills in scientific-name parts the API may omit by splitting the full name. */
  private static void normalizeHits(PlantRecognitionResult result) {
    if (result.getHits() == null) {
      return;
    }
    for (HitResponse hit : result.getHits()) {
      Species species = hit.getSpecies();
      if (species == null) {
        continue;
      }
      String fullName = species.getScientificName();
      if (fullName == null || fullName.isEmpty()) {
        continue;
      }
      if (species.getScientificNameWithoutAuthor() == null) {
        species.setScientificNameWithoutAuthor(beforeLastSpace(fullName));
      }
      if (species.getScientificNameAuthorship() == null) {
        species.setScientificNameAuthorship(afterLastSpace(fullName));
      }
    }
  }

  private static String beforeLastSpace(String value) {
    int index = value.lastIndexOf(' ');
    return index > 0 ? value.substring(0, index) : value;
  }

  private static String afterLastSpace(String value) {
    int index = value.lastIndexOf(' ');
    return index >= 0 ? value.substring(index + 1) : "";
  }
}
