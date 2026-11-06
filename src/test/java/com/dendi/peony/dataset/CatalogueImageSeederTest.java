package com.dendi.peony.dataset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dendi.peony.TestImages;
import com.dendi.peony.TestcontainersConfiguration;
import com.dendi.peony.entity.Plant;
import com.dendi.peony.entity.PlantImage;
import com.dendi.peony.entity.PlantImageSource;
import com.dendi.peony.repository.PlantImageRepository;
import com.dendi.peony.repository.PlantRepository;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

/**
 * Integration tests for the catalogue image seeder (issue #9 follow-up), against a real PostgreSQL
 * database with a mocked downloader.
 *
 * <p>The seeded images converge on position 0 with {@code source = SEED} and the manifest credits;
 * re-running skips a species that already has a seed image before downloading; a pre-existing
 * PlantNet position-0 row is shifted to position 1 without losing its file or credits; and a failed
 * download leaves neither a row nor a file and is counted.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class CatalogueImageSeederTest {

  private static final String FIXTURE = "dataset/catalogue-images-test.json";

  @TempDir static Path imageDir;

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("peony.plant-images.dir", imageDir::toString);
    registry.add("peony.dataset.images.file", () -> FIXTURE);
    registry.add("peony.dataset.images.long-edge", () -> "512");
  }

  @Autowired private CatalogueImageSeeder seeder;

  @Autowired private PlantRepository plantRepository;

  @Autowired private PlantImageRepository plantImageRepository;

  @MockitoBean private CatalogueImageDownloader downloader;

  @BeforeEach
  void clearImageDirectory() throws IOException {
    Files.createDirectories(imageDir);
    try (Stream<Path> files = Files.list(imageDir)) {
      for (Path file : files.toList()) {
        Files.deleteIfExists(file);
      }
    }
  }

  /** Every species is stored at position 0 with its credits and a 512 px JPEG. */
  @Test
  void testSeed_StoresSeedImageAtPositionZeroWithCredits_WhenCatalogueEmpty() throws Exception {
    seedPlants();
    when(downloader.download(anyString())).thenAnswer(invocation -> TestImages.jpegOf(1024, 768));

    CatalogueImageSeeder.SeedResult result = seeder.seed();

    assertEquals(3, result.seeded());
    assertEquals(0, result.failed());
    CatalogueImageEntry entry = fixtureEntry("Dracaena trifasciata");
    Plant plant = plantRepository.findByScientificName(entry.scientificName()).orElseThrow();
    PlantImage image =
        plantImageRepository.findByPlantIdAndPosition(plant.getId(), 0).orElseThrow();
    assertEquals(PlantImageSource.SEED, image.getSource());
    assertEquals(entry.image().author(), image.getAuthor());
    assertEquals(entry.image().license(), image.getLicense());
    assertEquals(expectedCitation(entry), image.getCitation());
    assertTrue(image.getFilename().startsWith("seed-"), "seed files use their own convention");
    assertTrue(image.getFilename().endsWith(".jpg"));

    Path stored = imageDir.resolve(image.getFilename());
    assertTrue(Files.isRegularFile(stored), "the processed seed image must be on disk");
    BufferedImage decoded = ImageIO.read(stored.toFile());
    assertEquals(512, Math.max(decoded.getWidth(), decoded.getHeight()));
  }

  /** A second run skips every species before downloading anything. */
  @Test
  void testSeed_SkipsSpeciesAndDoesNotDownload_WhenSeedImageAlreadyExists() throws Exception {
    seedPlants();
    when(downloader.download(anyString())).thenAnswer(invocation -> TestImages.jpegOf(1024, 768));

    seeder.seed();
    verify(downloader, times(3)).download(anyString());

    CatalogueImageSeeder.SeedResult second = seeder.seed();

    assertEquals(0, second.seeded());
    assertEquals(3, second.skipped());
    assertEquals(0, second.failed());
    verify(downloader, times(3)).download(anyString());
  }

  /** A PlantNet position-0 fallback moves to position 1 and keeps its file and credits. */
  @Test
  void testSeed_ShiftsPlantNetImageToPositionOne_WhenSeedImageTakesPositionZero() throws Exception {
    Plant snake = seedPlants().get(0);
    PlantImage existing =
        plantImageRepository.saveAndFlush(
            existingPlantNetImage(snake.getId(), "plantnet-existing.jpg"));
    when(downloader.download(anyString())).thenAnswer(invocation -> TestImages.jpegOf(1024, 768));

    seeder.seed();

    List<PlantImage> images = plantImageRepository.findByPlantIdOrderByPositionAsc(snake.getId());
    assertEquals(2, images.size(), "the PlantNet fallback must be retained");
    assertEquals(0, images.get(0).getPosition());
    assertEquals(PlantImageSource.SEED, images.get(0).getSource());
    assertEquals(1, images.get(1).getPosition());
    assertEquals(existing.getId(), images.get(1).getId());
    assertEquals("plantnet-existing.jpg", images.get(1).getFilename());
    assertEquals(PlantImageSource.PLANTNET, images.get(1).getSource());
    assertEquals("PlantNet author", images.get(1).getAuthor());
    assertEquals("cc-by", images.get(1).getLicense());
    assertEquals("PlantNet citation", images.get(1).getCitation());
  }

  /** A failed download leaves no row and no file for that species and is counted. */
  @Test
  void testSeed_LeavesNoRowOrFileAndCountsFailure_WhenDownloadFails() throws Exception {
    seedPlants();
    CatalogueImageEntry failing = fixtureEntry("Dracaena trifasciata");
    when(downloader.download(anyString()))
        .thenAnswer(
            invocation -> {
              if (failing.image().url().equals(invocation.getArgument(0))) {
                throw new IOException("network down");
              }
              return TestImages.jpegOf(1024, 768);
            });

    CatalogueImageSeeder.SeedResult result = seeder.seed();

    assertEquals(2, result.seeded());
    assertEquals(1, result.failed());
    Plant failedPlant =
        plantRepository.findByScientificName(failing.scientificName()).orElseThrow();
    assertFalse(
        plantImageRepository.existsByPlantId(failedPlant.getId()),
        "a failed download must leave no image row");
    assertEquals(2, storedFileCount(), "only the two successful images may leave files");
  }

  private List<Plant> seedPlants() {
    return List.of(
            plant("Dracaena trifasciata", "Snake Plant"),
            plant("Zamioculcas zamiifolia", "ZZ Plant"),
            plant("Epipremnum aureum", "Pothos"))
        .stream()
        .map(plantRepository::saveAndFlush)
        .toList();
  }

  private static Plant plant(String scientificName, String commonName) {
    Plant plant = new Plant();
    plant.setScientificName(scientificName);
    plant.setName(commonName);
    plant.setDescription("test plant");
    plant.setCreatedAt(LocalDateTime.of(2026, 1, 2, 8, 30));
    return plant;
  }

  private static PlantImage existingPlantNetImage(Long plantId, String filename) {
    PlantImage image = new PlantImage();
    image.setPlantId(plantId);
    image.setPosition(0);
    image.setFilename(filename);
    image.setSource(PlantImageSource.PLANTNET);
    image.setAuthor("PlantNet author");
    image.setLicense("cc-by");
    image.setCitation("PlantNet citation");
    image.setCreatedAt(LocalDateTime.of(2025, 12, 1, 10, 0));
    return image;
  }

  private static CatalogueImageEntry fixtureEntry(String scientificName) {
    return CatalogueImageFile.load(FIXTURE).species().stream()
        .filter(entry -> entry.scientificName().equals(scientificName))
        .findFirst()
        .orElseThrow();
  }

  private static String expectedCitation(CatalogueImageEntry entry) {
    return entry.image().title() + " — " + entry.image().descriptionUrl();
  }

  private long storedFileCount() throws IOException {
    try (Stream<Path> files = Files.list(imageDir)) {
      return files.filter(Files::isRegularFile).count();
    }
  }
}
