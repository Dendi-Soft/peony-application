package com.dendi.peony.service.plant;

import com.dendi.peony.api.model.HouseResponse;
import com.dendi.peony.api.model.HouseWithRoomsResponse;
import com.dendi.peony.api.model.PlantLocationResponse;
import com.dendi.peony.api.model.RoomResponse;
import com.dendi.peony.entity.House;
import com.dendi.peony.entity.PlantLocation;
import com.dendi.peony.entity.Room;
import java.util.List;

/**
 * Maps the nested location entities to their wire models.
 *
 * <p>Shared by the location endpoints, the my-plants listing, and the my-houses listing so the
 * location response shape has a single definition.
 */
final class PlantLocationMapper {

  private PlantLocationMapper() {}

  /**
   * Maps a plant assignment with its room and house.
   *
   * @param location plant-to-room assignment
   * @param room the assigned room
   * @param house the room's owning house
   * @return the location response
   */
  static PlantLocationResponse toModel(PlantLocation location, Room room, House house) {
    PlantLocationResponse model = new PlantLocationResponse();
    model.setId(location.getId());
    model.setHouse(toHouseModel(house));
    model.setRoom(toRoomModel(room));
    return model;
  }

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
    model.setRooms(rooms.stream().map(PlantLocationMapper::toRoomModel).toList());
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
