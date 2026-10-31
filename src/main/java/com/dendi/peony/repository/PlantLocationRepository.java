package com.dendi.peony.repository;

import com.dendi.peony.entity.PlantLocation;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

  /**
   * Lists the assignments of the given plants whose room belongs to one of the caller's houses.
   *
   * <p>Rooms carry no user of their own, so ownership is resolved through the room's house. The
   * join filters out assignments under another user's house; plants the caller did not link are
   * filtered by the caller's plant ids.
   *
   * @param plantIds ids of the caller's plants
   * @param userId id of the calling account
   * @return the caller-owned assignments, oldest first
   */
  @Query(
      """
      SELECT pl
      FROM PlantLocation pl
      JOIN Room r ON r.id = pl.roomId
      JOIN House h ON h.id = r.houseId
      WHERE pl.plantId IN :plantIds AND h.userId = :userId
      ORDER BY pl.createdAt ASC, pl.id ASC
      """)
  List<PlantLocation> findOwnedByPlantIds(
      @Param("plantIds") List<Long> plantIds, @Param("userId") Long userId);
}
