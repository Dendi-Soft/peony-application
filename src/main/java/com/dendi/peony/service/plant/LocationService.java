package com.dendi.peony.service.plant;

import com.dendi.peony.api.model.CreateHouseRequest;
import com.dendi.peony.api.model.CreateRoomRequest;
import com.dendi.peony.api.model.HouseResponse;
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
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Nested location operations for the v1 location endpoints (issue #10 Phase 2).
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
   * Lists a plant's locations, oldest first.
   *
   * <p>Rooms and houses are loaded in batches rather than per row; a stale assignment whose room or
   * house row is gone is reported with the matching 404 domain exception instead of a 500.
   *
   * @param plantId id of the plant
   * @return the assignments, each with its nested house and room
   * @throws PlantNotFoundException when no plant exists with the id
   * @throws RoomNotFoundException when an assigned room row is missing
   * @throws HouseNotFoundException when an assigned room's house row is missing
   */
  @Transactional(readOnly = true)
  public List<PlantLocationResponse> list(Long plantId) {
    requirePlant(plantId);
    List<PlantLocation> locations =
        plantLocationRepository.findByPlantIdOrderByCreatedAtAsc(plantId);
    Map<Long, Room> rooms = loadRooms(locations);
    Map<Long, House> houses = loadHouses(rooms);
    return locations.stream()
        .map(
            location -> {
              Room room = requireRoom(rooms, location);
              House house = requireHouse(houses, room);
              return PlantLocationMapper.toModel(location, room, house);
            })
        .toList();
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

  /** Loads all rooms referenced by the assignments in one query, keyed by id. */
  private Map<Long, Room> loadRooms(List<PlantLocation> locations) {
    List<Long> roomIds = locations.stream().map(PlantLocation::getRoomId).distinct().toList();
    return roomRepository.findAllById(roomIds).stream()
        .collect(Collectors.toMap(Room::getId, Function.identity()));
  }

  /** Loads the houses of the loaded rooms in one query, keyed by id. */
  private Map<Long, House> loadHouses(Map<Long, Room> rooms) {
    List<Long> houseIds = rooms.values().stream().map(Room::getHouseId).distinct().toList();
    return houseRepository.findAllById(houseIds).stream()
        .collect(Collectors.toMap(House::getId, Function.identity()));
  }

  /** Resolves the assignment's room, reporting a stale assignment with the room 404 exception. */
  private static Room requireRoom(Map<Long, Room> rooms, PlantLocation location) {
    Room room = rooms.get(location.getRoomId());
    if (room == null) {
      throw new RoomNotFoundException(location.getRoomId());
    }
    return room;
  }

  /** Resolves the room's house, reporting a stale assignment with the house 404 exception. */
  private static House requireHouse(Map<Long, House> houses, Room room) {
    House house = houses.get(room.getHouseId());
    if (house == null) {
      throw new HouseNotFoundException(room.getHouseId());
    }
    return house;
  }
}
