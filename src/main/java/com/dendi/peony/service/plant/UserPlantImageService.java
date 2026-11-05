package com.dendi.peony.service.plant;

import com.dendi.peony.config.UserImageProperties;
import com.dendi.peony.entity.UserPlantImage;
import com.dendi.peony.exception.CorruptImageException;
import com.dendi.peony.exception.ImageAlreadyExistsException;
import com.dendi.peony.exception.ImageTooLargeException;
import com.dendi.peony.exception.UnsupportedImageFormatException;
import com.dendi.peony.exception.UserPlantImageNotFoundException;
import com.dendi.peony.exception.UserPlantNotFoundException;
import com.dendi.peony.repository.UserPlantImageRepository;
import com.dendi.peony.repository.UserPlantRepository;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

/**
 * Stores and serves the caller's per-individual plant photos (issue #44, ADR-008).
 *
 * <p>One image slot per individual. The processor validates and normalises the upload; this service
 * owns the lifecycle: opaque file names derived from the immutable image id, the database row, the
 * owner check, the 409 retry contract, and synchronous deletion together with the individual. Bytes
 * live under {@code peony.user-images.dir}; files are deleted only after the database transaction
 * commits so a rollback cannot leave a row without bytes.
 */
@Service
public class UserPlantImageService {

  private static final Logger log = LoggerFactory.getLogger(UserPlantImageService.class);

  private static final String DISPLAY_SUFFIX = ".jpg";
  private static final String THUMB_SUFFIX = "-thumb.jpg";

  private final Path imageDir;
  private final UserPlantRepository userPlantRepository;
  private final UserPlantImageRepository imageRepository;
  private final UserPlantImageProcessor processor;
  private final Clock clock;

  /**
   * Creates the service.
   *
   * @param properties configured personal photo directory and its storage settings
   * @param userPlantRepository individual data access
   * @param imageRepository image metadata data access
   * @param processor validation and image processing pipeline
   * @param clock source of the stored creation timestamp
   */
  public UserPlantImageService(
      UserImageProperties properties,
      UserPlantRepository userPlantRepository,
      UserPlantImageRepository imageRepository,
      UserPlantImageProcessor processor,
      Clock clock) {
    this.imageDir = properties.directory();
    this.userPlantRepository = userPlantRepository;
    this.imageRepository = imageRepository;
    this.processor = processor;
    this.clock = clock;
  }

  /** The two stored file names of one image slot: display and thumbnail. */
  private record ImageFiles(String filename, String thumbFilename) {}

  /**
   * Validates and stores one individual's personal photo.
   *
   * <p>The individual must belong to the caller; another user's individual behaves as not found. An
   * individual may hold only one image: an existing image is a conflict, and a concurrent insert
   * that loses the unique race is mapped to the same conflict after the just-written files are
   * removed. When the individual disappears during the insert (its foreign key rejects the row),
   * the same cleanup happens and the caller sees the not-found response instead.
   *
   * <p>The insert is deliberately not part of an outer transaction: when a constraint violation
   * aborts a surrounding transaction, PostgreSQL rejects every later statement on it, and the
   * ownership re-check after a lost insert would then fail instead of reporting the outcome. The
   * row insert commits or rolls back on its own; a caller-side failure after that leaves the image
   * stored, which is the retry-safe outcome.
   *
   * @param userPlantId individual id
   * @param userId id of the calling account
   * @param file multipart JPEG upload
   * @throws UserPlantNotFoundException when no such individual belongs to the caller, including
   *     when the individual is deleted while the upload is being stored
   * @throws ImageAlreadyExistsException when the individual already has an image
   * @throws ImageTooLargeException when the upload exceeds the 1 MiB or pixel-dimension cap
   * @throws UnsupportedImageFormatException when the upload is not JPEG
   * @throws CorruptImageException when the upload carries a JPEG signature but cannot be decoded
   */
  public void upload(Long userPlantId, Long userId, MultipartFile file) {
    requireOwnedIndividual(userPlantId, userId);
    if (imageRepository.findByUserPlantId(userPlantId).isPresent()) {
      throw new ImageAlreadyExistsException(userPlantId);
    }

    UserPlantImageProcessor.ProcessedImage processed = processor.process(readBytes(file));
    UUID imageId = UUID.randomUUID();
    ImageFiles files = new ImageFiles(imageId + DISPLAY_SUFFIX, imageId + THUMB_SUFFIX);
    writeFiles(files, processed);

    UserPlantImage image = new UserPlantImage();
    image.setUserPlantId(userPlantId);
    image.setImageId(imageId);
    image.setFilename(files.filename());
    image.setThumbFilename(files.thumbFilename());
    image.setWidth(processed.width());
    image.setHeight(processed.height());
    image.setByteSize((long) processed.display().length);
    image.setCreatedAt(LocalDateTime.now(clock));
    try {
      // Flush inside the method so the unique-race loss is caught here, with the files at hand.
      imageRepository.saveAndFlush(image);
    } catch (DataIntegrityViolationException e) {
      deleteFiles(files);
      if (userPlantRepository.findByIdAndUserId(userPlantId, userId).isEmpty()) {
        throw new UserPlantNotFoundException(userPlantId);
      }
      throw new ImageAlreadyExistsException(userPlantId);
    }
  }

  /**
   * Loads one individual's stored photo for the calling owner.
   *
   * @param userPlantId individual id
   * @param userId id of the calling account
   * @param size which stored copy to load
   * @return the stored file name and bytes
   * @throws UserPlantNotFoundException when no such individual belongs to the caller
   * @throws UserPlantImageNotFoundException when the individual has no photo or its file is missing
   */
  @Transactional(readOnly = true)
  public StoredImage load(Long userPlantId, Long userId, ImageSize size) {
    requireOwnedIndividual(userPlantId, userId);
    UserPlantImage image =
        imageRepository
            .findByUserPlantId(userPlantId)
            .orElseThrow(() -> new UserPlantImageNotFoundException(userPlantId));
    String filename =
        switch (size) {
          case DISPLAY -> image.getFilename();
          case THUMBNAIL -> image.getThumbFilename();
        };
    Path file = ImageDirectory.resolve(imageDir, filename);
    if (file == null || !Files.isRegularFile(file)) {
      throw new UserPlantImageNotFoundException(userPlantId);
    }
    try {
      return new StoredImage(filename, Files.readAllBytes(file));
    } catch (IOException e) {
      throw new UserPlantImageNotFoundException(userPlantId);
    }
  }

  /**
   * Removes an individual's image row and schedules both files for deletion after commit.
   *
   * @param userPlantId individual id; a no-op when the individual has no image
   */
  @Transactional
  public void deleteForUserPlant(Long userPlantId) {
    imageRepository
        .findByUserPlantId(userPlantId)
        .ifPresent(
            image -> {
              imageRepository.delete(image);
              deleteFilesAfterCommit(new ImageFiles(image.getFilename(), image.getThumbFilename()));
            });
  }

  private void requireOwnedIndividual(Long userPlantId, Long userId) {
    userPlantRepository
        .findByIdAndUserId(userPlantId, userId)
        .orElseThrow(() -> new UserPlantNotFoundException(userPlantId));
  }

  private static byte[] readBytes(MultipartFile file) {
    if (file == null) {
      return new byte[0];
    }
    try {
      return file.getBytes();
    } catch (IOException e) {
      throw new IllegalArgumentException("Could not read the uploaded image", e);
    }
  }

  private void writeFiles(ImageFiles files, UserPlantImageProcessor.ProcessedImage processed) {
    try {
      Files.createDirectories(imageDir);
      Files.write(
          imageDir.resolve(files.filename()), processed.display(), StandardOpenOption.CREATE_NEW);
      Files.write(
          imageDir.resolve(files.thumbFilename()),
          processed.thumb(),
          StandardOpenOption.CREATE_NEW);
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to store the uploaded image", e);
    }
  }

  /** Deletes the bytes only after the transaction commits; a failed delete is logged, not fatal. */
  private void deleteFilesAfterCommit(ImageFiles files) {
    Runnable deletion = () -> deleteFiles(files);
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCommit() {
              deletion.run();
            }
          });
    } else {
      deletion.run();
    }
  }

  private void deleteFiles(ImageFiles files) {
    deleteQuietly(files.filename());
    deleteQuietly(files.thumbFilename());
  }

  private void deleteQuietly(String filename) {
    Path file = ImageDirectory.resolve(imageDir, filename);
    if (file == null) {
      log.warn("Refusing to delete a user image outside the image directory: {}", filename);
      return;
    }
    try {
      Files.deleteIfExists(file);
    } catch (IOException e) {
      log.warn("Failed to delete user image file {}", filename, e);
    }
  }
}
