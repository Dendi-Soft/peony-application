package com.dendi.peony.service.plant;

import com.dendi.peony.api.model.HouseResponse;
import com.dendi.peony.api.model.HouseWithRoomsResponse;
import com.dendi.peony.api.model.RoomResponse;
import com.dendi.peony.entity.House;
import com.dendi.peony.entity.Room;
import java.util.List;

/**
 * Maps the nested house and room entities to their wire models.
 *
 * <p>Shared by the my-plants, my-houses, and room endpoints so the house and room response shapes
 * have a single definition. An individual's optional location is mapped from its joined room and
 * house.
 */
final class LocationMapper {

  private LocationMapper() {}

  /**
   * Maps a house.
   *
   * @param house the house
   * @return the house response
   */
  static HouseResponse toHouseModel(House house) {
    HouseResponse model = new HouseResponse();
    model.setId(house.getId());
    model.setName(house.getName());
    return model;
  }

  /**
   * Maps a house together with its rooms.
   *
   * @param house the house
   * @param rooms the house's rooms, already ordered
   * @return the house-with-rooms response
   */
  static HouseWithRoomsResponse toHouseWithRoomsModel(House house, List<Room> rooms) {
    HouseWithRoomsResponse model = new HouseWithRoomsResponse();
    model.setId(house.getId());
    model.setName(house.getName());
    model.setRooms(rooms.stream().map(LocationMapper::toRoomModel).toList());
    return model;
  }

  /**
   * Maps a room.
   *
   * @param room the room
   * @return the room response
   */
  static RoomResponse toRoomModel(Room room) {
    RoomResponse model = new RoomResponse();
    model.setId(room.getId());
    model.setName(room.getName());
    model.setHouseId(room.getHouseId());
    return model;
  }
}
