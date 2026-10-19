package com.dendi.peony.controller.plant;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dendi.peony.TestcontainersConfiguration;
import com.dendi.peony.entity.Plant;
import com.dendi.peony.entity.PlantImage;
import com.dendi.peony.repository.PlantImageRepository;
import com.dendi.peony.repository.PlantRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Tests for the plant image serving endpoints at the REST seam, against a real PostgreSQL database:
 * the thumbnail (position 0) and gallery images by 0-based position, with content types derived
 * from the stored file names.
 *
 * <p>The image directory is redirected to a per-run temporary directory via {@code
 * peony.plant-images.dir}; image rows are seeded directly in the database and the matching files
 * written to disk.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class PlantImagesControllerTest {

  /** Temp image directory, created before the application context reads the property. */
  private static final Path IMAGE_DIR = createImageDir();

  private static Path createImageDir() {
    try {
      return Files.createTempDirectory("peony-plant-images").toRealPath();
    } catch (IOException e) {
      throw new IllegalStateException("Cannot create the temp image directory", e);
    }
  }

  @DynamicPropertySource
  static void imageDir(DynamicPropertyRegistry registry) {
    registry.add("peony.plant-images.dir", () -> IMAGE_DIR.toString());
  }

  @Autowired private MockMvc mockMvc;

  @Autowired private PlantRepository plantRepository;

  @Autowired private PlantImageRepository plantImageRepository;

  /** Image routes are protected: requests without a session are rejected. */
  @Test
  void testGetThumb_Returns401_WhenNoSession() throws Exception {
    mockMvc.perform(get("/api/v1/plants/1/thumb")).andExpect(status().isUnauthorized());
  }

  /** Image routes are protected: requests without a session are rejected. */
  @Test
  void testGetGalleryImage_Returns401_WhenNoSession() throws Exception {
    mockMvc.perform(get("/api/v1/plants/1/gallery/0")).andExpect(status().isUnauthorized());
  }

  /** The thumbnail serves the position-0 file with a content type from its extension. */
  @Test
  void testGetThumb_ReturnsImageBytes_WhenImageAtPositionZero() throws Exception {
    MockHttpSession session = signupAndSignin("t10-img-thumb-1");
    long plantId = seedPlant("Ficus t10 img thumb", "Ficus t10 img thumb");
    byte[] png = new byte[] {(byte) 0x89, 'P', 'N', 'G', 1, 2, 3, 4};
    Files.write(IMAGE_DIR.resolve("thumb-t10.png"), png);
    seedImage(plantId, 0, "thumb-t10.png");

    MvcResult result =
        mockMvc
            .perform(get("/api/v1/plants/" + plantId + "/thumb").session(session))
            .andExpect(status().isOk())
            .andReturn();
    assertEquals("image/png", result.getResponse().getContentType());
    assertArrayEquals(png, result.getResponse().getContentAsByteArray());
  }

  /** A plant without images gets a 404 for the thumbnail. */
  @Test
  void testGetThumb_Returns404_WhenPlantHasNoImages() throws Exception {
    MockHttpSession session = signupAndSignin("t10-img-thumb-none");
    long plantId = seedPlant("Ficus t10 img none", "Ficus t10 img none");

    mockMvc
        .perform(get("/api/v1/plants/" + plantId + "/thumb").session(session))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error").exists());
  }

  /** The thumbnail of an unknown plant is a 404. */
  @Test
  void testGetThumb_Returns404_WhenPlantNotFound() throws Exception {
    MockHttpSession session = signupAndSignin("t10-img-thumb-404");

    mockMvc
        .perform(get("/api/v1/plants/9999999/thumb").session(session))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error").exists());
  }

  /** Gallery images are addressed by 0-based position with a content type from the extension. */
  @Test
  void testGetGalleryImage_ReturnsImageBytes_WhenPositionExists() throws Exception {
    MockHttpSession session = signupAndSignin("t10-img-gallery-1");
    long plantId = seedPlant("Ficus t10 img gallery", "Ficus t10 img gallery");
    byte[] jpeg = new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 9, 8, 7};
    Files.write(IMAGE_DIR.resolve("gallery1-t10.jpg"), jpeg);
    seedImage(plantId, 0, "missing-thumb-t10.jpg");
    seedImage(plantId, 1, "gallery1-t10.jpg");

    MvcResult result =
        mockMvc
            .perform(get("/api/v1/plants/" + plantId + "/gallery/1").session(session))
            .andExpect(status().isOk())
            .andReturn();
    assertEquals("image/jpeg", result.getResponse().getContentType());
    assertArrayEquals(jpeg, result.getResponse().getContentAsByteArray());
  }

  /** A gallery position without an image row is a 404. */
  @Test
  void testGetGalleryImage_Returns404_WhenPositionOutOfRange() throws Exception {
    MockHttpSession session = signupAndSignin("t10-img-gallery-oob");
    long plantId = seedPlant("Ficus t10 img oob", "Ficus t10 img oob");
    byte[] png = new byte[] {(byte) 0x89, 'P', 'N', 'G'};
    Files.write(IMAGE_DIR.resolve("oob-t10.png"), png);
    seedImage(plantId, 0, "oob-t10.png");

    mockMvc
        .perform(get("/api/v1/plants/" + plantId + "/gallery/7").session(session))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error").exists());
  }

  /** An image row whose file is missing on disk is a 404. */
  @Test
  void testGetGalleryImage_Returns404_WhenFileMissingOnDisk() throws Exception {
    MockHttpSession session = signupAndSignin("t10-img-gallery-missing");
    long plantId = seedPlant("Ficus t10 img missing", "Ficus t10 img missing");
    seedImage(plantId, 0, "never-written-t10.jpg");

    mockMvc
        .perform(get("/api/v1/plants/" + plantId + "/gallery/0").session(session))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error").exists());
  }

  /** Seeds a catalog plant directly in the database and returns its id. */
  private long seedPlant(String name, String scientificName) {
    Plant plant = new Plant();
    plant.setName(name);
    plant.setScientificName(scientificName);
    plant.setDescription("seeded for testing");
    plant.setWateringFrequency(7);
    plant.setCreatedAt(LocalDateTime.now());
    return plantRepository.save(plant).getId();
  }

  /** Seeds an image row mapping the plant to a file name at the given position. */
  private void seedImage(long plantId, int position, String filename) {
    PlantImage image = new PlantImage();
    image.setPlantId(plantId);
    image.setPosition(position);
    image.setFilename(filename);
    image.setCreatedAt(LocalDateTime.now());
    plantImageRepository.save(image);
  }

  private MockHttpSession signupAndSignin(String username) throws Exception {
    mockMvc
        .perform(
            post("/api/v1/auth/signup")
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .content(
                    """
                    {"username":"%s","email":"%s@example.com","password":"supersecret1","firstName":"Test","lastName":"User"}"""
                        .formatted(username, username)))
        .andExpect(status().isCreated());

    MockHttpSession session = new MockHttpSession();
    mockMvc
        .perform(
            post("/api/v1/auth/signin")
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
