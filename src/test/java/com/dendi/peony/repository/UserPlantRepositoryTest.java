package com.dendi.peony.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dendi.peony.TestcontainersConfiguration;
import com.dendi.peony.entity.House;
import com.dendi.peony.entity.Plant;
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
 * Repository-level tests for the individual user-plant queries (issue #37), against a real
 * PostgreSQL database: caller scoping, newest-first ordering, duplicate individuals of one catalog
 * plant, catalog-plant pairing, and the caller-owned room/house join.
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

  /**
   * Precondition: the caller has three individuals (two tied timestamps) and another user has one.
   * The query returns only the caller's rows, each paired with its catalog plant, newest first with
   * id descending on ties.
   */
  @Test
  void testFindAllWithPlantByUserId_ReturnsCallerPlantsNewestFirst_WhenAnotherUserHasPlants() {
    User caller = seedUser("t37-repo-list-caller");
    Plant oldest = seedPlant("Monstera t37 repo list oldest");
    Plant tiedFirst = seedPlant("Dracaena t37 repo list tied first");
    Plant tiedSecond = seedPlant("Ficus t37 repo list tied second");
    link(caller, oldest, LocalDateTime.of(2026, 1, 1, 12, 1), null);
    link(caller, tiedFirst, LocalDateTime.of(2026, 1, 1, 12, 2), null);
    link(caller, tiedSecond, LocalDateTime.of(2026, 1, 1, 12, 2), null);
    User other = seedUser("t37-repo-list-other");
    Plant foreign = seedPlant("Aloe t37 repo list foreign");
    link(other, foreign, LocalDateTime.of(2026, 1, 1, 12, 3), null);

    List<UserPlantWithPlantAndLocation> rows =
        userPlantRepository.findAllWithPlantAndLocationByUserId(caller.getId());

    assertEquals(
        List.of(tiedSecond.getId(), tiedFirst.getId(), oldest.getId()),
        rows.stream().map(row -> row.plant().getId()).toList(),
        "only the caller's rows, newest first, with id descending on ties");
    assertEquals(
        List.of(
            "Ficus t37 repo list tied second",
            "Dracaena t37 repo list tied first",
            "Monstera t37 repo list oldest"),
        rows.stream().map(row -> row.plant().getScientificName()).toList(),
        "each row must be paired with its catalog plant");
  }

  /** The same catalog plant may back several individuals; each keeps its own user-plant id. */
  @Test
  void testFindAllWithPlantByUserId_ReturnsDuplicateIndividuals_WhenSamePlantAddedTwice() {
    User caller = seedUser("t37-repo-duplicates");
    Plant plant = seedPlant("Monstera t37 repo duplicates");
    UserPlant first = link(caller, plant, LocalDateTime.of(2026, 1, 1, 12, 1), null);
    UserPlant second = link(caller, plant, LocalDateTime.of(2026, 1, 1, 12, 2), null);

    List<UserPlantWithPlantAndLocation> rows =
        userPlantRepository.findAllWithPlantAndLocationByUserId(caller.getId());

    assertEquals(2, rows.size(), "both individuals of the same catalog plant must be listed");
    assertEquals(
        List.of(second.getId(), first.getId()),
        rows.stream().map(row -> row.userPlant().getId()).toList(),
        "each individual must keep its own user-plant id, newest first");
    assertEquals(
        List.of(plant.getId(), plant.getId()),
        rows.stream().map(row -> row.plant().getId()).toList(),
        "both individuals must resolve the same catalog plant");
  }

  /**
   * Precondition: the caller's individual is in a room of the caller's house. The detail query
   * returns the joined, caller-owned room and house.
   */
  @Test
  void testFindWithPlantAndLocationByIdAndUserId_ReturnsLocation_WhenRoomCallerOwned() {
    User caller = seedUser("t37-repo-detail");
    Plant plant = seedPlant("Monstera t37 repo detail");
    House house = seedHouse(caller, "Caller home");
    Room room = seedRoom(house, "Kitchen");
    UserPlant individual = link(caller, plant, NOW, room.getId());

    UserPlantWithPlantAndLocation row =
        userPlantRepository
            .findWithPlantAndLocationByIdAndUserId(individual.getId(), caller.getId())
            .orElseThrow();

    assertEquals(individual.getId(), row.userPlant().getId());
    assertEquals(plant.getId(), row.plant().getId());
    assertEquals(room.getId(), row.room().getId());
    assertEquals("Kitchen", row.room().getName());
    assertEquals(house.getId(), row.house().getId());
    assertEquals("Caller home", row.house().getName());
  }

  /** Another user's individual is never returned by id, even when the id is known. */
  @Test
  void testFindWithPlantAndLocationByIdAndUserId_ReturnsEmpty_WhenIndividualOwnedByOtherUser() {
    User owner = seedUser("t37-repo-owner");
    User stranger = seedUser("t37-repo-stranger");
    Plant plant = seedPlant("Monstera t37 repo foreign");
    UserPlant individual = link(owner, plant, NOW, null);

    assertTrue(
        userPlantRepository
            .findWithPlantAndLocationByIdAndUserId(individual.getId(), stranger.getId())
            .isEmpty(),
        "a foreign individual must not be returned");
  }

  /**
   * Locations never leak across users: a room under another user's house is not exposed even if the
   * individual's room_id points at it.
   */
  @Test
  void testFindWithPlantAndLocationByIdAndUserId_OmitsForeignRoom_WhenRoomNotCallerOwned() {
    User caller = seedUser("t37-repo-foreign-room-caller");
    User other = seedUser("t37-repo-foreign-room-other");
    Plant plant = seedPlant("Monstera t37 repo foreign room");
    House otherHouse = seedHouse(other, "Other home");
    Room otherRoom = seedRoom(otherHouse, "Other kitchen");
    UserPlant individual = link(caller, plant, NOW, otherRoom.getId());

    UserPlantWithPlantAndLocation row =
        userPlantRepository
            .findWithPlantAndLocationByIdAndUserId(individual.getId(), caller.getId())
            .orElseThrow();

    assertNull(row.room(), "a foreign room must not be joined");
    assertNull(row.house(), "a foreign house must not be joined");
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

  private UserPlant link(User owner, Plant plant, LocalDateTime createdAt, Long roomId) {
    UserPlant link = new UserPlant();
    link.setUserId(owner.getId());
    link.setPlantId(plant.getId());
    link.setNickname("");
    link.setRoomId(roomId);
    link.setLastWateredAt(NOW);
    link.setNextWaterDueAt(NOW.plusDays(7));
    link.setCreatedAt(createdAt);
    link.setUpdatedAt(createdAt);
    return userPlantRepository.save(link);
  }
}
