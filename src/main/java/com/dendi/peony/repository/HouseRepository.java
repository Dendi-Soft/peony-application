package com.dendi.peony.repository;

import com.dendi.peony.entity.House;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

  /**
   * Lists a user's houses ordered case-insensitively by name.
   *
   * <p>Names are compared case-insensitively; the id breaks ties so the order is deterministic.
   *
   * @param userId id of the owning account
   * @return the user's houses, ordered case-insensitively by name, then id
   */
  @Query(
      """
      select h from House h
      where h.userId = :userId
      order by lower(h.name) asc, h.id asc
      """)
  List<House> findByUserIdOrderByNameIgnoreCaseAscIdAsc(@Param("userId") Long userId);
}
