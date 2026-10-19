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
import com.dendi.peony.dto.plantrecognition.HitResponse;
import com.dendi.peony.dto.plantrecognition.PlantRecognitionResult;
import com.dendi.peony.dto.plantrecognition.RecognitionRequest;
import com.dendi.peony.service.plantrecognition.PlantNetClient;
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
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
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

  /** The recognize endpoint is protected: requests without a session get a 401. */
  @Test
  void testRecognize_Returns401_WhenNoSession() throws Exception {
    mockMvc
        .perform(recognizeRequest().session(new MockHttpSession()))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error").value("Authentication required"));

    verifyNoInteractions(plantNetClient);
  }

  /** A free-tier user may upload five photos in a calendar month. */
  @Test
  void testRecognize_AllowsUploads_WhenFreeTierUnderMonthlyLimit() throws Exception {
    stubRecognition();
    MockHttpSession session = signupAndSignin("free1");

    for (int i = 1; i <= 5; i++) {
      assertEquals(200, uploadStatus(session), "upload " + i + " must be allowed");
    }
    verify(plantNetClient, times(5)).recognize(any(RecognitionRequest.class));
  }

  /** The sixth photo of the month is rejected with a 429 for a free-tier user. */
  @Test
  void testRecognize_Returns429_WhenFreeTierExceedsMonthlyLimit() throws Exception {
    stubRecognition();
    MockHttpSession session = signupAndSignin("free2");

    for (int i = 1; i <= 5; i++) {
      assertEquals(200, uploadStatus(session), "upload " + i + " must be allowed");
    }

    MvcResult over = mockMvc.perform(recognizeRequest().session(session)).andReturn();
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
    MockHttpSession session = signupAndSignin("premium1");
    upgrade(session);

    for (int i = 1; i <= 7; i++) {
      assertEquals(200, uploadStatus(session), "upload " + i + " must be allowed");
    }
    verify(plantNetClient, times(7)).recognize(any(RecognitionRequest.class));
  }

  /** The upload counter resets when the calendar month changes. */
  @Test
  void testRecognize_ResetsCounter_WhenCalendarMonthChanges() throws Exception {
    stubRecognition();
    MockHttpSession session = signupAndSignin("free3");

    for (int i = 1; i <= 5; i++) {
      assertEquals(200, uploadStatus(session), "upload " + i + " must be allowed");
    }
    assertEquals(429, uploadStatus(session), "6th upload of October must be rejected");

    clock.advance(Period.ofMonths(1));
    assertEquals(200, uploadStatus(session), "first upload of November must be allowed");

    verify(plantNetClient, times(6)).recognize(any(RecognitionRequest.class));
  }

  /**
   * Free-tier users get basic results: the top hit only, with the species reduced to its scientific
   * name and common names.
   */
  @Test
  void testRecognize_ReturnsBasicResults_WhenFreeTierUser() throws Exception {
    stubRecognition();
    MockHttpSession session = signupAndSignin("free-shape");

    mockMvc
        .perform(recognizeRequest().session(session))
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
    MockHttpSession session = signupAndSignin("premium-shape");
    upgrade(session);

    mockMvc
        .perform(recognizeRequest().session(session))
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
    return post("/api/plantrecognition/recognize")
        .contentType(MediaType.APPLICATION_JSON_VALUE)
        .param("base64Image", Base64.getEncoder().encodeToString(JPEG_BYTES));
  }

  private int uploadStatus(MockHttpSession session) throws Exception {
    MvcResult result = mockMvc.perform(recognizeRequest().session(session)).andReturn();
    return result.getResponse().getStatus();
  }

  /** Stubs the PlantNet client with a fully populated result, as the real API returns. */
  private void stubRecognition() throws java.io.IOException {
    PlantRecognitionResult result = new PlantRecognitionResult();
    result.setApiVersion("2025-01-17");
    result.setRemainingRequests(42);

    HitResponse hit = new HitResponse();
    hit.setScore(0.9);
    HitResponse.Species species = hit.getSpecies();
    species.setScientificNameWithoutAuthor("Ajuga reptans");
    species.setScientificNameAuthorship("L.");
    species.setScientificName("Ajuga reptans L.");
    species.getGenus().setScientificName("Ajuga");
    species.getFamily().setScientificName("Lamiaceae");
    species.setCommonNames(List.of("Carpet bugle"));
    hit.getGbif().setId("2868390");
    hit.getPowo().setId("30010088-2");

    result.setHits(List.of(hit));
    when(plantNetClient.recognize(any(RecognitionRequest.class))).thenReturn(result);
  }

  private void upgrade(MockHttpSession session) throws Exception {
    mockMvc.perform(post("/api/subscription/upgrade").session(session)).andExpect(status().isOk());
  }

  private MockHttpSession signupAndSignin(String username) throws Exception {
    mockMvc
        .perform(
            post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .content(
                    """
                    {"username":"%s","email":"%s@example.com","password":"supersecret1","firstName":"Test","lastName":"User"}"""
                        .formatted(username, username)))
        .andExpect(status().isCreated());

    MockHttpSession session = new MockHttpSession();
    mockMvc
        .perform(
            post("/api/auth/signin")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .content(
                    """
                    {"username":"%s","password":"supersecret1"}"""
                        .formatted(username)))
        .andExpect(status().isOk());
    return session;
  }
}
