package com.dendi.peony.controller.plant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dendi.peony.TestImages;
import com.dendi.peony.TestcontainersConfiguration;
import com.dendi.peony.entity.PlantImage;
import com.dendi.peony.entity.PlantImageSource;
import com.dendi.peony.entity.PlantNetSpeciesImage;
import com.dendi.peony.repository.PlantImageRepository;
import com.dendi.peony.repository.PlantNetSpeciesImageRepository;
import com.dendi.peony.repository.PlantRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okio.Buffer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * End-to-end test of the PlantNet species-image fill at the scan REST seam (issue #45): a kept scan
 * downloads a staged candidate, stores a 256 px position-0 fallback, and returns the individual
 * with the PlantNet credit. The download origin is a local MockWebServer, so no live PlantNet call
 * is made and no API key is ever attached.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class PlantScanImageFillTest {

  private static final ObjectMapper JSON = new ObjectMapper();

  private static final MockWebServer PLANTNET_IMAGES = startServer();

  /** Temp image directory for the downloaded fallbacks. */
  private static final Path IMAGE_DIR = createImageDir();

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("peony.plantnet-image.allowed-origin", () -> allowedOrigin());
    registry.add("peony.plant-images.dir", IMAGE_DIR::toString);
  }

  @Autowired private MockMvc mockMvc;

  @Autowired private PlantRepository plantRepository;

  @Autowired private PlantImageRepository plantImageRepository;

  @Autowired private PlantNetSpeciesImageRepository plantNetSpeciesImageRepository;

  @AfterAll
  static void shutdownServer() throws IOException {
    PLANTNET_IMAGES.shutdown();
  }

  /**
   * A scan with a fresh staged candidate reports 201 with the individual's personal-photo fields
   * unchanged and the position-0 PlantNet credit; the downloaded JPEG is stored and the credit
   * fields are copied onto the catalogue row.
   */
  @Test
  void testCreatePlantFromScan_ReturnsFilledImageCredit_WhenStagedCandidateIsDownloadable()
      throws Exception {
    String scientificName = "Caladium t45 scan fill";
    PlantNetSpeciesImage staged = new PlantNetSpeciesImage();
    staged.setScientificName("caladium t45 scan fill");
    staged.setImageUrl(PLANTNET_IMAGES.url("/img/medium.jpg").toString());
    staged.setOrgan("leaf");
    staged.setAuthor("Jane Doe");
    staged.setLicense("cc-by-sa");
    staged.setCitation("PlantNet citation");
    staged.setStagedAt(LocalDateTime.now());
    plantNetSpeciesImageRepository.save(staged);

    byte[] jpeg = TestImages.jpegOf(640, 480);
    PLANTNET_IMAGES.enqueue(
        new MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "image/jpeg")
            .setBody(new Buffer().write(jpeg)));

    String token = signupAndSignin("t45-scan-fill");

    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/plants/from-scan")
                    .contentType(MediaType.APPLICATION_JSON_VALUE)
                    .content("{\"scientificName\":\"" + scientificName + "\"}")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.scientificName").value(scientificName))
            .andExpect(jsonPath("$.hasImage").value(false))
            .andExpect(jsonPath("$.imageCredit.position").value(0))
            .andExpect(jsonPath("$.imageCredit.source").value("PLANTNET"))
            .andExpect(jsonPath("$.imageCredit.author").value("Jane Doe"))
            .andExpect(jsonPath("$.imageCredit.license").value("cc-by-sa"))
            .andExpect(jsonPath("$.imageCredit.citation").value("PlantNet citation"))
            .andExpect(jsonPath("$.imageCredits").doesNotExist())
            .andReturn();

    long plantId =
        JSON.readTree(result.getResponse().getContentAsString()).path("plantId").asLong();
    assertEquals(
        plantRepository.findByScientificName(scientificName).orElseThrow().getId(),
        plantId,
        "the response must carry the catalogue plant id");

    PlantImage stored = plantImageRepository.findByPlantIdAndPosition(plantId, 0).orElseThrow();
    assertEquals(PlantImageSource.PLANTNET, stored.getSource());
    assertEquals("Jane Doe", stored.getAuthor());
    assertEquals("cc-by-sa", stored.getLicense());
    assertEquals("PlantNet citation", stored.getCitation());
    assertTrue(
        Files.isRegularFile(IMAGE_DIR.resolve(stored.getFilename())),
        "the processed fallback must be stored on disk");

    assertTrue(
        plantNetSpeciesImageRepository.findById(staged.getId()).isEmpty(),
        "a successful fill must consume the staged candidate");

    RecordedRequest download = PLANTNET_IMAGES.takeRequest(5, TimeUnit.SECONDS);
    assertNotNull(download, "the fill must download the staged image");
    assertEquals("/img/medium.jpg", download.getPath());
    assertNull(download.getHeader("Api-Key"), "image downloads must carry no API key");
  }

  private static String allowedOrigin() {
    String url = PLANTNET_IMAGES.url("/").toString();
    return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
  }

  private static MockWebServer startServer() {
    MockWebServer server = new MockWebServer();
    try {
      server.start();
    } catch (IOException exception) {
      throw new IllegalStateException("Cannot start the PlantNet image stub", exception);
    }
    return server;
  }

  private static Path createImageDir() {
    try {
      return Files.createTempDirectory("peony-scan-fill").toRealPath();
    } catch (IOException exception) {
      throw new IllegalStateException("Cannot create the temp image directory", exception);
    }
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
