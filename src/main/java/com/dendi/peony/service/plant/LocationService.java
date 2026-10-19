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
    requireText(request.getName(), "name");
    House house = new House();
    house.setUserId(userId);
    house.setName(request.getName().trim());
    house.setCreatedAt(LocalDateTime.now());
    return toHouseModel(houseRepository.save(house));
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
    requireText(request.getName(), "name");
    Room room = new Room();
    room.setHouseId(house.getId());
    room.setName(request.getName().trim());
    room.setCreatedAt(LocalDateTime.now());
    return toRoomModel(roomRepository.save(room));
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
    Room room = findOwnedRoom(roomId, userId);
    if (plantLocationRepository.existsByPlantIdAndRoomId(plantId, room.getId())) {
      throw new DuplicateLocationException(plantId, room.getId());
    }
    PlantLocation location = new PlantLocation();
    location.setPlantId(plantId);
    location.setRoomId(room.getId());
    location.setCreatedAt(LocalDateTime.now());
    return toLocationModel(plantLocationRepository.save(location));
  }

  /**
   * Lists a plant's locations, oldest first.
   *
   * @param plantId id of the plant
   * @return the assignments, each with its nested house and room
   * @throws PlantNotFoundException when no plant exists with the id
   */
  @Transactional(readOnly = true)
  public List<PlantLocationResponse> list(Long plantId) {
    requirePlant(plantId);
    return plantLocationRepository.findByPlantIdOrderByCreatedAtAsc(plantId).stream()
        .map(this::toLocationModel)
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

  /**
   * Loads a room only when its house belongs to the user; otherwise the room behaves as missing.
   */
  private Room findOwnedRoom(Long roomId, Long userId) {
    Room room =
        roomRepository.findById(roomId).orElseThrow(() -> new RoomNotFoundException(roomId));
    houseRepository
        .findByIdAndUserId(room.getHouseId(), userId)
        .orElseThrow(() -> new RoomNotFoundException(roomId));
    return room;
  }

  private static void requireText(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(field + " is required");
    }
  }

  private static HouseResponse toHouseModel(House house) {
    HouseResponse model = new HouseResponse();
    model.setId(house.getId());
    model.setName(house.getName());
    return model;
  }

  private static RoomResponse toRoomModel(Room room) {
    RoomResponse model = new RoomResponse();
    model.setId(room.getId());
    model.setName(room.getName());
    model.setHouseId(room.getHouseId());
    return model;
  }

  private PlantLocationResponse toLocationModel(PlantLocation location) {
    Room room = roomRepository.findById(location.getRoomId()).orElseThrow();
    House house = houseRepository.findById(room.getHouseId()).orElseThrow();
    PlantLocationResponse model = new PlantLocationResponse();
    model.setId(location.getId());
    model.setHouse(toHouseModel(house));
    model.setRoom(toRoomModel(room));
    return model;
  }
}
