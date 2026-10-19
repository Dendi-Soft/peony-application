package com.dendi.peony.controller.plantrecognition;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dendi.peony.TestFixtures;
import com.dendi.peony.api.model.Gbif;
import com.dendi.peony.api.model.HitResponse;
import com.dendi.peony.api.model.PlantRecognitionResult;
import com.dendi.peony.api.model.Powo;
import com.dendi.peony.api.model.RecognitionRequest;
import com.dendi.peony.api.model.Species;
import com.dendi.peony.entity.User;
import com.dendi.peony.entity.UserTier;
import com.dendi.peony.exception.PlantNetApiException;
import com.dendi.peony.exception.UnsupportedImageFormatException;
import com.dendi.peony.exception.UploadLimitExceededException;
import com.dendi.peony.service.auth.PeonyUserDetails;
import com.dendi.peony.service.auth.UploadRateLimitService;
import com.dendi.peony.service.auth.UserService;
import com.dendi.peony.service.plantrecognition.PlantNetClient;
import com.dendi.peony.service.plantrecognition.RecognitionTierShaper;
import com.dendi.peony.service.plantrecognition.WebpConverter;
import java.io.IOException;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** Tests for the recognize endpoint at the REST seam, with the PlantNet client mocked. */
@ExtendWith(MockitoExtension.class)
class PlantRecognitionControllerTest {

  private static final byte[] JPEG_BYTES = new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 1};

  @Mock private PlantNetClient plantNetClient;

  @Mock private UploadRateLimitService uploadRateLimitService;

  @Mock private UserService userService;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    // The converter and tier shaper are pure functions - use the real ones so the handoffs are
    // exercised
    var controller =
        new PlantRecognitionController(
            plantNetClient,
            new WebpConverter(),
            uploadRateLimitService,
            userService,
            new RecognitionTierShaper());
    // Standalone MockMvc does not register Spring Security's argument resolvers (the full app
    // context does), so the @AuthenticationPrincipal parameter needs the resolver explicitly
    mockMvc =
        MockMvcBuilders.standaloneSetup(controller)
            .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
            .build();
    // The endpoint is session-protected; run every test as a premium account so tier limits do
    // not interfere with the recognition behavior under test
    PeonyUserDetails principal = new PeonyUserDetails(1L, "tester", "hash");
    SecurityContextHolder.getContext()
        .setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated(principal, "N/A", List.of()));
    when(userService.currentUser(principal)).thenReturn(premiumUser());
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  /** A premium account with the id the principal carries. */
  private User premiumUser() {
    User user =
        User.create("tester", "tester@example.com", "hash", "Test", "User", UserTier.PREMIUM);
    user.setId(1L);
    return user;
  }

  /** Recognizes a multipart JPEG upload and returns the client result. */
  @Test
  void testRecognize_ReturnsResult_WhenMultipartFileUploaded() throws Exception {
    when(plantNetClient.recognize(any(RecognitionRequest.class)))
        .thenReturn(resultWithHit("Ajuga reptans L."));

    mockMvc
        .perform(
            multipart("/api/v1/plantrecognition/recognize")
                .file(
                    new MockMultipartFile(
                        "file", "plant.jpg", MediaType.IMAGE_JPEG_VALUE, JPEG_BYTES)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.hits[0].species.scientificName").value("Ajuga reptans L."));

    ArgumentCaptor<RecognitionRequest> captor = ArgumentCaptor.forClass(RecognitionRequest.class);
    verify(plantNetClient).recognize(captor.capture());
    assertArrayEquals(JPEG_BYTES, captor.getValue().getImage());
    assertEquals("image/jpeg", captor.getValue().getFormat());
  }

  /** Recognizes a base64-encoded image and passes the decoded bytes to the client. */
  @Test
  void testRecognize_ReturnsResult_WhenBase64ImageProvided() throws Exception {
    when(plantNetClient.recognize(any(RecognitionRequest.class)))
        .thenReturn(resultWithHit("Ajuga reptans L."));
    String base64 = Base64.getEncoder().encodeToString(JPEG_BYTES);

    mockMvc
        .perform(
            post("/api/v1/plantrecognition/recognize")
                .contentType(MediaType.APPLICATION_JSON)
                .param("base64Image", base64)
                .param("format", "image/jpeg"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.hits[0].species.scientificName").value("Ajuga reptans L."));

    ArgumentCaptor<RecognitionRequest> captor = ArgumentCaptor.forClass(RecognitionRequest.class);
    verify(plantNetClient).recognize(captor.capture());
    assertArrayEquals(JPEG_BYTES, captor.getValue().getImage());
  }

  /** Converts a WebP upload to JPEG and tells the client the effective format. */
  @Test
  void testRecognize_PassesConvertedJpegToClient_WhenWebPUploaded() throws Exception {
    byte[] webp = TestFixtures.read("fixtures/webp-fixture.webp");
    when(plantNetClient.recognize(any(RecognitionRequest.class)))
        .thenReturn(resultWithHit("Ajuga reptans L."));

    mockMvc
        .perform(
            multipart("/api/v1/plantrecognition/recognize")
                .file(new MockMultipartFile("file", "plant.webp", "image/webp", webp))
                .param("format", "image/webp"))
        .andExpect(status().isOk());

    ArgumentCaptor<RecognitionRequest> captor = ArgumentCaptor.forClass(RecognitionRequest.class);
    verify(plantNetClient).recognize(captor.capture());
    byte[] forwarded = captor.getValue().getImage();
    assertEquals((byte) 0xFF, forwarded[0]);
    assertEquals((byte) 0xD8, forwarded[1]);
    assertEquals("image/jpeg", captor.getValue().getFormat());
  }

  /** Converts a base64-encoded WebP to JPEG and tells the client the effective format. */
  @Test
  void testRecognize_PassesConvertedJpegToClient_WhenWebPBase64Provided() throws Exception {
    byte[] webp = TestFixtures.read("fixtures/webp-fixture.webp");
    when(plantNetClient.recognize(any(RecognitionRequest.class)))
        .thenReturn(resultWithHit("Ajuga reptans L."));

    mockMvc
        .perform(
            post("/api/v1/plantrecognition/recognize")
                .contentType(MediaType.APPLICATION_JSON)
                .param("base64Image", Base64.getEncoder().encodeToString(webp))
                .param("format", "image/webp"))
        .andExpect(status().isOk());

    ArgumentCaptor<RecognitionRequest> captor = ArgumentCaptor.forClass(RecognitionRequest.class);
    verify(plantNetClient).recognize(captor.capture());
    byte[] forwarded = captor.getValue().getImage();
    assertEquals((byte) 0xFF, forwarded[0]);
    assertEquals((byte) 0xD8, forwarded[1]);
    assertEquals("image/jpeg", captor.getValue().getFormat());
  }

  /** Rejects requests that carry neither a file nor base64 data. */
  @Test
  void testRecognize_Returns400_WhenNoImageProvided() throws Exception {
    mockMvc
        .perform(post("/api/v1/plantrecognition/recognize").contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").exists());

    verify(plantNetClient, never()).recognize(any(RecognitionRequest.class));
  }

  /** Rejects malformed base64 input with a 400 response. */
  @Test
  void testRecognize_Returns400_WhenBase64IsInvalid() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/plantrecognition/recognize")
                .contentType(MediaType.APPLICATION_JSON)
                .param("base64Image", "not-valid-base64!!"))
        .andExpect(status().isBadRequest());

    verify(plantNetClient, never()).recognize(any(RecognitionRequest.class));
  }

  /** Forwards the upstream rate-limit status to the API consumer. */
  @Test
  void testRecognize_Returns429_WhenPlantNetIsRateLimited() throws Exception {
    when(plantNetClient.recognize(any(RecognitionRequest.class)))
        .thenThrow(new PlantNetApiException(429, "too many requests"));

    mockMvc
        .perform(
            post("/api/v1/plantrecognition/recognize")
                .contentType(MediaType.APPLICATION_JSON)
                .param("base64Image", Base64.getEncoder().encodeToString(JPEG_BYTES)))
        .andExpect(status().isTooManyRequests())
        .andExpect(
            jsonPath("$.error")
                .value("PlantNet API rate limit exceeded. Please wait a moment and try again."));
  }

  /** Rejects the upload with the quota message when the rate limiter rejects it. */
  @Test
  void testRecognize_Returns429_WhenUploadLimitExceeded() throws Exception {
    doThrow(
            new UploadLimitExceededException(
                "Free tier limit of 5 photo uploads per month reached. "
                    + "Upgrade to premium for unlimited uploads."))
        .when(uploadRateLimitService)
        .recordUpload(any(UserTier.class), anyLong());

    mockMvc
        .perform(
            post("/api/v1/plantrecognition/recognize")
                .contentType(MediaType.APPLICATION_JSON)
                .param("base64Image", Base64.getEncoder().encodeToString(JPEG_BYTES)))
        .andExpect(status().isTooManyRequests())
        .andExpect(
            jsonPath("$.error")
                .value(
                    "Free tier limit of 5 photo uploads per month reached. "
                        + "Upgrade to premium for unlimited uploads."));

    verify(plantNetClient, never()).recognize(any(RecognitionRequest.class));
  }

  /** Shapes the result for a free-tier user and records the upload against their quota. */
  @Test
  void testRecognize_ShapesBasicResult_WhenFreeTierUser() throws Exception {
    User freeUser =
        User.create("tester", "tester@example.com", "hash", "Test", "User", UserTier.FREE);
    freeUser.setId(1L);
    when(userService.currentUser(new PeonyUserDetails(1L, "tester", "hash"))).thenReturn(freeUser);
    when(plantNetClient.recognize(any(RecognitionRequest.class)))
        .thenReturn(fullResultWithHit("Ajuga reptans L."));

    mockMvc
        .perform(
            post("/api/v1/plantrecognition/recognize")
                .contentType(MediaType.APPLICATION_JSON)
                .param("base64Image", Base64.getEncoder().encodeToString(JPEG_BYTES)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.tier").value("free"))
        .andExpect(jsonPath("$.hits.length()").value(1))
        .andExpect(jsonPath("$.hits[0].species.scientificName").value("Ajuga reptans L."))
        .andExpect(jsonPath("$.hits[0].species.genus").doesNotExist())
        .andExpect(jsonPath("$.hits[0].species.family").doesNotExist())
        .andExpect(jsonPath("$.hits[0].gbif").doesNotExist())
        .andExpect(jsonPath("$.hits[0].powo").doesNotExist())
        .andExpect(jsonPath("$.remaining_identification_requests").doesNotExist());

    verify(uploadRateLimitService).recordUpload(UserTier.FREE, 1L);
  }

  /** Maps upstream server errors and network failures to a 502 response. */
  @Test
  void testRecognize_Returns502_WhenPlantNetIsUnavailable() throws Exception {
    when(plantNetClient.recognize(any(RecognitionRequest.class)))
        .thenThrow(new PlantNetApiException(503, "maintenance"));

    mockMvc
        .perform(
            post("/api/v1/plantrecognition/recognize")
                .contentType(MediaType.APPLICATION_JSON)
                .param("base64Image", Base64.getEncoder().encodeToString(JPEG_BYTES)))
        .andExpect(status().isBadGateway())
        .andExpect(
            jsonPath("$.error")
                .value("PlantNet API is temporarily unavailable. Please try again later."));
  }

  /** Maps network failures while calling PlantNet to a 502 response. */
  @Test
  void testRecognize_Returns502_WhenNetworkFails() throws Exception {
    when(plantNetClient.recognize(any(RecognitionRequest.class)))
        .thenThrow(new IOException("connection refused"));

    mockMvc
        .perform(
            post("/api/v1/plantrecognition/recognize")
                .contentType(MediaType.APPLICATION_JSON)
                .param("base64Image", Base64.getEncoder().encodeToString(JPEG_BYTES)))
        .andExpect(status().isBadGateway())
        .andExpect(
            jsonPath("$.error").value("PlantNet API is unreachable. Please try again later."));
  }

  /** Maps undecodable images to a 415 response. */
  @Test
  void testRecognize_Returns415_WhenImageCannotBeDecoded() throws Exception {
    when(plantNetClient.recognize(any(RecognitionRequest.class)))
        .thenThrow(new UnsupportedImageFormatException("bad image"));

    mockMvc
        .perform(
            post("/api/v1/plantrecognition/recognize")
                .contentType(MediaType.APPLICATION_JSON)
                .param("base64Image", Base64.getEncoder().encodeToString(JPEG_BYTES)))
        .andExpect(status().isUnsupportedMediaType())
        .andExpect(jsonPath("$.error").value("bad image"));
  }

  private PlantRecognitionResult resultWithHit(String scientificName) {
    var hit = new HitResponse();
    hit.setScore(0.9);
    var species = new Species();
    species.setScientificName(scientificName);
    hit.setSpecies(species);
    var result = new PlantRecognitionResult();
    result.setHits(List.of(hit));
    return result;
  }

  /** A fully populated result, as the PlantNet API returns for premium-tier recognition. */
  private PlantRecognitionResult fullResultWithHit(String scientificName) {
    var result = new PlantRecognitionResult();
    result.setApiVersion("2025-01-17");
    result.setRemainingIdentificationRequests(42);
    var hit = new HitResponse();
    hit.setScore(0.9);
    var species = new Species();
    species.setScientificName(scientificName);
    hit.setSpecies(species);
    var gbif = new Gbif();
    gbif.setId("2868390");
    hit.setGbif(gbif);
    var powo = new Powo();
    powo.setId("30010088-2");
    hit.setPowo(powo);
    result.setHits(List.of(hit));
    return result;
  }
}
