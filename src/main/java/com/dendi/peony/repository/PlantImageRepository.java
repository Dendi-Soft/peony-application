package com.dendi.peony.repository;

import com.dendi.peony.entity.PlantImage;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

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
}
