package com.dendi.peony.repository;

import com.dendi.peony.entity.House;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Data access for user-owned houses. */
public interface HouseRepository extends JpaRepository<House, Long> {

  /**
   * Finds a house only when the given user owns it.
   *
   * @param id house id
   * @param userId id of the looking account
   * @return the house, if it exists and belongs to the user
   */
  Optional<House> findByIdAndUserId(Long id, Long userId);

  /**
   * Lists the houses owned by a user.
   *
   * @param userId id of the owning account
   * @return the user's houses
   */
  List<House> findByUserId(Long userId);
}
