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
}
