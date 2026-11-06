package com.dendi.peony.repository;

import com.dendi.peony.entity.UserPlant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Data access for the per-user individual plant records. */
public interface UserPlantRepository extends JpaRepository<UserPlant, Long> {

  /**
   * Shared projection and joins for a caller-owned individual, its catalog plant, its optional
   * caller-owned location, and its optional personal photo.
   *
   * <p>The room and house are joined only when the house belongs to the caller, so a stale or
   * foreign location never leaks. The image is joined by its unique user-plant link, keeping the
   * listing free of an N+1 lookup for the {@code hasImage} / {@code imageId} fields. The catalog
   * plant's position-0 image is joined for the species-image credit.
   */
  String WITH_PLANT_AND_LOCATION =
      """
      SELECT new com.dendi.peony.repository.UserPlantProjection(up, p, r, h, upi, pi)
      FROM UserPlant up
      JOIN Plant p ON p.id = up.plantId
      LEFT JOIN House h
        ON h.id = (SELECT r2.houseId FROM Room r2 WHERE r2.id = up.roomId)
        AND h.userId = :userId
      LEFT JOIN Room r ON r.id = up.roomId AND h.id IS NOT NULL
      LEFT JOIN UserPlantImage upi ON upi.userPlantId = up.id
      LEFT JOIN PlantImage pi ON pi.plantId = p.id AND pi.position = 0
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
  List<UserPlantProjection> findAllWithPlantAndLocationByUserId(@Param("userId") Long userId);

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
  Optional<UserPlantProjection> findWithPlantAndLocationByIdAndUserId(
      @Param("id") Long id, @Param("userId") Long userId);

  /**
   * Fills the next watering due date for individuals of seeded species.
   *
   * <p>Only individuals with a last-watered date and no due date are scheduled, using the curated
   * watering frequency. An existing due date is never overwritten, so re-running the seeder is
   * idempotent and cannot move a user's schedule (ADR-007).
   *
   * @param seededNames scientific names of the curated species
   * @return number of individuals whose due date was filled
   */
  @Modifying(clearAutomatically = true)
  @Query(
      value =
          """
          UPDATE user_plants up
          SET next_water_due_at = up.last_watered_at
              + (p.watering_frequency * INTERVAL '1 day')
          FROM plants p
          WHERE up.plant_id = p.id
            AND up.last_watered_at IS NOT NULL
            AND up.next_water_due_at IS NULL
            AND p.watering_frequency IS NOT NULL
            AND p.scientific_name IN (:seededNames)
          """,
      nativeQuery = true)
  int backfillNextWaterDueAt(@Param("seededNames") List<String> seededNames);
}
