package com.dendi.peony.repository;

import com.dendi.peony.entity.PlantLocation;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** Data access for plant-to-room assignments. */
public interface PlantLocationRepository extends JpaRepository<PlantLocation, Long> {

  /**
   * Reports whether a plant is already assigned to a room.
   *
   * @param plantId id of the plant
   * @param roomId id of the room
   * @return {@code true} when the pair is already assigned
   */
  boolean existsByPlantIdAndRoomId(Long plantId, Long roomId);

  /**
   * Lists a plant's locations.
   *
   * @param plantId id of the plant
   * @return the assignments, oldest first
   */
  List<PlantLocation> findByPlantIdOrderByCreatedAtAsc(Long plantId);
}
