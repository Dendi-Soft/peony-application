package com.dendi.peony.service.plantrecognition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dendi.peony.dto.plantrecognition.HitResponse;
import com.dendi.peony.dto.plantrecognition.PlantRecognitionResult;
import com.dendi.peony.dto.plantrecognition.RecognitionRequest;
import com.dendi.peony.exception.PlantNetApiException;
import java.io.IOException;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Tests for PlantNet API request building, retry behavior, and response parsing. */
class PlantNetClientTest {

  private static final String SUCCESS_BODY =
      """
      {
        "successful": true,
        "results": [
          {
            "score": 0.9534,
            "species": {
              "scientificName": "Ajuga reptans L.",
              "scientificNameWithoutAuthor": "Ajuga reptans",
              "scientificNameAuthorship": "L.",
              "genus": {"scientificName": "Ajuga", "scientificNameWithoutAuthor": "Ajuga"},
              "family": {"scientificName": "Lamiaceae", "scientificNameWithoutAuthor": "Lamiaceae"},
              "commonNames": ["Carpet bugle"]
            },
            "gbif": {"id": "2868390"},
            "powo": {"id": "30010088-2"}
          }
        ],
        "api_version": "2025-01-17",
        "remaining_identification_requests": 48
      }
      """;

  private MockWebServer server;
  private PlantNetClient client;

  @BeforeEach
  void setUp() throws IOException {
    server = new MockWebServer();
    server.start();
    client = new PlantNetClient(null, server.url("/").toString(), 5, 1L);
  }

  @AfterEach
  void tearDown() throws IOException {
    server.shutdown();
  }

  /** Parses the API response into hits and top-level metadata. */
  @Test
  void testRecognize_ReturnsHitsWithMetadata_WhenApiRespondsSuccessfully() throws Exception {
    server.enqueue(
        new MockResponse().setBody(SUCCESS_BODY).addHeader("Content-Type", "application/json"));

    PlantRecognitionResult result =
        client.recognize(RecognitionRequest.single("img".getBytes(), "image/jpeg"));

    assertTrue(result.isSuccessful());
    assertEquals("2025-01-17", result.getApiVersion());
    assertEquals(48, result.getRemainingRequests());
    assertEquals(1, result.getHits().size());

    HitResponse hit = result.getHits().get(0);
    assertEquals(0.9534, hit.getScore());
    assertEquals("Ajuga reptans L.", hit.getSpecies().getScientificName());
    assertEquals("Ajuga reptans", hit.getSpecies().getScientificNameWithoutAuthor());
    assertEquals("L.", hit.getSpecies().getScientificNameAuthorship());
    assertEquals("Carpet bugle", hit.getSpecies().getCommonNames().get(0));
    assertEquals("Ajuga", hit.getSpecies().getGenus().getScientificName());
    assertEquals("2868390", hit.getGbif().getId());
    assertEquals("30010088-2", hit.getPowo().getId());
  }

  /** Posts a multipart identify request with the documented query parameters. */
  @Test
  void testRecognize_PostsMultipartToIdentifyAll_WhenRequestIsMade() throws Exception {
    server.enqueue(new MockResponse().setBody(SUCCESS_BODY));

    client.recognize(RecognitionRequest.single(new byte[] {1, 2, 3}, "image/jpeg"));

    var recorded = server.takeRequest();
    assertTrue(recorded.getPath().startsWith("/identify/all?"));
    assertTrue(recorded.getPath().contains("include-related-images=false"));
    assertTrue(recorded.getPath().contains("no-reject=false"));
    assertTrue(recorded.getPath().contains("nb-results=10"));
    assertTrue(recorded.getPath().contains("lang=en"));
    assertTrue(recorded.getPath().contains("detailed=false"));
    assertFalse(recorded.getPath().contains("api-key"));
    assertEquals("POST", recorded.getMethod());

    String body = recorded.getBody().readUtf8();
    assertTrue(body.contains("name=\"images\""));
    assertTrue(body.contains("filename=\"image1.jpeg\""));
    assertTrue(body.contains("name=\"organs\""));
    assertTrue(body.contains("auto"));
  }

  /** Adds the API key as a URL-encoded query parameter when configured. */
  @Test
  void testRecognize_IncludesApiKeyInQuery_WhenApiKeyConfigured() throws Exception {
    PlantNetClient authenticated =
        new PlantNetClient("secret-key", server.url("/").toString(), 5, 1L);
    server.enqueue(new MockResponse().setBody(SUCCESS_BODY));

    authenticated.recognize(RecognitionRequest.single(new byte[] {1}, "image/jpeg"));

    var recorded = server.takeRequest();
    assertTrue(recorded.getPath().contains("api-key=secret-key"));
  }

  /** Retries a rate-limited response and succeeds once the API recovers. */
  @Test
  void testRecognize_RetriesThenSucceeds_WhenRateLimitedThenAvailable() throws Exception {
    server.enqueue(new MockResponse().setResponseCode(429));
    server.enqueue(new MockResponse().setBody(SUCCESS_BODY));

    PlantRecognitionResult result =
        client.recognize(RecognitionRequest.single(new byte[] {1}, "image/jpeg"));

    assertTrue(result.isSuccessful());
    assertEquals(2, server.getRequestCount());
  }

  /** Retries a service-unavailable response and succeeds once the API recovers. */
  @Test
  void testRecognize_RetriesThenSucceeds_WhenServiceUnavailableThenAvailable() throws Exception {
    server.enqueue(new MockResponse().setResponseCode(503));
    server.enqueue(new MockResponse().setBody(SUCCESS_BODY));

    PlantRecognitionResult result =
        client.recognize(RecognitionRequest.single(new byte[] {1}, "image/jpeg"));

    assertTrue(result.isSuccessful());
    assertEquals(2, server.getRequestCount());
  }

  /** Gives up with the upstream status after exhausting all retry attempts. */
  @Test
  void testRecognize_ThrowsPlantNetApiException_WhenRateLimitExhaustsRetries() throws Exception {
    for (int i = 0; i < 5; i++) {
      server.enqueue(new MockResponse().setResponseCode(429));
    }

    PlantNetApiException exception =
        assertThrows(
            PlantNetApiException.class,
            () -> client.recognize(RecognitionRequest.single(new byte[] {1}, "image/jpeg")));

    assertEquals(429, exception.getStatusCode());
    assertEquals(5, server.getRequestCount());
  }

  /** Fails fast with the upstream status for non-retryable client errors. */
  @Test
  void testRecognize_ThrowsPlantNetApiException_WhenApiReturnsClientError() throws Exception {
    server.enqueue(new MockResponse().setResponseCode(400).setBody("{\"error\":\"bad image\"}"));

    PlantNetApiException exception =
        assertThrows(
            PlantNetApiException.class,
            () -> client.recognize(RecognitionRequest.single(new byte[] {1}, "image/jpeg")));

    assertEquals(400, exception.getStatusCode());
    assertTrue(exception.getDetail().contains("bad image"));
    assertEquals(1, server.getRequestCount());
  }

  /** Reports a network failure as an IOException after all attempts fail. */
  @Test
  void testRecognize_ThrowsIOException_WhenNetworkFailsOnAllAttempts() throws Exception {
    server.shutdown();

    assertThrows(
        IOException.class,
        () -> client.recognize(RecognitionRequest.single(new byte[] {1}, "image/jpeg")));
  }

  /** Derives the missing scientific-name parts from the full name when the API omits them. */
  @Test
  void testRecognize_DerivesScientificNameParts_WhenApiOmitsThem() throws Exception {
    server.enqueue(
        new MockResponse()
            .setBody(
                """
                {
                  "successful": true,
                  "results": [
                    {"score": 0.8, "species": {"scientificName": "Ajuga reptans L."}}
                  ]
                }
                """));

    PlantRecognitionResult result =
        client.recognize(RecognitionRequest.single(new byte[] {1}, "image/jpeg"));

    HitResponse hit = result.getHits().get(0);
    assertEquals("Ajuga reptans", hit.getSpecies().getScientificNameWithoutAuthor());
    assertEquals("L.", hit.getSpecies().getScientificNameAuthorship());
  }

  /** Rejects formats OkHttp cannot parse before any HTTP call is made. */
  @Test
  void testRecognize_ThrowsIllegalArgumentException_WhenFormatIsUnparseable() {
    assertThrows(
        IllegalArgumentException.class,
        () -> client.recognize(RecognitionRequest.single(new byte[] {1}, "not-a-media-type")));
  }
}
