package com.dendi.peony.service.plant;

import com.dendi.peony.config.UserImageProperties;
import com.dendi.peony.repository.UserPlantImageRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Removes personal photo files whose database row never committed (issue #44, ADR-008).
 *
 * <p>Uploads write both files before inserting the row, so a crash or a lost insert race leaves
 * unreferenced bytes behind. There is no scheduler in this application, so the sweep runs once per
 * backend start. Files younger than {@link #SAFETY_AGE} are kept: an in-flight upload may have
 * written bytes that are not yet visible in the database.
 */
@Component
public class UserPlantImageOrphanSweeper implements ApplicationRunner {

  /** Age below which an unreferenced file may still belong to an in-flight upload. */
  static final Duration SAFETY_AGE = Duration.ofHours(1);

  private static final Logger log = LoggerFactory.getLogger(UserPlantImageOrphanSweeper.class);

  private final Path imageDir;
  private final UserPlantImageRepository imageRepository;
  private final Clock clock;

  /**
   * Creates the sweeper.
   *
   * @param properties configured personal photo directory
   * @param imageRepository image metadata data access
   * @param clock source of the safety-age threshold
   */
  public UserPlantImageOrphanSweeper(
      UserImageProperties properties, UserPlantImageRepository imageRepository, Clock clock) {
    this.imageDir = properties.directory();
    this.imageRepository = imageRepository;
    this.clock = clock;
  }

  /**
   * Sweeps the store on every backend start.
   *
   * @param args application arguments (unused)
   */
  @Override
  public void run(ApplicationArguments args) {
    sweep();
  }

  /**
   * Deletes unreferenced files older than the safety age.
   *
   * @return how many files were examined and how many were deleted
   */
  public SweepResult sweep() {
    if (!Files.isDirectory(imageDir)) {
      log.debug("User image directory {} does not exist; nothing to sweep", imageDir);
      return new SweepResult(0, 0);
    }
    Set<String> referenced =
        imageRepository.findAll().stream()
            .flatMap(image -> Stream.of(image.getFilename(), image.getThumbFilename()))
            .collect(Collectors.toSet());
    Instant threshold = Instant.now(clock).minus(SAFETY_AGE);
    int checked = 0;
    int deleted = 0;
    try (Stream<Path> entries = Files.list(imageDir)) {
      for (Path entry : entries.toList()) {
        if (!Files.isRegularFile(entry)) {
          continue;
        }
        checked++;
        if (referenced.contains(entry.getFileName().toString()) || !isOlderThan(entry, threshold)) {
          continue;
        }
        try {
          if (Files.deleteIfExists(entry)) {
            deleted++;
          }
        } catch (IOException e) {
          log.warn("Failed to delete orphaned user image file {}", entry, e);
        }
      }
    } catch (IOException e) {
      log.warn("Failed to list the user image directory {}", imageDir, e);
      return new SweepResult(checked, deleted);
    }
    log.info("User image sweep: checked {} file(s), deleted {} orphan(s)", checked, deleted);
    return new SweepResult(checked, deleted);
  }

  private boolean isOlderThan(Path file, Instant threshold) {
    try {
      return Files.getLastModifiedTime(file).toInstant().isBefore(threshold);
    } catch (IOException e) {
      log.warn("Failed to read the modification time of user image file {}", file, e);
      return false;
    }
  }

  /** The outcome of one sweep: files examined and files deleted. */
  public record SweepResult(int checked, int deleted) {}
}
