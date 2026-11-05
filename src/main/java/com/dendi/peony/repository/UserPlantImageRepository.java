package com.dendi.peony.repository;

import com.dendi.peony.entity.UserPlantImage;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Data access for the per-individual personal plant photos. */
public interface UserPlantImageRepository extends JpaRepository<UserPlantImage, Long> {

  /**
   * Finds the image slot of one individual.
   *
   * @param userPlantId individual id
   * @return the individual's image, if it has one
   */
  Optional<UserPlantImage> findByUserPlantId(Long userPlantId);
}
