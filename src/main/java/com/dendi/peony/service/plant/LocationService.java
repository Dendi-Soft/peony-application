package com.dendi.peony.service.plant;

import com.dendi.peony.api.model.CreateHouseRequest;
import com.dendi.peony.api.model.CreateRoomRequest;
import com.dendi.peony.api.model.HouseResponse;
import com.dendi.peony.api.model.HouseWithRoomsResponse;
import com.dendi.peony.api.model.PlantLocationResponse;
import com.dendi.peony.api.model.RoomResponse;
import com.dendi.peony.entity.House;
import com.dendi.peony.entity.PlantLocation;
import com.dendi.peony.entity.Room;
import com.dendi.peony.exception.DuplicateLocationException;
import com.dendi.peony.exception.HouseNotFoundException;
import com.dendi.peony.exception.PlantNotFoundException;
import com.dendi.peony.exception.RoomNotFoundException;
import com.dendi.peony.repository.HouseRepository;
import com.dendi.peony.repository.PlantLocationRepository;
import com.dendi.peony.repository.PlantRepository;
import com.dendi.peony.repository.RoomRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Nested location operations for the v1 location endpoints (issue #10 Phase 2) and the caller's
 * house listing (issue #26).
 *
 * <p>Houses are user-owned; rooms are scoped through house ownership, so a room reachable only
 * through another user's house behaves as not found. Location endpoints are not tier-gated.
 */
@Service
@RequiredArgsConstructor
public class LocationService {

  private final PlantRepository plantRepository;
  private final HouseRepository houseRepository;
  private final RoomRepository roomRepository;
  private final PlantLocationRepository plantLocationRepository;

  /**
   * Creates a house for the calling user.
   *
   * @param plantId plant the house is created for (must exist)
   * @param userId id of the calling account
   * @param request house payload
   * @return the created house
   * @throws PlantNotFoundException when no plant exists with the id
   * @throws IllegalArgumentException when the name is missing
   */
  @Transactional
  public HouseResponse createHouse(Long plantId, Long userId, CreateHouseRequest request) {
    requirePlant(plantId);
    RequiredText.requireText(request.getName(), "name");
    House house = new House();
    house.setUserId(userId);
    house.setName(request.getName().trim());
    house.setCreatedAt(LocalDateTime.now());
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
    room.setCreatedAt(LocalDateTime.now());
    return PlantLocationMapper.toRoomModel(roomRepository.save(room));
  }

  /**
   * Assigns a plant to a room reachable through the calling user's houses.
   *
   * @param plantId id of the plant
   * @param userId id of the calling account
   * @param roomId id of the room to assign
   * @return the created assignment with its nested house and room
   * @throws PlantNotFoundException when no plant exists with the id
   * @throws RoomNotFoundException when no such room belongs to the user's houses
   * @throws DuplicateLocationException when the pair is already assigned
   */
  @Transactional
  public PlantLocationResponse assign(Long plantId, Long userId, Long roomId) {
    requirePlant(plantId);
    Room room =
        roomRepository.findById(roomId).orElseThrow(() -> new RoomNotFoundException(roomId));
    House house =
        houseRepository
            .findByIdAndUserId(room.getHouseId(), userId)
            .orElseThrow(() -> new RoomNotFoundException(roomId));
    if (plantLocationRepository.existsByPlantIdAndRoomId(plantId, room.getId())) {
      throw new DuplicateLocationException(plantId, room.getId());
    }
    PlantLocation location = new PlantLocation();
    location.setPlantId(plantId);
    location.setRoomId(room.getId());
    location.setCreatedAt(LocalDateTime.now());
    return PlantLocationMapper.toModel(plantLocationRepository.save(location), room, house);
  }

  /**
   * Lists the calling user's locations for a plant, oldest first.
   *
   * <p>Plants are shared catalog rows, so the plant's assignments are filtered through the room's
   * house: an assignment under another user's house is never returned. The caller-scoped query
   * returns the room and house with each assignment, so no follow-up lookups are needed.
   *
   * @param plantId id of the plant
   * @param userId id of the calling account
   * @return the caller's assignments, each with its nested house and room
   * @throws PlantNotFoundException when no plant exists with the id
   */
  @Transactional(readOnly = true)
  public List<PlantLocationResponse> list(Long plantId, Long userId) {
    requirePlant(plantId);
    return plantLocationRepository.findByPlantIdOwnedByUser(plantId, userId).stream()
        .map(owned -> PlantLocationMapper.toModel(owned.location(), owned.room(), owned.house()))
        .toList();
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

  private void requirePlant(Long plantId) {
    if (!plantRepository.existsById(plantId)) {
      throw new PlantNotFoundException(plantId);
    }
  }

  private House findOwnedHouse(Long houseId, Long userId) {
    return houseRepository
        .findByIdAndUserId(houseId, userId)
        .orElseThrow(() -> new HouseNotFoundException(houseId));
  }
}
