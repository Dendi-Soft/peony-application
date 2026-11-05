package com.dendi.peony.controller.plant;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dendi.peony.TestImages;
import com.dendi.peony.TestcontainersConfiguration;
import com.dendi.peony.entity.Plant;
import com.dendi.peony.entity.UserPlant;
import com.dendi.peony.entity.UserPlantImage;
import com.dendi.peony.repository.PlantRepository;
import com.dendi.peony.repository.UserPlantImageRepository;
import com.dendi.peony.repository.UserPlantRepository;
import com.dendi.peony.repository.UserRepository;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Tests for the per-individual photo endpoints (issue #44) at the REST seam, against a real
 * PostgreSQL database and a temporary image directory: owner-scoped upload, both serve sizes, the
 * 409 retry contract, validation errors, and the delete lifecycle.
 *
 * <p>The image directory is redirected to a per-run temporary directory via {@code
 * peony.user-images.dir}. Tests share one database, so each test uses its own account.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class MePlantImagesControllerTest {

  private static final ObjectMapper JSON = new ObjectMapper();

  private static final int MAX_UPLOAD_BYTES = 1_048_576;

  /** Temp image directory, created before the application context reads the property. */
  private static final Path IMAGE_DIR = createImageDir();

  private static Path createImageDir() {
    try {
      return Files.createTempDirectory("peony-user-images").toRealPath();
    } catch (IOException e) {
      throw new IllegalStateException("Cannot create the temp image directory", e);
    }
  }

  @DynamicPropertySource
  static void imageDir(DynamicPropertyRegistry registry) {
    registry.add("peony.user-images.dir", () -> IMAGE_DIR.toString());
  }

  @Autowired private MockMvc mockMvc;

  @Autowired private PlantRepository plantRepository;

  @Autowired private UserPlantRepository userPlantRepository;

  @Autowired private UserPlantImageRepository userPlantImageRepository;

  @Autowired private UserRepository userRepository;

  /** Upload and both serve routes require a bearer token. */
  @Test
  void testUploadImage_Returns401_WhenNoBearer() throws Exception {
    mockMvc
        .perform(
            multipart("/api/v1/me/plants/1/image")
                .file(
                    new MockMultipartFile(
                        "file", "photo.jpg", "image/jpeg", TestImages.jpegOf(100, 80))))
        .andExpect(status().isUnauthorized());
  }

  /** Both serve routes require a bearer token. */
  @Test
  void testGetImage_Returns401_WhenNoBearer() throws Exception {
    mockMvc.perform(get("/api/v1/me/plants/1/image")).andExpect(status().isUnauthorized());
    mockMvc.perform(get("/api/v1/me/plants/1/image/thumb")).andExpect(status().isUnauthorized());
  }

  /**
   * A valid JPEG upload stores the row and both files, reports hasImage/imageId, and the display is
   * bounded at 1280 px while the thumbnail is bounded at 256 px.
   */
  @Test
  void testUploadImage_StoresDisplayAndThumbnail_WhenJpegWithinLimit() throws Exception {
    String token = signupAndSignin("t44-upload-ok");
    UserPlant individual = seedIndividual("t44-upload-ok", "Monstera t44 upload ok");

    MvcResult result =
        mockMvc
            .perform(uploadRequest(individual.getId(), TestImages.jpegOf(1600, 1200), token))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.hasImage").value(true))
            .andExpect(jsonPath("$.imageId").exists())
            .andReturn();

    UUID imageId =
        UUID.fromString(
            JSON.readTree(result.getResponse().getContentAsString()).path("imageId").asText());
    UserPlantImage image =
        userPlantImageRepository.findByUserPlantId(individual.getId()).orElseThrow();
    assertEquals(imageId, image.getImageId());
    assertEquals(imageId + ".jpg", image.getFilename());
    assertEquals(imageId + "-thumb.jpg", image.getThumbFilename());
    assertEquals(1280, image.getWidth());
    assertEquals(960, image.getHeight());

    Path display = IMAGE_DIR.resolve(image.getFilename());
    Path thumb = IMAGE_DIR.resolve(image.getThumbFilename());
    assertTrue(Files.isRegularFile(display), "the display file must be written");
    assertTrue(Files.isRegularFile(thumb), "the thumbnail file must be written");
    assertEquals(Files.size(display), image.getByteSize());
    assertEquals(1280, decode(display).getWidth());
    assertEquals(960, decode(display).getHeight());
    assertEquals(256, decode(thumb).getWidth());
    assertEquals(192, decode(thumb).getHeight());
  }

  /**
   * Precondition: the individual already has a photo. A second upload is a 409 and leaves both the
   * stored row and files untouched, so the client can treat the response as success.
   */
  @Test
  void testUploadImage_Returns409AndKeepsImage_WhenIndividualAlreadyHasImage() throws Exception {
    String token = signupAndSignin("t44-upload-duplicate");
    UserPlant individual = seedIndividual("t44-upload-duplicate", "Monstera t44 duplicate");
    mockMvc
        .perform(uploadRequest(individual.getId(), TestImages.jpegOf(400, 300), token))
        .andExpect(status().isCreated());
    UserPlantImage first =
        userPlantImageRepository.findByUserPlantId(individual.getId()).orElseThrow();
    byte[] firstDisplay = Files.readAllBytes(IMAGE_DIR.resolve(first.getFilename()));
    byte[] firstThumb = Files.readAllBytes(IMAGE_DIR.resolve(first.getThumbFilename()));
    Set<String> filesBefore = listFileNames();

    mockMvc
        .perform(uploadRequest(individual.getId(), TestImages.jpegOf(500, 500), token))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error").value("Image already exists"));

    UserPlantImage after =
        userPlantImageRepository.findByUserPlantId(individual.getId()).orElseThrow();
    assertEquals(first.getImageId(), after.getImageId(), "the original image id must survive");
    assertArrayEquals(
        firstDisplay,
        Files.readAllBytes(IMAGE_DIR.resolve(after.getFilename())),
        "the display bytes must be unchanged");
    assertArrayEquals(
        firstThumb,
        Files.readAllBytes(IMAGE_DIR.resolve(after.getThumbFilename())),
        "the thumbnail bytes must be unchanged");
    assertEquals(filesBefore, listFileNames(), "a rejected retry must not leave extra files");
  }

  /**
   * Precondition: the caller is signed in and the individual belongs to another account. The upload
   * is a 404 and writes neither a row nor a file.
   */
  @Test
  void testUploadImage_Returns404_WhenIndividualBelongsToAnotherUser() throws Exception {
    String token = signupAndSignin("t44-upload-foreign-a");
    signupAndSignin("t44-upload-foreign-b");
    UserPlant foreign = seedIndividual("t44-upload-foreign-b", "Monstera t44 foreign upload");
    long filesBefore = countFiles();

    mockMvc
        .perform(uploadRequest(foreign.getId(), TestImages.jpegOf(200, 100), token))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error").value("User plant not found"));

    assertTrue(userPlantImageRepository.findByUserPlantId(foreign.getId()).isEmpty());
    assertEquals(filesBefore, countFiles(), "a rejected upload must not write files");
  }

  /**
   * Precondition: the caller is signed in and the uploaded id matches no individual. The upload is
   * a 404 and stores nothing.
   */
  @Test
  void testUploadImage_Returns404_WhenIndividualUnknown() throws Exception {
    String token = signupAndSignin("t44-upload-unknown");

    mockMvc
        .perform(uploadRequest(9_999_999L, TestImages.jpegOf(200, 100), token))
        .andExpect(status().isNotFound());
  }

  /**
   * Precondition: the caller owns the individual and uploads PNG bytes. Only JPEG is accepted, so
   * the upload is a 415 and stores neither a row nor a file.
   */
  @Test
  void testUploadImage_Returns415_WhenNotJpeg() throws Exception {
    String token = signupAndSignin("t44-upload-png");
    UserPlant individual = seedIndividual("t44-upload-png", "Monstera t44 upload png");

    mockMvc
        .perform(
            uploadRequest(
                individual.getId(), new byte[] {(byte) 0x89, 'P', 'N', 'G', 1, 2, 3}, token))
        .andExpect(status().isUnsupportedMediaType());

    assertTrue(userPlantImageRepository.findByUserPlantId(individual.getId()).isEmpty());
  }

  /** A JPEG signature with an undecodable body is a 400, not a 415. */
  @Test
  void testUploadImage_Returns400_WhenJpegIsCorrupt() throws Exception {
    String token = signupAndSignin("t44-upload-corrupt");
    UserPlant individual = seedIndividual("t44-upload-corrupt", "Monstera t44 upload corrupt");

    mockMvc
        .perform(
            uploadRequest(
                individual.getId(),
                new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 1, 2, 3, 4, 5},
                token))
        .andExpect(status().isBadRequest());

    assertTrue(userPlantImageRepository.findByUserPlantId(individual.getId()).isEmpty());
  }

  /** An upload over the 1 MiB cap is a 413 and stores nothing. */
  @Test
  void testUploadImage_Returns413_WhenLargerThanOneMebibyte() throws Exception {
    String token = signupAndSignin("t44-upload-large");
    UserPlant individual = seedIndividual("t44-upload-large", "Monstera t44 upload large");
    byte[] oversize = new byte[MAX_UPLOAD_BYTES + 1];

    mockMvc
        .perform(uploadRequest(individual.getId(), oversize, token))
        .andExpect(status().isPayloadTooLarge());

    assertTrue(userPlantImageRepository.findByUserPlantId(individual.getId()).isEmpty());
  }

  /** The display route serves the stored display bytes privately with the JPEG content type. */
  @Test
  void testGetImage_ReturnsDisplayBytes_WhenCallerOwnsTheIndividual() throws Exception {
    String token = signupAndSignin("t44-get-own");
    UserPlant individual = seedIndividual("t44-get-own", "Monstera t44 get own");
    mockMvc
        .perform(uploadRequest(individual.getId(), TestImages.jpegOf(1600, 1200), token))
        .andExpect(status().isCreated());
    UserPlantImage image =
        userPlantImageRepository.findByUserPlantId(individual.getId()).orElseThrow();

    MvcResult result =
        mockMvc
            .perform(
                get("/api/v1/me/plants/" + individual.getId() + "/image")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "private"))
            .andReturn();

    assertEquals("image/jpeg", result.getResponse().getContentType());
    assertArrayEquals(
        Files.readAllBytes(IMAGE_DIR.resolve(image.getFilename())),
        result.getResponse().getContentAsByteArray());
  }

  /**
   * Precondition: the caller owns an individual with an uploaded photo. A request carrying the
   * client-side cache-version query parameter (an unrelated UUID, since v1 does not validate it)
   * serves the same stored display and thumbnail bytes as the unversioned URLs.
   */
  @Test
  void testGetImage_IgnoresImageIdQueryParameter_WhenServingBothSizes() throws Exception {
    String token = signupAndSignin("t44-image-id-param");
    UserPlant individual = seedIndividual("t44-image-id-param", "Monstera t44 image id param");
    mockMvc
        .perform(uploadRequest(individual.getId(), TestImages.jpegOf(1600, 1200), token))
        .andExpect(status().isCreated());
    UserPlantImage image =
        userPlantImageRepository.findByUserPlantId(individual.getId()).orElseThrow();
    String cacheVersion = UUID.randomUUID().toString();

    MvcResult display =
        mockMvc
            .perform(
                get("/api/v1/me/plants/" + individual.getId() + "/image")
                    .queryParam("imageId", cacheVersion)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andExpect(status().isOk())
            .andReturn();
    MvcResult thumb =
        mockMvc
            .perform(
                get("/api/v1/me/plants/" + individual.getId() + "/image/thumb")
                    .queryParam("imageId", cacheVersion)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andExpect(status().isOk())
            .andReturn();

    assertArrayEquals(
        Files.readAllBytes(IMAGE_DIR.resolve(image.getFilename())),
        display.getResponse().getContentAsByteArray(),
        "the display route must serve the stored bytes unchanged");
    assertArrayEquals(
        Files.readAllBytes(IMAGE_DIR.resolve(image.getThumbFilename())),
        thumb.getResponse().getContentAsByteArray(),
        "the thumbnail route must serve the stored bytes unchanged");
  }

  /** The thumb route serves the stored thumbnail bytes privately. */
  @Test
  void testGetThumb_ReturnsThumbnailBytes_WhenCallerOwnsTheIndividual() throws Exception {
    String token = signupAndSignin("t44-thumb-own");
    UserPlant individual = seedIndividual("t44-thumb-own", "Monstera t44 thumb own");
    mockMvc
        .perform(uploadRequest(individual.getId(), TestImages.jpegOf(1600, 1200), token))
        .andExpect(status().isCreated());
    UserPlantImage image =
        userPlantImageRepository.findByUserPlantId(individual.getId()).orElseThrow();

    MvcResult result =
        mockMvc
            .perform(
                get("/api/v1/me/plants/" + individual.getId() + "/image/thumb")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "private"))
            .andReturn();

    assertEquals("image/jpeg", result.getResponse().getContentType());
    assertArrayEquals(
        Files.readAllBytes(IMAGE_DIR.resolve(image.getThumbFilename())),
        result.getResponse().getContentAsByteArray());
  }

  /** Another user's individual is a 404 on both serve routes, never a 403. */
  @Test
  void testGetImage_Returns404_WhenIndividualBelongsToAnotherUser() throws Exception {
    String token = signupAndSignin("t44-get-foreign-a");
    String otherToken = signupAndSignin("t44-get-foreign-b");
    UserPlant foreign = seedIndividual("t44-get-foreign-b", "Monstera t44 foreign get");
    mockMvc
        .perform(uploadRequest(foreign.getId(), TestImages.jpegOf(200, 100), otherToken))
        .andExpect(status().isCreated());

    mockMvc
        .perform(
            get("/api/v1/me/plants/" + foreign.getId() + "/image")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error").value("User plant not found"));
    mockMvc
        .perform(
            get("/api/v1/me/plants/" + foreign.getId() + "/image/thumb")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error").value("User plant not found"));
  }

  /**
   * Precondition: the caller owns an individual that has no photo. The display route is a 404 with
   * the image-not-found envelope.
   */
  @Test
  void testGetImage_Returns404_WhenIndividualHasNoImage() throws Exception {
    String token = signupAndSignin("t44-get-none");
    UserPlant individual = seedIndividual("t44-get-none", "Monstera t44 get none");

    mockMvc
        .perform(
            get("/api/v1/me/plants/" + individual.getId() + "/image")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error").value("Image not found"));
  }

  /**
   * Precondition: the individual has an image row whose bytes were never written to disk. Both
   * serve routes behave as not found instead of failing.
   */
  @Test
  void testGetImage_Returns404_WhenFileMissingOnDisk() throws Exception {
    String token = signupAndSignin("t44-get-missing-file");
    UserPlant individual = seedIndividual("t44-get-missing-file", "Monstera t44 missing file");
    UUID imageId = UUID.randomUUID();
    seedImageRow(individual.getId(), imageId);

    mockMvc
        .perform(
            get("/api/v1/me/plants/" + individual.getId() + "/image")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(
            get("/api/v1/me/plants/" + individual.getId() + "/image/thumb")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isNotFound());
  }

  /** Deleting the individual removes the image row and both files in the same request. */
  @Test
  void testDeleteMyPlant_RemovesImageRowAndFiles_WhenIndividualHasImage() throws Exception {
    String token = signupAndSignin("t44-delete-image");
    UserPlant individual = seedIndividual("t44-delete-image", "Monstera t44 delete image");
    mockMvc
        .perform(uploadRequest(individual.getId(), TestImages.jpegOf(400, 300), token))
        .andExpect(status().isCreated());
    UserPlantImage image =
        userPlantImageRepository.findByUserPlantId(individual.getId()).orElseThrow();
    Path display = IMAGE_DIR.resolve(image.getFilename());
    Path thumb = IMAGE_DIR.resolve(image.getThumbFilename());
    assertTrue(Files.isRegularFile(display));

    mockMvc
        .perform(
            delete("/api/v1/me/plants/" + individual.getId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isNoContent());

    assertTrue(userPlantImageRepository.findByUserPlantId(individual.getId()).isEmpty());
    assertFalse(Files.exists(display), "the display file must be deleted with the individual");
    assertFalse(Files.exists(thumb), "the thumbnail file must be deleted with the individual");
  }

  /** A stored image keeps its immutable id across listing fetches so cached URLs stay valid. */
  @Test
  void testListMyPlants_ReturnsStableImageId_WhenImageUploaded() throws Exception {
    String token = signupAndSignin("t44-list-stable");
    UserPlant individual = seedIndividual("t44-list-stable", "Monstera t44 list stable");
    MvcResult upload =
        mockMvc
            .perform(uploadRequest(individual.getId(), TestImages.jpegOf(400, 300), token))
            .andExpect(status().isCreated())
            .andReturn();
    String uploadedId =
        JSON.readTree(upload.getResponse().getContentAsString()).path("imageId").asText();

    MvcResult list =
        mockMvc
            .perform(get("/api/v1/me/plants").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andExpect(status().isOk())
            .andReturn();
    JsonNode row = JSON.readTree(list.getResponse().getContentAsString()).get(0);

    assertEquals(true, row.path("hasImage").asBoolean());
    assertEquals(uploadedId, row.path("imageId").asText());
    assertNotEquals("", uploadedId);
  }

  private MockMultipartHttpServletRequestBuilder uploadRequest(
      Long userPlantId, byte[] bytes, String token) {
    return multipart("/api/v1/me/plants/" + userPlantId + "/image")
        .file(new MockMultipartFile("file", "photo.jpg", "image/jpeg", bytes))
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
  }

  private static BufferedImage decode(Path file) throws IOException {
    BufferedImage image = ImageIO.read(file.toFile());
    assertTrue(image != null, "the stored file must be a decodable JPEG");
    return image;
  }

  private long countFiles() throws IOException {
    try (var files = Files.list(IMAGE_DIR)) {
      return files.count();
    }
  }

  private Set<String> listFileNames() throws IOException {
    try (var files = Files.list(IMAGE_DIR)) {
      return files.map(path -> path.getFileName().toString()).collect(Collectors.toSet());
    }
  }

  /** Seeds a catalog plant and an individual owned by the given account. */
  private UserPlant seedIndividual(String username, String scientificName) {
    Plant plant = new Plant();
    plant.setName("Monstera");
    plant.setScientificName(scientificName);
    plant.setDescription("seeded for testing");
    plant.setWateringFrequency(7);
    plant.setCreatedAt(LocalDateTime.now());
    plant = plantRepository.save(plant);

    UserPlant individual = new UserPlant();
    individual.setUserId(userRepository.findByUsername(username).orElseThrow().getId());
    individual.setPlantId(plant.getId());
    individual.setNickname("");
    individual.setCreatedAt(LocalDateTime.now());
    individual.setUpdatedAt(LocalDateTime.now());
    return userPlantRepository.save(individual);
  }

  /** Seeds the image row without writing the files, to exercise the missing-file branch. */
  private void seedImageRow(Long userPlantId, UUID imageId) {
    userPlantImageRepository.save(
        TestImages.userPlantImage(userPlantId, imageId, LocalDateTime.now()));
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
