package com.dendi.peony.repository;

import com.dendi.peony.entity.Room;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Data access for rooms nested under houses. */
public interface RoomRepository extends JpaRepository<Room, Long> {

  /**
   * Lists the rooms of a house.
   *
   * @param houseId id of the owning house
   * @return the house's rooms
   */
  List<Room> findByHouseId(Long houseId);

  /**
   * Lists the rooms of several houses in one query.
   *
   * <p>Names are compared case-insensitively; the id breaks ties so the order is deterministic.
   *
   * @param houseIds ids of the owning houses
   * @return the rooms of those houses, ordered case-insensitively by name, then id
   */
  @Query(
      """
      select r from Room r
      where r.houseId in :houseIds
      order by lower(r.name) asc, r.id asc
      """)
  List<Room> findByHouseIdInOrderByNameIgnoreCaseAscIdAsc(
      @Param("houseIds") Collection<Long> houseIds);
}
