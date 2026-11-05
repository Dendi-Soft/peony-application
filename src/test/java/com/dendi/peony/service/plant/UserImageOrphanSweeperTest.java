package com.dendi.peony.service.plant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.dendi.peony.entity.UserPlantImage;
import com.dendi.peony.repository.UserPlantImageRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests the user-image orphan sweep (issue #44): files without a committed row are removed only
 * once they are older than the safety age, so a sweep during an in-flight upload cannot delete
 * bytes that are about to be recorded.
 */
class UserImageOrphanSweeperTest {

  private static final Instant NOW = Instant.parse("2026-06-01T10:00:00Z");

  @TempDir Path imageDir;

  /**
   * Precondition: the directory holds a referenced display and thumbnail, an old unreferenced file,
   * and a fresh unreferenced file. The sweep deletes only the old unreferenced file.
   */
  @Test
  void testSweep_DeletesOnlyOldUnreferencedFiles_WhenDirectoryHasMixedFiles() throws Exception {
    UserPlantImage image = imageRow(UUID.randomUUID());
    Path display = writeFile(image.getFilename(), NOW.minusSeconds(2 * 60 * 60));
    Path thumb = writeFile(image.getThumbFilename(), NOW.minusSeconds(2 * 60 * 60));
    Path oldOrphan = writeFile("orphan-old.jpg", NOW.minusSeconds(2 * 60 * 60));
    Path freshOrphan = writeFile("orphan-fresh.jpg", NOW.minusSeconds(30 * 60));

    UserImageOrphanSweeper.SweepResult result = sweeper(List.of(image)).sweep();

    assertEquals(4, result.checked());
    assertEquals(1, result.deleted());
    assertTrue(Files.exists(display), "a referenced display file must survive");
    assertTrue(Files.exists(thumb), "a referenced thumbnail must survive");
    assertFalse(Files.exists(oldOrphan), "an old orphan must be deleted");
    assertTrue(
        Files.exists(freshOrphan),
        "a fresh orphan may belong to an in-flight upload and must stay");
  }

  /** A sweep against a directory that does not exist yet is a no-op, not a startup failure. */
  @Test
  void testSweep_DoesNothing_WhenImageDirectoryIsMissing() {
    UserImageOrphanSweeper sweeper =
        new UserImageOrphanSweeper(
            imageDir.resolve("missing").toString(),
            mock(UserPlantImageRepository.class),
            fixedClock());

    UserImageOrphanSweeper.SweepResult result = sweeper.sweep();

    assertEquals(0, result.checked());
    assertEquals(0, result.deleted());
  }

  /** Files in nested directories are not part of the flat store and are left alone. */
  @Test
  void testSweep_IgnoresNestedFiles_WhenSubdirectoryExists() throws Exception {
    Path nested = Files.createDirectories(imageDir.resolve("nested"));
    Path nestedFile = writeFileAt(nested.resolve("old.jpg"), NOW.minusSeconds(7200));

    UserImageOrphanSweeper.SweepResult result = sweeper(List.of()).sweep();

    assertEquals(0, result.checked());
    assertTrue(Files.exists(nestedFile), "the store is flat; nested files are out of scope");
  }

  private UserImageOrphanSweeper sweeper(List<UserPlantImage> images) {
    UserPlantImageRepository repository = mock(UserPlantImageRepository.class);
    when(repository.findAll()).thenReturn(images);
    return new UserImageOrphanSweeper(imageDir.toString(), repository, fixedClock());
  }

  private static Clock fixedClock() {
    return Clock.fixed(NOW, ZoneOffset.UTC);
  }

  private Path writeFile(String filename, Instant lastModified) throws IOException {
    return writeFileAt(imageDir.resolve(filename), lastModified);
  }

  private static Path writeFileAt(Path file, Instant lastModified) throws IOException {
    Files.write(file, new byte[] {1, 2, 3});
    Files.setLastModifiedTime(file, FileTime.from(lastModified));
    return file;
  }

  private static UserPlantImage imageRow(UUID imageId) {
    UserPlantImage image = new UserPlantImage();
    image.setId(1L);
    image.setUserPlantId(7L);
    image.setImageId(imageId);
    image.setFilename(imageId + ".jpg");
    image.setThumbFilename(imageId + "-thumb.jpg");
    image.setWidth(1280);
    image.setHeight(960);
    image.setByteSize(3L);
    image.setCreatedAt(LocalDateTime.of(2026, 1, 1, 12, 0));
    return image;
  }
}
