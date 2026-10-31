package com.dendi.peony.controller.plantrecognition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dendi.peony.AdjustableClock;
import com.dendi.peony.TestcontainersConfiguration;
import com.dendi.peony.api.model.Family;
import com.dendi.peony.api.model.Gbif;
import com.dendi.peony.api.model.Genus;
import com.dendi.peony.api.model.HitResponse;
import com.dendi.peony.api.model.PlantRecognitionResult;
import com.dendi.peony.api.model.Powo;
import com.dendi.peony.api.model.RecognitionRequest;
import com.dendi.peony.api.model.Species;
import com.dendi.peony.service.plantrecognition.PlantNetClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.Period;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Tests for the tier rules applied to the recognize endpoint at the REST seam, against a real
 * PostgreSQL database: session protection, the free-tier monthly upload limit (with an injectable
 * clock), and the unlimited premium tier.
 *
 * <p>The PlantNet client is mocked so no network calls happen; the counter rows and the tier are
 * exercised end to end.
 */
@Import({TestcontainersConfiguration.class, PlantRecognitionTierTest.TestClockConfig.class})
@SpringBootTest
@AutoConfigureMockMvc
class PlantRecognitionTierTest {

  private static final byte[] JPEG_BYTES = new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 1};
  private static final ObjectMapper JSON = new ObjectMapper();

  @MockitoBean private PlantNetClient plantNetClient;

  @Autowired private MockMvc mockMvc;

  @Autowired private AdjustableClock clock;

  /** Test clock: starts mid-October so month rollover can be advanced deterministically. */
  @TestConfiguration(proxyBeanMethods = false)
  static class TestClockConfig {

    /**
     * Creates the adjustable test clock, taking precedence over the application clock by type.
     *
     * <p>Named distinctly from the main {@code clock} bean because bean definition overriding is
     * disabled.
     *
     * @return the clock starting at 2026-10-05
     */
    @Bean
    @Primary
    AdjustableClock testClock() {
      return new AdjustableClock(Instant.parse("2026-10-05T12:00:00Z"));
    }
  }

  /** The recognize endpoint is protected: requests without a bearer token get a 401. */
  @Test
  void testRecognize_Returns401_WhenNoBearer() throws Exception {
    mockMvc
        .perform(recognizeRequest())
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error").value("Authentication required"));

    verifyNoInteractions(plantNetClient);
  }

  /** A free-tier user may upload five photos in a calendar month. */
  @Test
  void testRecognize_AllowsUploads_WhenFreeTierUnderMonthlyLimit() throws Exception {
    stubRecognition();
    String token = signupAndSignin("free1");

    for (int i = 1; i <= 5; i++) {
      assertEquals(200, uploadStatus(token), "upload " + i + " must be allowed");
    }
    verify(plantNetClient, times(5)).recognize(any(RecognitionRequest.class));
  }

  /** The sixth photo of the month is rejected with a 429 for a free-tier user. */
  @Test
  void testRecognize_Returns429_WhenFreeTierExceedsMonthlyLimit() throws Exception {
    stubRecognition();
    String token = signupAndSignin("free2");

    for (int i = 1; i <= 5; i++) {
      assertEquals(200, uploadStatus(token), "upload " + i + " must be allowed");
    }

    MvcResult over =
        mockMvc
            .perform(recognizeRequest().header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andReturn();
    assertEquals(429, over.getResponse().getStatus());
    assertEquals(
        "{\"error\":\"Free tier limit of 5 photo uploads per month reached. "
            + "Upgrade to premium for unlimited uploads.\"}",
        over.getResponse().getContentAsString());

    verify(plantNetClient, times(5)).recognize(any(RecognitionRequest.class));
  }

  /** A premium-tier user has no monthly upload limit. */
  @Test
  void testRecognize_AllowsUnlimitedUploads_WhenPremiumTierUser() throws Exception {
    stubRecognition();
    String token = signupAndSignin("premium1");
    upgrade(token);

    for (int i = 1; i <= 7; i++) {
      assertEquals(200, uploadStatus(token), "upload " + i + " must be allowed");
    }
    verify(plantNetClient, times(7)).recognize(any(RecognitionRequest.class));
  }

  /** The upload counter resets when the calendar month changes. */
  @Test
  void testRecognize_ResetsCounter_WhenCalendarMonthChanges() throws Exception {
    stubRecognition();
    String token = signupAndSignin("free3");

    for (int i = 1; i <= 5; i++) {
      assertEquals(200, uploadStatus(token), "upload " + i + " must be allowed");
    }
    assertEquals(429, uploadStatus(token), "6th upload of October must be rejected");

    clock.advance(Period.ofMonths(1));
    // Access tokens live for one hour, so re-authenticate after the month jump.
    token = signin("free3");
    assertEquals(200, uploadStatus(token), "first upload of November must be allowed");

    verify(plantNetClient, times(6)).recognize(any(RecognitionRequest.class));
  }

  /**
   * Free-tier users get basic results: the top hit only, with the species reduced to its scientific
   * name and common names.
   */
  @Test
  void testRecognize_ReturnsBasicResults_WhenFreeTierUser() throws Exception {
    stubRecognition();
    String token = signupAndSignin("free-shape");

    mockMvc
        .perform(recognizeRequest().header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.successful").value(true))
        .andExpect(jsonPath("$.api_version").value("2025-01-17"))
        .andExpect(jsonPath("$.tier").value("free"))
        .andExpect(jsonPath("$.capabilities.length()").value(4))
        .andExpect(jsonPath("$.capabilities[0]").value("PLANTS_READ"))
        .andExpect(jsonPath("$.capabilities[1]").value("PLANTS_CREATE"))
        .andExpect(jsonPath("$.capabilities[2]").value("PLANTS_UPDATE_NOTES"))
        .andExpect(jsonPath("$.capabilities[3]").value("RECOGNITION_BASIC"))
        .andExpect(jsonPath("$.hits.length()").value(1))
        .andExpect(jsonPath("$.hits[0].score").value(0.9))
        .andExpect(jsonPath("$.hits[0].species.scientificName").value("Ajuga reptans L."))
        .andExpect(jsonPath("$.hits[0].species.commonNames.length()").value(1))
        .andExpect(jsonPath("$.hits[0].species.commonNames[0]").value("Carpet bugle"))
        .andExpect(jsonPath("$.hits[0].species.scientificNameWithoutAuthor").doesNotExist())
        .andExpect(jsonPath("$.hits[0].species.scientificNameAuthorship").doesNotExist())
        .andExpect(jsonPath("$.hits[0].species.genus").doesNotExist())
        .andExpect(jsonPath("$.hits[0].species.family").doesNotExist())
        .andExpect(jsonPath("$.hits[0].gbif").doesNotExist())
        .andExpect(jsonPath("$.hits[0].powo").doesNotExist())
        .andExpect(jsonPath("$.remaining_identification_requests").doesNotExist());
  }

  /** Premium-tier users get the complete PlantNet result with full species detail. */
  @Test
  void testRecognize_ReturnsCompleteResults_WhenPremiumTierUser() throws Exception {
    stubRecognition();
    String token = signupAndSignin("premium-shape");
    upgrade(token);

    mockMvc
        .perform(recognizeRequest().header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.successful").value(true))
        .andExpect(jsonPath("$.api_version").value("2025-01-17"))
        .andExpect(jsonPath("$.tier").value("premium"))
        .andExpect(jsonPath("$.capabilities.length()").value(6))
        .andExpect(jsonPath("$.capabilities[0]").value("PLANTS_READ"))
        .andExpect(jsonPath("$.capabilities[1]").value("PLANTS_CREATE"))
        .andExpect(jsonPath("$.capabilities[2]").value("PLANTS_UPDATE_NOTES"))
        .andExpect(jsonPath("$.capabilities[3]").value("RECOGNITION_COMPLETE"))
        .andExpect(jsonPath("$.capabilities[4]").value("MULTI_DEVICE_SYNC"))
        .andExpect(jsonPath("$.capabilities[5]").value("ADVANCED_ANALYTICS"))
        .andExpect(jsonPath("$.hits.length()").value(1))
        .andExpect(jsonPath("$.hits[0].score").value(0.9))
        .andExpect(jsonPath("$.hits[0].species.scientificName").value("Ajuga reptans L."))
        .andExpect(jsonPath("$.hits[0].species.scientificNameWithoutAuthor").value("Ajuga reptans"))
        .andExpect(jsonPath("$.hits[0].species.scientificNameAuthorship").value("L."))
        .andExpect(jsonPath("$.hits[0].species.genus.scientificName").value("Ajuga"))
        .andExpect(jsonPath("$.hits[0].species.family.scientificName").value("Lamiaceae"))
        .andExpect(jsonPath("$.hits[0].species.commonNames.length()").value(1))
        .andExpect(jsonPath("$.hits[0].species.commonNames[0]").value("Carpet bugle"))
        .andExpect(jsonPath("$.hits[0].gbif.id").value("2868390"))
        .andExpect(jsonPath("$.hits[0].powo.id").value("30010088-2"))
        .andExpect(jsonPath("$.remaining_identification_requests").value(42));
  }

  private MockHttpServletRequestBuilder recognizeRequest() {
    return post("/api/v1/plantrecognition/recognize")
        .contentType(MediaType.APPLICATION_JSON_VALUE)
        .param("base64Image", Base64.getEncoder().encodeToString(JPEG_BYTES));
  }

  private int uploadStatus(String token) throws Exception {
    MvcResult result =
        mockMvc
            .perform(recognizeRequest().header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andReturn();
    return result.getResponse().getStatus();
  }

  /** Stubs the PlantNet client with a fully populated result, as the real API returns. */
  private void stubRecognition() throws java.io.IOException {
    PlantRecognitionResult result = new PlantRecognitionResult();
    result.setApiVersion("2025-01-17");
    result.setRemainingIdentificationRequests(42);

    Species species = new Species();
    species.setScientificNameWithoutAuthor("Ajuga reptans");
    species.setScientificNameAuthorship("L.");
    species.setScientificName("Ajuga reptans L.");
    Genus genus = new Genus();
    genus.setScientificName("Ajuga");
    species.setGenus(genus);
    Family family = new Family();
    family.setScientificName("Lamiaceae");
    species.setFamily(family);
    species.setCommonNames(List.of("Carpet bugle"));

    Gbif gbif = new Gbif();
    gbif.setId("2868390");
    Powo powo = new Powo();
    powo.setId("30010088-2");

    HitResponse hit = new HitResponse();
    hit.setScore(0.9);
    hit.setSpecies(species);
    hit.setGbif(gbif);
    hit.setPowo(powo);

    result.setHits(List.of(hit));
    when(plantNetClient.recognize(any(RecognitionRequest.class))).thenReturn(result);
  }

  private void upgrade(String token) throws Exception {
    mockMvc
        .perform(
            post("/api/v1/subscription/upgrade")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isOk());
  }

  private String signupAndSignin(String username) throws Exception {
    mockMvc
        .perform(
            post("/api/v1/auth/signup")
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .content(
                    """
                    {"username":"%s","email":"%s@example.com","password":"supersecret1","firstName":"Test","lastName":"User"}"""
                        .formatted(username, username)))
        .andExpect(status().isCreated());

    return signin(username);
  }

  private String signin(String username) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/auth/signin")
                    .contentType(MediaType.APPLICATION_JSON_VALUE)
                    .content(
                        """
                        {"username":"%s","password":"supersecret1"}"""
                            .formatted(username)))
            .andExpect(status().isOk())
            .andReturn();
    return JSON.readTree(result.getResponse().getContentAsString()).get("token").asText();
  }
}
