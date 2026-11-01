package com.dendi.peony.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.dendi.peony.TestcontainersConfiguration;
import com.dendi.peony.entity.House;
import com.dendi.peony.entity.Plant;
import com.dendi.peony.entity.PlantLocation;
import com.dendi.peony.entity.Room;
import com.dendi.peony.entity.User;
import com.dendi.peony.entity.UserTier;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

/**
 * Repository-level tests for the caller-scoped plant location query (issue #32), against a real
 * PostgreSQL database: ownership isolation through the room's house and chronological ordering.
 *
 * <p>Each test runs in a rolled-back transaction, so the shared database keeps no rows between
 * tests. Usernames and scientific names are unique per test to keep fixtures independent.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class PlantLocationRepositoryTest {

  private static final LocalDateTime NOW = LocalDateTime.of(2026, 2, 3, 9, 15, 30);

  @Autowired private PlantLocationRepository plantLocationRepository;

  @Autowired private PlantRepository plantRepository;

  @Autowired private UserRepository userRepository;

  @Autowired private HouseRepository houseRepository;

  @Autowired private RoomRepository roomRepository;

  /**
   * Precondition: two users assigned the same catalog plant to rooms in their own houses. The query
   * returns only the caller's assignment, carrying the joined room and house; the other user's
   * assignment is never visible from either side.
   */
  @Test
  void testFindByPlantIdOwnedByUser_ReturnsOnlyCallerLocations_WhenBothUsersAssignedPlant() {
    User caller = seedUser("t32-repo-loc-caller");
    Plant plant = seedPlant("Monstera t32 repo location");
    House callerHouse = seedHouse(caller, "Caller home");
    Room callerRoom = seedRoom(callerHouse, "Caller kitchen");
    PlantLocation callerLocation = seedLocation(plant, callerRoom, NOW);

    List<OwnedPlantLocation> callerOwned =
        plantLocationRepository.findByPlantIdOwnedByUser(plant.getId(), caller.getId());

    assertEquals(
        List.of(callerLocation.getId()),
        callerOwned.stream().map(owned -> owned.location().getId()).toList(),
        "the caller must see only their own assignment");
    assertEquals(callerRoom.getId(), callerOwned.get(0).room().getId());
    assertEquals("Caller kitchen", callerOwned.get(0).room().getName());
    assertEquals(callerHouse.getId(), callerOwned.get(0).house().getId());
    assertEquals(caller.getId(), callerOwned.get(0).house().getUserId());

    User other = seedUser("t32-repo-loc-other");
    House otherHouse = seedHouse(other, "Other home");
    Room otherRoom = seedRoom(otherHouse, "Other balcony");
    PlantLocation otherLocation = seedLocation(plant, otherRoom, NOW);

    List<OwnedPlantLocation> otherOwned =
        plantLocationRepository.findByPlantIdOwnedByUser(plant.getId(), other.getId());

    assertEquals(
        List.of(otherLocation.getId()),
        otherOwned.stream().map(owned -> owned.location().getId()).toList(),
        "the other user must see only their own assignment");
    assertEquals(otherRoom.getId(), otherOwned.get(0).room().getId());
    assertEquals(otherHouse.getId(), otherOwned.get(0).house().getId());
  }

  /**
   * Precondition: the caller assigned the same plant to three of their rooms, and the last two
   * assignments share a creation timestamp. The query returns the caller's assignments oldest
   * first, breaking timestamp ties by ascending id.
   */
  @Test
  void testFindByPlantIdOwnedByUser_ReturnsChronologicalOrder_WhenCallerHasSeveralAssignments() {
    User caller = seedUser("t32-repo-loc-order");
    Plant plant = seedPlant("Ficus t32 repo order");
    House house = seedHouse(caller, "Home");
    Room oldestRoom = seedRoom(house, "Bedroom");
    Room tiedFirstRoom = seedRoom(house, "Kitchen");
    Room tiedSecondRoom = seedRoom(house, "Balcony");
    PlantLocation oldest = seedLocation(plant, oldestRoom, NOW.minusDays(1));
    PlantLocation tiedFirst = seedLocation(plant, tiedFirstRoom, NOW);
    PlantLocation tiedSecond = seedLocation(plant, tiedSecondRoom, NOW);

    List<OwnedPlantLocation> owned =
        plantLocationRepository.findByPlantIdOwnedByUser(plant.getId(), caller.getId());

    assertEquals(
        List.of(oldest.getId(), tiedFirst.getId(), tiedSecond.getId()),
        owned.stream().map(result -> result.location().getId()).toList(),
        "oldest first, with id ascending on ties");
  }

  private User seedUser(String username) {
    return userRepository.save(
        User.create(username, username + "@example.com", "hash", "Test", "User", UserTier.FREE));
  }

  private Plant seedPlant(String scientificName) {
    Plant plant = new Plant();
    plant.setName("Monstera");
    plant.setScientificName(scientificName);
    plant.setDescription("seeded for repository tests");
    plant.setWateringFrequency(7);
    plant.setCreatedAt(NOW);
    return plantRepository.save(plant);
  }

  private House seedHouse(User owner, String name) {
    House house = new House();
    house.setUserId(owner.getId());
    house.setName(name);
    house.setCreatedAt(NOW);
    return houseRepository.save(house);
  }

  private Room seedRoom(House house, String name) {
    Room room = new Room();
    room.setHouseId(house.getId());
    room.setName(name);
    room.setCreatedAt(NOW);
    return roomRepository.save(room);
  }

  private PlantLocation seedLocation(Plant plant, Room room, LocalDateTime createdAt) {
    PlantLocation location = new PlantLocation();
    location.setPlantId(plant.getId());
    location.setRoomId(room.getId());
    location.setCreatedAt(createdAt);
    return plantLocationRepository.save(location);
  }
}
