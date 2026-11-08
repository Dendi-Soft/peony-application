package com.dendi.peony.controller.plantrecognition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dendi.peony.TestImages;
import com.dendi.peony.TestcontainersConfiguration;
import com.dendi.peony.api.model.HitResponse;
import com.dendi.peony.api.model.PlantRecognitionResult;
import com.dendi.peony.api.model.RecognitionRequest;
import com.dendi.peony.api.model.Species;
import com.dendi.peony.entity.Plant;
import com.dendi.peony.entity.PlantImage;
import com.dendi.peony.entity.PlantImageSource;
import com.dendi.peony.repository.PlantImageRepository;
import com.dendi.peony.repository.PlantNetSpeciesImageRepository;
import com.dendi.peony.repository.PlantRepository;
import com.dendi.peony.service.plant.PlantNetImageDownloadClient;
import com.dendi.peony.service.plantrecognition.License;
import com.dendi.peony.service.plantrecognition.PlantNetClient;
import com.dendi.peony.service.plantrecognition.RecognitionOutcome;
import com.dendi.peony.service.plantrecognition.RelatedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.ObjectMapper;

/**
 * End-to-end test of the catalogue image fill at the recognition seam (issue #54): viewing care
 * details straight after a scan must show the PlantNet fallback, not only after the user adds the
 * plant.
 *
 * <p>Recognition stages a related-image candidate, creates the scan catalogue row, and fills its
 * image before returning. The detail endpoint therefore carries the position-0 credit on the very
 * first request, and the thumb endpoint serves the stored JPEG. A failed download changes nothing:
 * the recognition response is unaffected and the species keeps the leaf fallback.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class PlantRecognitionCatalogueImageTest {

  private static final String SUCCESS_SPECIES = "Caladium t54e2e";

  private static final String FAILURE_SPECIES = "Caladium t54fail";

  private static final String NO_CANDIDATE_SPECIES = "Caladium t54none";

  private static final byte[] JPEG_BYTES = new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 1};

  private static final ObjectMapper JSON = new ObjectMapper();

  @TempDir static Path imageDir;

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("peony.plant-images.dir", imageDir::toString);
  }

  @MockitoBean private PlantNetClient plantNetClient;

  @MockitoBean private PlantNetImageDownloadClient downloadClient;

  @Autowired private MockMvc mockMvc;

  @Autowired private PlantRepository plantRepository;

  @Autowired private PlantImageRepository plantImageRepository;

  @Autowired private PlantNetSpeciesImageRepository plantNetSpeciesImageRepository;

  @AfterEach
  void cleanUp() throws IOException {
    deleteCatalogueRow(SUCCESS_SPECIES);
    deleteCatalogueRow(FAILURE_SPECIES);
    deleteCatalogueRow(NO_CANDIDATE_SPECIES);
    plantNetSpeciesImageRepository.findAll().stream()
        .filter(
            staged ->
                SUCCESS_SPECIES.equalsIgnoreCase(staged.getScientificName())
                    || FAILURE_SPECIES.equalsIgnoreCase(staged.getScientificName())
                    || NO_CANDIDATE_SPECIES.equalsIgnoreCase(staged.getScientificName()))
        .forEach(plantNetSpeciesImageRepository::delete);
    try (Stream<Path> files = Files.list(imageDir)) {
      for (Path file : files.toList()) {
        Files.deleteIfExists(file);
      }
    }
  }

  /**
   * Scanning a species that is not in the catalogue fills its image during recognition, so the
   * first care-details fetch carries the position-0 PlantNet credit and the thumb serves the JPEG.
   */
  @Test
  void testRecognize_FillsCatalogueImage_WhenSpeciesIsNotInCatalogue() throws Exception {
    stubRecognition(SUCCESS_SPECIES, "caladium t54e2e");
    when(downloadClient.download(anyString())).thenReturn(TestImages.jpegOf(640, 480));
    String token = signupAndSignin("t54-catalogue-image");

    mockMvc
        .perform(recognizeRequest(token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.hits[0].catalogDraft.scientificName").value(SUCCESS_SPECIES));

    Plant plant = plantRepository.findByScientificName(SUCCESS_SPECIES).orElseThrow();

    mockMvc
        .perform(detailRequest(plant.getId(), token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.imageCredits[0].position").value(0))
        .andExpect(jsonPath("$.imageCredits[0].source").value("PLANTNET"))
        .andExpect(jsonPath("$.imageCredits[0].author").value("Jane Doe"))
        .andExpect(jsonPath("$.imageCredits[0].license").value("cc-by-sa"))
        .andExpect(jsonPath("$.imageCredits[0].citation").value("PlantNet citation"));

    PlantImage stored =
        plantImageRepository.findByPlantIdAndPosition(plant.getId(), 0).orElseThrow();
    assertEquals(PlantImageSource.PLANTNET, stored.getSource());
    assertTrue(
        Files.isRegularFile(imageDir.resolve(stored.getFilename())),
        "the fallback must be stored on disk");

    mockMvc
        .perform(
            get("/api/v1/plants/{plantId}/thumb", plant.getId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.IMAGE_JPEG));
  }

  /**
   * A failed download is best effort: recognition still succeeds, no image row is created, and the
   * detail and thumb endpoints keep the leaf fallback behaviour.
   */
  @Test
  void testRecognize_LeavesImageMissing_WhenDownloadFails() throws Exception {
    stubRecognition(FAILURE_SPECIES, "caladium t54fail");
    when(downloadClient.download(anyString())).thenThrow(new IOException("download failed"));
    String token = signupAndSignin("t54-catalogue-image-fail");

    mockMvc
        .perform(recognizeRequest(token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.hits[0].catalogDraft.scientificName").value(FAILURE_SPECIES));

    Plant plant = plantRepository.findByScientificName(FAILURE_SPECIES).orElseThrow();
    assertTrue(
        plantImageRepository.findByPlantIdOrderByPositionAsc(plant.getId()).isEmpty(),
        "a failed fill must not create an image row");

    mockMvc
        .perform(detailRequest(plant.getId(), token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.imageCredits").doesNotExist());

    mockMvc
        .perform(
            get("/api/v1/plants/{plantId}/thumb", plant.getId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isNotFound());
  }

  /**
   * A scan with no acceptable related-image candidate still creates the catalogue row, but leaves
   * it image-less: no image row, no credits, and the thumb endpoint stays on the leaf fallback.
   */
  @Test
  void testRecognize_LeavesCatalogueImageMissing_WhenNoCandidateStaged() throws Exception {
    stubRecognition(NO_CANDIDATE_SPECIES);
    String token = signupAndSignin("t54-catalogue-image-none");

    mockMvc
        .perform(recognizeRequest(token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.hits[0].catalogDraft.scientificName").value(NO_CANDIDATE_SPECIES));

    Plant plant = plantRepository.findByScientificName(NO_CANDIDATE_SPECIES).orElseThrow();
    assertTrue(
        plantImageRepository.findByPlantIdOrderByPositionAsc(plant.getId()).isEmpty(),
        "no staged candidate means no image row");

    mockMvc
        .perform(detailRequest(plant.getId(), token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.imageCredits").doesNotExist());

    mockMvc
        .perform(
            get("/api/v1/plants/{plantId}/thumb", plant.getId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isNotFound());
  }

  /** Stubs the identify call with one confident hit and one staged habit-image candidate. */
  private void stubRecognition(String scientificName, String stagedName) throws IOException {
    RelatedImage candidate =
        new RelatedImage(
            stagedName,
            "habit",
            "Jane Doe",
            License.CC_BY_SA,
            "PlantNet citation",
            "https://bs.plantnet.org/v2/image/medium.jpg");
    when(plantNetClient.recognizeWithRelatedImages(any(RecognitionRequest.class)))
        .thenReturn(new RecognitionOutcome(recognitionResult(scientificName), List.of(candidate)));
  }

  /** Stubs the identify call with one confident hit and no acceptable related-image candidates. */
  private void stubRecognition(String scientificName) throws IOException {
    when(plantNetClient.recognizeWithRelatedImages(any(RecognitionRequest.class)))
        .thenReturn(new RecognitionOutcome(recognitionResult(scientificName), List.of()));
  }

  private static PlantRecognitionResult recognitionResult(String scientificName) {
    Species species = new Species();
    species.setScientificName(scientificName + " L.");
    species.setScientificNameWithoutAuthor(scientificName);
    species.setCommonNames(List.of("Heart of Jesus"));

    HitResponse hit = new HitResponse();
    hit.setScore(0.9);
    hit.setSpecies(species);

    PlantRecognitionResult result = new PlantRecognitionResult();
    result.setApiVersion("2025-01-17");
    result.setHits(List.of(hit));
    return result;
  }

  private MockHttpServletRequestBuilder recognizeRequest(String token) {
    return post("/api/v1/plantrecognition/recognize")
        .contentType(MediaType.APPLICATION_JSON_VALUE)
        .param("base64Image", Base64.getEncoder().encodeToString(JPEG_BYTES))
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
  }

  private static MockHttpServletRequestBuilder detailRequest(long plantId, String token) {
    return get("/api/v1/plants/{plantId}", plantId)
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
  }

  private void deleteCatalogueRow(String scientificName) {
    plantRepository
        .findByScientificName(scientificName)
        .ifPresent(
            plant -> {
              plantImageRepository.deleteAll(
                  plantImageRepository.findByPlantIdOrderByPositionAsc(plant.getId()));
              plantRepository.delete(plant);
            });
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
