package com.dendi.peony.repository;

import com.dendi.peony.entity.UserPlant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Data access for the per-user plant records. */
public interface UserPlantRepository extends JpaRepository<UserPlant, Long> {

  /**
   * Finds the user's record for a plant.
   *
   * @param userId account id
   * @param plantId catalog plant id
   * @return the record, if the user has it
   */
  Optional<UserPlant> findByUserIdAndPlantId(Long userId, Long plantId);

  /**
   * Lists a user's plants together with their catalog plants, newest first.
   *
   * <p>One joined query keeps the per-user listing free of an N+1 lookup and uses the (user_id,
   * plant_id) unique link. Ordering is by user-plant creation timestamp, then id, descending.
   *
   * @param userId account id
   * @return the caller's user plants paired with their catalog plants
   */
  @Query(
      """
      SELECT new com.dendi.peony.repository.UserPlantWithPlant(up, p)
      FROM UserPlant up
      JOIN Plant p ON p.id = up.plantId
      WHERE up.userId = :userId
      ORDER BY up.createdAt DESC, up.id DESC
      """)
  List<UserPlantWithPlant> findAllWithPlantByUserId(@Param("userId") Long userId);
}
