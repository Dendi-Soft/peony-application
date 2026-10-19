package com.dendi.peony.repository;

import com.dendi.peony.entity.UserPlant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

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
}
