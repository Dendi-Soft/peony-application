package com.dendi.peony.service.plant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.dendi.peony.config.UserImageProperties;
import com.dendi.peony.entity.UserPlant;
import com.dendi.peony.exception.ImageAlreadyExistsException;
import com.dendi.peony.exception.UserPlantNotFoundException;
import com.dendi.peony.repository.UserPlantImageRepository;
import com.dendi.peony.repository.UserPlantRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

/**
 * Unit tests for the personal-photo upload race (issue #44): when the image insert violates a
 * constraint, the service must remove the just-written files and distinguish a vanished individual
 * (404) from a lost one-image-per-individual race (409).
 */
class UserPlantImageServiceTest {

  private static final long USER_PLANT_ID = 7L;

  private static final long USER_ID = 1L;

  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-06-01T10:00:00Z"), ZoneOffset.UTC);

  @TempDir Path imageDir;

  /**
   * Precondition: the individual exists at the ownership check but another transaction deletes it
   * before the image row is flushed. The insert violates the foreign key, so the upload deletes the
   * just-written files and reports the individual as not found.
   */
  @Test
  void testUpload_DeletesFilesAndThrowsNotFound_WhenIndividualDeletedDuringInsert()
      throws Exception {
    UserPlantRepository userPlantRepository = mock(UserPlantRepository.class);
    UserPlantImageRepository imageRepository = mock(UserPlantImageRepository.class);
    UserPlantImageProcessor processor = mock(UserPlantImageProcessor.class);
    when(userPlantRepository.findByIdAndUserId(USER_PLANT_ID, USER_ID))
        .thenReturn(Optional.of(individual()))
        .thenReturn(Optional.empty());
    when(imageRepository.findByUserPlantId(USER_PLANT_ID)).thenReturn(Optional.empty());
    when(imageRepository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("fk"));
    when(processor.process(any())).thenReturn(processed());

    UserPlantImageService service = service(userPlantRepository, imageRepository, processor);

    assertThrows(
        UserPlantNotFoundException.class, () -> service.upload(USER_PLANT_ID, USER_ID, upload()));

    assertEquals(0, storedFileCount(), "the just-written files must be removed");
  }

  /**
   * Precondition: a concurrent upload for the same individual wins the unique constraint. The
   * losing upload deletes its own files and reports the image conflict.
   */
  @Test
  void testUpload_DeletesFilesAndThrowsConflict_WhenConcurrentUploadWins() throws Exception {
    UserPlantRepository userPlantRepository = mock(UserPlantRepository.class);
    UserPlantImageRepository imageRepository = mock(UserPlantImageRepository.class);
    UserPlantImageProcessor processor = mock(UserPlantImageProcessor.class);
    when(userPlantRepository.findByIdAndUserId(USER_PLANT_ID, USER_ID))
        .thenReturn(Optional.of(individual()));
    when(imageRepository.findByUserPlantId(USER_PLANT_ID)).thenReturn(Optional.empty());
    when(imageRepository.saveAndFlush(any()))
        .thenThrow(new DataIntegrityViolationException("unique"));
    when(processor.process(any())).thenReturn(processed());

    UserPlantImageService service = service(userPlantRepository, imageRepository, processor);

    assertThrows(
        ImageAlreadyExistsException.class, () -> service.upload(USER_PLANT_ID, USER_ID, upload()));

    assertEquals(0, storedFileCount(), "the losing upload must remove its files");
  }

  private UserPlantImageService service(
      UserPlantRepository userPlantRepository,
      UserPlantImageRepository imageRepository,
      UserPlantImageProcessor processor) {
    return new UserPlantImageService(
        new UserImageProperties(imageDir.toString()),
        userPlantRepository,
        imageRepository,
        processor,
        CLOCK);
  }

  private long storedFileCount() throws IOException {
    try (var files = Files.list(imageDir)) {
      return files.count();
    }
  }

  private static UserPlant individual() {
    UserPlant individual = new UserPlant();
    individual.setId(USER_PLANT_ID);
    return individual;
  }

  private static UserPlantImageProcessor.ProcessedImage processed() {
    return new UserPlantImageProcessor.ProcessedImage(
        new byte[] {1, 2, 3}, new byte[] {4, 5}, 10, 20);
  }

  private static MultipartFile upload() {
    return new MockMultipartFile("file", "photo.jpg", "image/jpeg", new byte[] {1, 2, 3});
  }
}
