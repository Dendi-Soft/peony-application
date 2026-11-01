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
   * Lists a plant's assignments whose room belongs to one of the caller's houses.
   *
   * <p>Rooms carry no user of their own, so ownership is resolved through the room's house. The
   * joins filter out assignments under another user's house and return the room and house with each
   * assignment, so the caller needs no follow-up lookups.
   *
   * @param plantId id of the plant
   * @param userId id of the calling account
   * @return the caller-owned assignments with their room and house, oldest first
   */
  @Query(
      """
      SELECT new com.dendi.peony.repository.OwnedPlantLocation(pl, r, h)
      FROM PlantLocation pl
      JOIN Room r ON r.id = pl.roomId
      JOIN House h ON h.id = r.houseId
      WHERE pl.plantId = :plantId AND h.userId = :userId
      ORDER BY pl.createdAt ASC, pl.id ASC
      """)
  List<OwnedPlantLocation> findByPlantIdOwnedByUser(
      @Param("plantId") Long plantId, @Param("userId") Long userId);

  /**
   * Lists the assignments of the given plants whose room belongs to one of the caller's houses.
   *
   * <p>Rooms carry no user of their own, so ownership is resolved through the room's house. The
   * joins filter out assignments under another user's house and return the room and house with each
   * assignment, so the caller needs no follow-up lookups.
   *
   * @param plantIds ids of the caller's plants
   * @param userId id of the calling account
   * @return the caller-owned assignments with their room and house, oldest first
   */
  @Query(
      """
      SELECT new com.dendi.peony.repository.OwnedPlantLocation(pl, r, h)
      FROM PlantLocation pl
      JOIN Room r ON r.id = pl.roomId
      JOIN House h ON h.id = r.houseId
      WHERE pl.plantId IN :plantIds AND h.userId = :userId
      ORDER BY pl.createdAt ASC, pl.id ASC
      """)
  List<OwnedPlantLocation> findByPlantIdsOwnedByUser(
      @Param("plantIds") List<Long> plantIds, @Param("userId") Long userId);
}
