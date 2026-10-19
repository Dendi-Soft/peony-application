package com.dendi.peony.repository;

import com.dendi.peony.entity.Room;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** Data access for rooms nested under houses. */
public interface RoomRepository extends JpaRepository<Room, Long> {

  /**
   * Lists the rooms of a house.
   *
   * @param houseId id of the owning house
   * @return the house's rooms
   */
  List<Room> findByHouseId(Long houseId);
}
