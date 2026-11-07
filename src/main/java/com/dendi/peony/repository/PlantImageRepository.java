package com.dendi.peony.repository;

import com.dendi.peony.entity.PlantImage;
import com.dendi.peony.entity.PlantImageSource;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Data access for the plant-to-image-file mapping. */
public interface PlantImageRepository extends JpaRepository<PlantImage, Long> {

  /**
   * Finds the image of a plant at a gallery position.
   *
   * @param plantId id of the plant
   * @param position 0-based gallery position
   * @return the image row, if present
   */
  Optional<PlantImage> findByPlantIdAndPosition(Long plantId, Integer position);

  /**
   * Lists a plant's images in gallery order.
   *
   * @param plantId id of the plant
   * @return the image rows, by ascending position
   */
  List<PlantImage> findByPlantIdOrderByPositionAsc(Long plantId);

  /**
   * Checks whether a plant already has any image.
   *
   * @param plantId id of the plant
   * @return true when at least one image row exists
   */
  boolean existsByPlantId(Long plantId);

  /**
   * Loads the position-0 (primary) image of a plant.
   *
   * @param plantId id of the plant
   * @return the primary image row, if present
   */
  Optional<PlantImage> findFirstByPlantIdOrderByPositionAsc(Long plantId);

  /**
   * Batch-loads images of several plants in gallery order (issue #45 attribution).
   *
   * <p>Used by catalogue search to avoid one query per result row.
   *
   * @param plantIds ids of the plants
   * @return the image rows, ordered by plant id then ascending position
   */
  List<PlantImage> findByPlantIdInOrderByPlantIdAscPositionAsc(List<Long> plantIds);

  /**
   * Checks whether a plant already has an image from one origin.
   *
   * <p>The catalogue image seeder uses this to skip a species that already has a curated {@code
   * SEED} image before downloading anything (issue #9 follow-up).
   *
   * @param plantId id of the plant
   * @param source image origin to look for
   * @return true when at least one matching image row exists
   */
  boolean existsByPlantIdAndSource(Long plantId, PlantImageSource source);

  /**
   * Moves every image of a plant above a large offset in one statement.
   *
   * <p>Parking rows above {@code offset} frees positions 0..n-1 so the second shift can move them
   * to 1..n without ever colliding with the {@code (plant_id, position)} unique constraint (issue
   * #9 follow-up).
   *
   * @param plantId id of the plant whose rows move
   * @param offset distance added to every position
   * @return number of rows updated
   */
  @Modifying(clearAutomatically = true)
  @Query(
      value = "UPDATE plant_images SET position = position + :offset WHERE plant_id = :plantId",
      nativeQuery = true)
  int shiftPositionsUp(@Param("plantId") Long plantId, @Param("offset") int offset);

  /**
   * Moves the parked images back down to their final positions, starting at 1.
   *
   * <p>Only rows at or above {@code offset} move, so the new position-0 row is never touched.
   *
   * @param plantId id of the plant whose rows move
   * @param offset the same distance used by {@link #shiftPositionsUp}
   * @return number of rows updated
   */
  @Modifying(clearAutomatically = true)
  @Query(
      value =
          """
          UPDATE plant_images
          SET position = position - (:offset - 1)
          WHERE plant_id = :plantId AND position >= :offset
          """,
      nativeQuery = true)
  int shiftPositionsDown(@Param("plantId") Long plantId, @Param("offset") int offset);
}
