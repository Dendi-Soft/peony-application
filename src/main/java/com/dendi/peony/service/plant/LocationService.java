package com.dendi.peony.service.plant;

import com.dendi.peony.api.model.CreateHouseRequest;
import com.dendi.peony.api.model.CreateRoomRequest;
import com.dendi.peony.api.model.HouseResponse;
import com.dendi.peony.api.model.HouseWithRoomsResponse;
import com.dendi.peony.api.model.RoomResponse;
import com.dendi.peony.entity.House;
import com.dendi.peony.entity.Room;
import com.dendi.peony.exception.HouseNotFoundException;
import com.dendi.peony.repository.HouseRepository;
import com.dendi.peony.repository.RoomRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * House and room operations for the v1 location endpoints and the caller's house listing.
 *
 * <p>Houses are user-owned; rooms are scoped through house ownership, so a room reachable only
 * through another user's house behaves as not found. Location endpoints are not tier-gated.
 * Individuals are linked to rooms by {@link UserPlantService}, not here.
 */
@Service
@RequiredArgsConstructor
public class LocationService {

  private final HouseRepository houseRepository;
  private final RoomRepository roomRepository;
  private final Clock clock;

  /**
   * Creates a house for the calling user.
   *
   * @param userId id of the calling account
   * @param request house payload
   * @return the created house
   * @throws IllegalArgumentException when the name is missing
   */
  @Transactional
  public HouseResponse createHouse(Long userId, CreateHouseRequest request) {
    RequiredText.requireText(request.getName(), "name");
    House house = new House();
    house.setUserId(userId);
    house.setName(request.getName().trim());
    house.setCreatedAt(LocalDateTime.now(clock));
    return PlantLocationMapper.toHouseModel(houseRepository.save(house));
  }

  /**
   * Creates a room under a house owned by the calling user.
   *
   * @param houseId id of the house the room belongs to
   * @param userId id of the calling account
   * @param request room payload
   * @return the created room
   * @throws HouseNotFoundException when no such house belongs to the user
   * @throws IllegalArgumentException when the name is missing
   */
  @Transactional
  public RoomResponse createRoom(Long houseId, Long userId, CreateRoomRequest request) {
    House house = findOwnedHouse(houseId, userId);
    RequiredText.requireText(request.getName(), "name");
    Room room = new Room();
    room.setHouseId(house.getId());
    room.setName(request.getName().trim());
    room.setCreatedAt(LocalDateTime.now(clock));
    return PlantLocationMapper.toRoomModel(roomRepository.save(room));
  }

  /**
   * Lists the calling user's houses with their rooms nested.
   *
   * <p>Houses are ordered case-insensitively by name; rooms within each house are ordered the same
   * way. Houses without rooms carry an empty list. The rooms of all houses are loaded in one query,
   * so the query count does not grow with the number of houses.
   *
   * @param userId id of the calling account
   * @return the caller's houses with their rooms
   */
  @Transactional(readOnly = true)
  public List<HouseWithRoomsResponse> listHousesWithRooms(Long userId) {
    List<House> houses = houseRepository.findOwnedOrderedByName(userId);
    if (houses.isEmpty()) {
      return List.of();
    }
    Map<Long, List<Room>> roomsByHouse = loadRoomsByHouse(houses);
    return houses.stream()
        .map(
            house ->
                PlantLocationMapper.toHouseWithRoomsModel(
                    house, roomsByHouse.getOrDefault(house.getId(), List.of())))
        .toList();
  }

  /** Loads all rooms in one query so the query count does not grow with the number of houses. */
  private Map<Long, List<Room>> loadRoomsByHouse(List<House> houses) {
    List<Long> houseIds = houses.stream().map(House::getId).toList();
    return roomRepository.findByHouseIdsOrderedByName(houseIds).stream()
        .collect(Collectors.groupingBy(Room::getHouseId));
  }

  private House findOwnedHouse(Long houseId, Long userId) {
    return houseRepository
        .findByIdAndUserId(houseId, userId)
        .orElseThrow(() -> new HouseNotFoundException(houseId));
  }
}
