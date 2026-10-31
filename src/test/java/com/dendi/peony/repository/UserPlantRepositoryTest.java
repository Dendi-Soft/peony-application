package com.dendi.peony.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.dendi.peony.TestcontainersConfiguration;
import com.dendi.peony.entity.House;
import com.dendi.peony.entity.Plant;
import com.dendi.peony.entity.PlantLocation;
import com.dendi.peony.entity.Room;
import com.dendi.peony.entity.User;
import com.dendi.peony.entity.UserPlant;
import com.dendi.peony.entity.UserTier;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

/**
 * Repository-level tests for the user-plant listing queries (issue #25), against a real PostgreSQL
 * database: caller scoping, ordering, catalog-plant pairing, caller-owned location joins, and the
 * idempotent link upsert.
 *
 * <p>Each test runs in a rolled-back transaction, so the shared database keeps no rows between
 * tests. Usernames and scientific names are unique per test to keep fixtures independent.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class UserPlantRepositoryTest {

  private static final LocalDateTime NOW = LocalDateTime.of(2026, 1, 2, 8, 30, 15);

  @Autowired private UserPlantRepository userPlantRepository;

  @Autowired private PlantRepository plantRepository;

  @Autowired private UserRepository userRepository;

  @Autowired private HouseRepository houseRepository;

  @Autowired private RoomRepository roomRepository;

  @Autowired private PlantLocationRepository plantLocationRepository;

  /**
   * Precondition: two users own separate houses and both linked the same catalog plant. The query
   * returns only the caller's assignment, carrying the joined room and house.
   */
  @Test
  void testFindByPlantIdsOwnedByUser_ReturnsCallerOwnedLocation_WhenBothUsersLinkedSamePlant() {
    User caller = seedUser("t25-repo-loc-caller");
    User other = seedUser("t25-repo-loc-other");
    Plant plant = seedPlant("Monstera t25 repo location");
    House callerHouse = seedHouse(caller, "Caller home");
    Room callerRoom = seedRoom(callerHouse, "Caller kitchen");
    House otherHouse = seedHouse(other, "Other home");
    Room otherRoom = seedRoom(otherHouse, "Other balcony");
    PlantLocation callerLocation = seedLocation(plant, callerRoom);
    seedLocation(plant, otherRoom);

    List<OwnedPlantLocation> owned =
        plantLocationRepository.findByPlantIdsOwnedByUser(List.of(plant.getId()), caller.getId());

    assertEquals(1, owned.size(), "only the caller's assignment may be returned");
    OwnedPlantLocation result = owned.get(0);
    assertEquals(callerLocation.getId(), result.location().getId());
    assertEquals(callerRoom.getId(), result.room().getId());
    assertEquals("Caller kitchen", result.room().getName());
    assertEquals(callerHouse.getId(), result.house().getId());
    assertEquals("Caller home", result.house().getName());
    assertEquals(caller.getId(), result.house().getUserId());
  }

  /**
   * Precondition: the caller has three links at controlled timestamps, two of them tied, and
   * another user has a link. The query returns only the caller's rows, each paired with its catalog
   * plant, newest first with id descending on ties.
   */
  @Test
  void testFindAllWithPlantByUserId_ReturnsCallerPlantsNewestFirst_WhenAnotherUserHasLinks() {
    User caller = seedUser("t25-repo-list-caller");
    Plant oldest = seedPlant("Monstera t25 repo list oldest");
    Plant tiedFirst = seedPlant("Dracaena t25 repo list tied first");
    Plant tiedSecond = seedPlant("Ficus t25 repo list tied second");
    link(caller, oldest, LocalDateTime.of(2026, 1, 1, 12, 1));
    link(caller, tiedFirst, LocalDateTime.of(2026, 1, 1, 12, 2));
    link(caller, tiedSecond, LocalDateTime.of(2026, 1, 1, 12, 2));
    User other = seedUser("t25-repo-list-other");
    Plant foreign = seedPlant("Aloe t25 repo list foreign");
    link(other, foreign, LocalDateTime.of(2026, 1, 1, 12, 3));

    List<UserPlantWithPlant> rows = userPlantRepository.findAllWithPlantByUserId(caller.getId());

    assertEquals(
        List.of(tiedSecond.getId(), tiedFirst.getId(), oldest.getId()),
        rows.stream().map(row -> row.plant().getId()).toList(),
        "only the caller's rows, newest first, with id descending on ties");
    assertEquals(
        List.of(
            "Ficus t25 repo list tied second",
            "Dracaena t25 repo list tied first",
            "Monstera t25 repo list oldest"),
        rows.stream().map(row -> row.plant().getScientificName()).toList(),
        "each row must be paired with its catalog plant");
  }

  /**
   * Precondition: the caller has no link to the catalog plant. Calling the upsert twice leaves one
   * link with the first call's values, so concurrent registrations cannot create duplicates.
   */
  @Test
  void testInsertLinkIfAbsent_LeavesOneLink_WhenCalledTwice() {
    User caller = seedUser("t25-repo-upsert");
    Plant plant = seedPlant("Monstera t25 repo upsert");

    userPlantRepository.insertLinkIfAbsent(caller.getId(), plant.getId(), NOW, NOW.plusDays(7));
    userPlantRepository.insertLinkIfAbsent(caller.getId(), plant.getId(), NOW, NOW.plusDays(14));

    List<UserPlant> links =
        userPlantRepository.findAll().stream()
            .filter(link -> link.getUserId().equals(caller.getId()))
            .toList();
    assertEquals(1, links.size(), "the second insert must be a no-op");
    assertEquals("", links.get(0).getNickname());
    assertEquals(NOW, links.get(0).getLastWateredAt());
    assertEquals(NOW.plusDays(7), links.get(0).getNextWaterDueAt());
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

  private PlantLocation seedLocation(Plant plant, Room room) {
    PlantLocation location = new PlantLocation();
    location.setPlantId(plant.getId());
    location.setRoomId(room.getId());
    location.setCreatedAt(NOW);
    return plantLocationRepository.save(location);
  }

  private void link(User owner, Plant plant, LocalDateTime createdAt) {
    UserPlant link = new UserPlant();
    link.setUserId(owner.getId());
    link.setPlantId(plant.getId());
    link.setNickname("");
    link.setLastWateredAt(NOW);
    link.setNextWaterDueAt(NOW.plusDays(7));
    link.setCreatedAt(createdAt);
    link.setUpdatedAt(createdAt);
    userPlantRepository.save(link);
  }
}
