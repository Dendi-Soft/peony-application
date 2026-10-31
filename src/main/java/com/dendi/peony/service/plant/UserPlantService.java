package com.dendi.peony.service.plant;

import com.dendi.peony.api.model.PlantLocationResponse;
import com.dendi.peony.api.model.UserPlantResponse;
import com.dendi.peony.entity.House;
import com.dendi.peony.entity.PlantLocation;
import com.dendi.peony.entity.Room;
import com.dendi.peony.entity.UserPlant;
import com.dendi.peony.entity.UserTier;
import com.dendi.peony.repository.HouseRepository;
import com.dendi.peony.repository.PlantLocationRepository;
import com.dendi.peony.repository.RoomRepository;
import com.dendi.peony.repository.UserPlantRepository;
import com.dendi.peony.repository.UserPlantWithPlant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lists the authenticated caller's user plants (issue #25).
 *
 * <p>Both tiers see the same set of plants; the tier only shapes which plant fields are visible.
 * The caller's per-user record supplies the nickname and watering dates, and the embedded locations
 * are only those rooms whose house belongs to the caller.
 */
@Service
@RequiredArgsConstructor
public class UserPlantService {

  private final UserPlantRepository userPlantRepository;
  private final PlantLocationRepository plantLocationRepository;
  private final RoomRepository roomRepository;
  private final HouseRepository houseRepository;

  /**
   * Lists the caller's user plants, newest first.
   *
   * <p>Plants and their locations are loaded in batches (one joined query for the plants, one for
   * the caller-owned assignments, then rooms and houses by id), so the query count does not grow
   * with the number of rows.
   *
   * @param userId id of the calling account
   * @param tier tier of the calling account
   * @return the caller's user plants, ordered by user-plant creation timestamp and id descending
   */
  @Transactional(readOnly = true)
  public List<UserPlantResponse> list(Long userId, UserTier tier) {
    List<UserPlantWithPlant> rows = userPlantRepository.findAllWithPlantByUserId(userId);
    if (rows.isEmpty()) {
      return List.of();
    }
    Map<Long, List<PlantLocationResponse>> locations = loadCallerLocations(rows, userId);
    return rows.stream().map(row -> toModel(row, tier, locations)).toList();
  }

  /** Loads the caller-owned locations of the listed plants, grouped by plant id. */
  private Map<Long, List<PlantLocationResponse>> loadCallerLocations(
      List<UserPlantWithPlant> rows, Long userId) {
    List<Long> plantIds = rows.stream().map(row -> row.plant().getId()).distinct().toList();
    List<PlantLocation> locations = plantLocationRepository.findOwnedByPlantIds(plantIds, userId);
    if (locations.isEmpty()) {
      return Map.of();
    }
    Map<Long, Room> rooms = loadRooms(locations);
    Map<Long, House> houses = loadHouses(rooms);
    Map<Long, List<PlantLocationResponse>> byPlant = new HashMap<>();
    for (PlantLocation location : locations) {
      Room room = rooms.get(location.getRoomId());
      House house = room == null ? null : houses.get(room.getHouseId());
      if (room == null || house == null) {
        // The query already joined rooms and houses; a missing row can only be a concurrent
        // delete, and omitting the stale assignment is safe for a read-only listing.
        continue;
      }
      byPlant
          .computeIfAbsent(location.getPlantId(), key -> new ArrayList<>())
          .add(PlantLocationMapper.toModel(location, room, house));
    }
    return byPlant;
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

  private static UserPlantResponse toModel(
      UserPlantWithPlant row, UserTier tier, Map<Long, List<PlantLocationResponse>> locations) {
    UserPlant userPlant = row.userPlant();
    UserPlantResponse model =
        PlantResponseMapper.applyPlantFields(new UserPlantResponse(), row.plant(), tier);
    model.setNickname(userPlant.getNickname());
    model.setLastWateredAt(userPlant.getLastWateredAt());
    model.setNextWaterDueAt(userPlant.getNextWaterDueAt());
    model.setLocations(locations.getOrDefault(row.plant().getId(), List.of()));
    return model;
  }
}
