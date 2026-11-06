package com.dendi.peony.service.plant;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.dendi.peony.entity.Plant;
import com.dendi.peony.entity.PlantNetSpeciesImage;
import com.dendi.peony.repository.PlantImageRepository;
import com.dendi.peony.repository.PlantRepository;
import com.dendi.peony.service.plantrecognition.PlantNetStagingService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Unit tests for the species-image fill pipeline (issue #45, ADR-008): it runs only for an
 * image-less catalogue plant with a fresh staged candidate, writes a file only when the pipeline
 * succeeds, deletes the file when the row insert loses, and never propagates a failure into the
 * scan result.
 */
class SpeciesImageServiceTest {

  private static final long PLANT_ID = 42L;

  private static final String IMAGE_URL = "https://bs.plantnet.org/image/m/abc";

  private static final byte[] DOWNLOAD = {1, 2, 3, 4};

  private static final byte[] PROCESSED = {9, 8, 7};

  @TempDir Path imageDir;

  /** The happy path stores the processed file and consumes the staged candidate via the writer. */
  @Test
  void testFillIfMissing_StoresImage_WhenPlantHasNoImageAndCandidateFresh() throws Exception {
    Fixture fixture = fixture();
    when(fixture.stagingService.findFreshForName("Ajuga reptans"))
        .thenReturn(Optional.of(staged()));
    when(fixture.downloadClient.download(IMAGE_URL)).thenReturn(DOWNLOAD);
    when(fixture.processor.process(DOWNLOAD)).thenReturn(PROCESSED);

    boolean filled = fixture.service.fillIfMissing(PLANT_ID);

    assertTrue(filled, "the image must be filled");
    ArgumentCaptor<String> filename = ArgumentCaptor.forClass(String.class);
    verify(fixture.writer)
        .insertPrimary(eq(PLANT_ID), filename.capture(), any(PlantNetSpeciesImage.class));
    Path stored = imageDir.resolve(filename.getValue());
    assertTrue(Files.isRegularFile(stored), "the processed bytes must be on disk");
    assertArrayEquals(PROCESSED, Files.readAllBytes(stored));
    assertTrue(
        filename.getValue().startsWith("plantnet-") && filename.getValue().endsWith(".jpg"),
        "the stored fallback must use the PlantNet file-name convention");
  }

  /** Any existing image, at any position, suppresses the fill and the download. */
  @Test
  void testFillIfMissing_SkipsPlant_WhenAnyImageAlreadyExists() {
    Fixture fixture = fixture();
    when(fixture.imageRepository.existsByPlantId(PLANT_ID)).thenReturn(true);

    boolean filled = fixture.service.fillIfMissing(PLANT_ID);

    assertFalse(filled);
    verifyNoInteractions(fixture.stagingService, fixture.downloadClient, fixture.processor);
    verify(fixture.writer, never()).insertPrimary(anyLong(), anyString(), any());
  }

  /** Without a fresh staged candidate there is nothing to download. */
  @Test
  void testFillIfMissing_DoesNotDownload_WhenNoCandidateStaged() {
    Fixture fixture = fixture();
    when(fixture.stagingService.findFreshForName("Ajuga reptans")).thenReturn(Optional.empty());

    boolean filled = fixture.service.fillIfMissing(PLANT_ID);

    assertFalse(filled);
    verifyNoInteractions(fixture.downloadClient, fixture.processor);
    verify(fixture.writer, never()).insertPrimary(anyLong(), anyString(), any());
  }

  /**
   * A download failure is swallowed, leaves no file, and does not consume the staged candidate, so
   * a later scan can retry.
   */
  @Test
  void testFillIfMissing_LeavesNoImageAndKeepsStaging_WhenDownloadFails() throws Exception {
    Fixture fixture = fixture();
    when(fixture.stagingService.findFreshForName("Ajuga reptans"))
        .thenReturn(Optional.of(staged()));
    when(fixture.downloadClient.download(IMAGE_URL)).thenThrow(new IOException("network down"));

    boolean filled = fixture.service.fillIfMissing(PLANT_ID);

    assertFalse(filled);
    assertTrue(storedFiles().isEmpty(), "a failed download must leave no file");
    verify(fixture.writer, never()).insertPrimary(anyLong(), anyString(), any());
  }

  /** A processor failure is swallowed and leaves no file. */
  @Test
  void testFillIfMissing_LeavesNoImage_WhenProcessingFails() throws Exception {
    Fixture fixture = fixture();
    when(fixture.stagingService.findFreshForName("Ajuga reptans"))
        .thenReturn(Optional.of(staged()));
    when(fixture.downloadClient.download(IMAGE_URL)).thenReturn(DOWNLOAD);
    when(fixture.processor.process(DOWNLOAD)).thenThrow(new IllegalStateException("bad bytes"));

    boolean filled = fixture.service.fillIfMissing(PLANT_ID);

    assertFalse(filled);
    assertTrue(storedFiles().isEmpty(), "a failed decode must leave no file");
    verify(fixture.writer, never()).insertPrimary(anyLong(), anyString(), any());
  }

  /** A losing insert (the plant gained an image meanwhile) removes the just-written file. */
  @Test
  void testFillIfMissing_DeletesFile_WhenRowInsertFails() throws Exception {
    Fixture fixture = fixture();
    when(fixture.stagingService.findFreshForName("Ajuga reptans"))
        .thenReturn(Optional.of(staged()));
    when(fixture.downloadClient.download(IMAGE_URL)).thenReturn(DOWNLOAD);
    when(fixture.processor.process(DOWNLOAD)).thenReturn(PROCESSED);
    doThrow(new DataIntegrityViolationException("unique (plant_id, position)"))
        .when(fixture.writer)
        .insertPrimary(anyLong(), anyString(), any());

    boolean filled = fixture.service.fillIfMissing(PLANT_ID);

    assertFalse(filled);
    assertTrue(storedFiles().isEmpty(), "the losing file must be removed");
  }

  /** A missing catalogue plant is a no-op. */
  @Test
  void testFillIfMissing_DoesNothing_WhenPlantMissing() {
    Fixture fixture = fixture();
    when(fixture.plantRepository.findById(PLANT_ID)).thenReturn(Optional.empty());

    assertFalse(fixture.service.fillIfMissing(PLANT_ID));
    verifyNoInteractions(fixture.stagingService, fixture.downloadClient, fixture.processor);
  }

  private Fixture fixture() {
    PlantRepository plantRepository = mock(PlantRepository.class);
    PlantImageRepository imageRepository = mock(PlantImageRepository.class);
    PlantNetStagingService stagingService = mock(PlantNetStagingService.class);
    PlantNetImageDownloadClient downloadClient = mock(PlantNetImageDownloadClient.class);
    SpeciesImageProcessor processor = mock(SpeciesImageProcessor.class);
    SpeciesImageWriter writer = mock(SpeciesImageWriter.class);
    when(plantRepository.findById(PLANT_ID)).thenReturn(Optional.of(plant()));
    when(imageRepository.existsByPlantId(PLANT_ID)).thenReturn(false);
    return new Fixture(
        new SpeciesImageService(
            imageDir.toString(),
            plantRepository,
            imageRepository,
            stagingService,
            downloadClient,
            processor,
            writer),
        plantRepository,
        imageRepository,
        stagingService,
        downloadClient,
        processor,
        writer);
  }

  private static Plant plant() {
    Plant plant = new Plant();
    plant.setId(PLANT_ID);
    plant.setScientificName("Ajuga reptans");
    return plant;
  }

  private static PlantNetSpeciesImage staged() {
    PlantNetSpeciesImage staged = new PlantNetSpeciesImage();
    staged.setId(7L);
    staged.setScientificName("ajuga reptans");
    staged.setImageUrl(IMAGE_URL);
    staged.setLicense("cc-by-sa");
    return staged;
  }

  private List<Path> storedFiles() throws IOException {
    if (!Files.isDirectory(imageDir)) {
      return List.of();
    }
    try (Stream<Path> files = Files.list(imageDir)) {
      return files.toList();
    }
  }

  private record Fixture(
      SpeciesImageService service,
      PlantRepository plantRepository,
      PlantImageRepository imageRepository,
      PlantNetStagingService stagingService,
      PlantNetImageDownloadClient downloadClient,
      SpeciesImageProcessor processor,
      SpeciesImageWriter writer) {}
}
