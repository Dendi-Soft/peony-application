package com.dendi.peony.repository;

import com.dendi.peony.entity.UserPlant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Data access for the per-user individual plant records. */
public interface UserPlantRepository extends JpaRepository<UserPlant, Long> {

  /**
   * Shared projection and joins for a caller-owned individual, its catalog plant, and its optional
   * caller-owned location.
   *
   * <p>The room and house are joined only when the house belongs to the caller, so a stale or
   * foreign location never leaks.
   */
  String WITH_PLANT_AND_LOCATION =
      """
      SELECT new com.dendi.peony.repository.UserPlantWithPlantAndLocation(up, p, r, h)
      FROM UserPlant up
      JOIN Plant p ON p.id = up.plantId
      LEFT JOIN House h
        ON h.id = (SELECT r2.houseId FROM Room r2 WHERE r2.id = up.roomId)
        AND h.userId = :userId
      LEFT JOIN Room r ON r.id = up.roomId AND h.id IS NOT NULL
      """;

  /**
   * Finds an individual only when the given user owns it.
   *
   * @param id user-plant id
   * @param userId id of the looking account
   * @return the individual, if it exists and belongs to the user
   */
  Optional<UserPlant> findByIdAndUserId(Long id, Long userId);

  /**
   * Lists a user's individuals with their catalog plants and optional locations, newest first.
   *
   * <p>One joined query keeps the individual listing free of an N+1 lookup. Ordering is by
   * individual creation timestamp, then id, descending.
   *
   * @param userId account id
   * @return the caller's individuals paired with their catalog plants and caller-owned locations
   */
  @Query(
      WITH_PLANT_AND_LOCATION
          + """
          WHERE up.userId = :userId
          ORDER BY up.createdAt DESC, up.id DESC
          """)
  List<UserPlantWithPlantAndLocation> findAllWithPlantAndLocationByUserId(
      @Param("userId") Long userId);

  /**
   * Loads one caller-owned individual with its catalog plant and optional caller-owned location.
   *
   * @param id user-plant id
   * @param userId id of the looking account
   * @return the individual, if it exists and belongs to the user
   */
  @Query(
      WITH_PLANT_AND_LOCATION
          + """
          WHERE up.id = :id AND up.userId = :userId
          """)
  Optional<UserPlantWithPlantAndLocation> findWithPlantAndLocationByIdAndUserId(
      @Param("id") Long id, @Param("userId") Long userId);
}
