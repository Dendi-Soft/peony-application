package com.dendi.peony.service.plant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Tests the location service at its public seam: creating houses for the caller, creating rooms
 * through caller-owned houses, and the batched, ordered house listing.
 */
@ExtendWith(MockitoExtension.class)
class LocationServiceTest {

  private static final Long USER_ID = 5L;
  private static final Long HOUSE_ID = 20L;
  private static final Long ROOM_ID = 10L;
  private static final LocalDateTime NOW = LocalDateTime.of(2026, 6, 1, 10, 0);

  @Mock private HouseRepository houseRepository;

  @Mock private RoomRepository roomRepository;

  private LocationService locationService;

  @BeforeEach
  void setUp() {
    locationService = new LocationService(houseRepository, roomRepository);
  }

  /** A house is owned by the caller, named from the trimmed payload, and stamped at creation. */
  @Test
  void testCreateHouse_StoresCallerOwnedHouse_WhenNameProvided() {
    when(houseRepository.save(any(House.class)))
        .thenAnswer(
            invocation -> {
              House house = invocation.getArgument(0);
              house.setId(HOUSE_ID);
              return house;
            });

    LocalDateTime before = LocalDateTime.now();
    HouseResponse response =
        locationService.createHouse(USER_ID, new CreateHouseRequest().name("  Garden  "));
    LocalDateTime after = LocalDateTime.now();

    assertEquals(HOUSE_ID, response.getId());
    assertEquals("Garden", response.getName());

    ArgumentCaptor<House> captor = ArgumentCaptor.forClass(House.class);
    verify(houseRepository).save(captor.capture());
    assertEquals(USER_ID, captor.getValue().getUserId());
    assertEquals("Garden", captor.getValue().getName());
    assertFalse(captor.getValue().getCreatedAt().isBefore(before));
    assertFalse(captor.getValue().getCreatedAt().isAfter(after));
  }

  /** A house payload without a name is rejected before touching the database. */
  @Test
  void testCreateHouse_ThrowsIllegalArgument_WhenNameMissing() {
    assertThrows(
        IllegalArgumentException.class,
        () -> locationService.createHouse(USER_ID, new CreateHouseRequest()));
  }

  /** A room is created only through a house that belongs to the caller. */
  @Test
  void testCreateRoom_StoresRoom_WhenHouseOwnedByCaller() {
    when(houseRepository.findByIdAndUserId(HOUSE_ID, USER_ID)).thenReturn(Optional.of(house()));
    when(roomRepository.save(any(Room.class)))
        .thenAnswer(
            invocation -> {
              Room room = invocation.getArgument(0);
              room.setId(ROOM_ID);
              return room;
            });

    LocalDateTime before = LocalDateTime.now();
    RoomResponse response =
        locationService.createRoom(HOUSE_ID, USER_ID, new CreateRoomRequest().name("  Kitchen  "));
    LocalDateTime after = LocalDateTime.now();

    assertEquals(ROOM_ID, response.getId());
    assertEquals("Kitchen", response.getName());
    assertEquals(HOUSE_ID, response.getHouseId());

    ArgumentCaptor<Room> captor = ArgumentCaptor.forClass(Room.class);
    verify(roomRepository).save(captor.capture());
    assertEquals(HOUSE_ID, captor.getValue().getHouseId());
    assertFalse(captor.getValue().getCreatedAt().isBefore(before));
    assertFalse(captor.getValue().getCreatedAt().isAfter(after));
  }

  /** A house owned by another user behaves as not found. */
  @Test
  void testCreateRoom_ThrowsHouseNotFound_WhenHouseNotOwnedByCaller() {
    when(houseRepository.findByIdAndUserId(HOUSE_ID, USER_ID)).thenReturn(Optional.empty());

    assertThrows(
        HouseNotFoundException.class,
        () ->
            locationService.createRoom(HOUSE_ID, USER_ID, new CreateRoomRequest().name("Kitchen")));
  }

  /** Houses nest their own rooms and stay ordered as the repository returns them. */
  @Test
  void testListHousesWithRooms_NestsRoomsUnderEachHouse_WhenCallerHasHouses() {
    House home = house();
    House shed = new House();
    shed.setId(2L);
    shed.setUserId(USER_ID);
    shed.setName("Shed");
    Room kitchen = room(ROOM_ID, HOUSE_ID, "Kitchen");
    Room porch = room(11L, 2L, "Porch");
    when(houseRepository.findOwnedOrderedByName(USER_ID)).thenReturn(List.of(home, shed));
    when(roomRepository.findByHouseIdsOrderedByName(List.of(HOUSE_ID, 2L)))
        .thenReturn(List.of(kitchen, porch));

    List<HouseWithRoomsResponse> houses = locationService.listHousesWithRooms(USER_ID);

    assertEquals(2, houses.size());
    assertEquals(HOUSE_ID, houses.get(0).getId());
    assertEquals(List.of(ROOM_ID), roomsOf(houses.get(0)));
    assertEquals(2L, houses.get(1).getId());
    assertEquals(List.of(11L), roomsOf(houses.get(1)));
  }

  /** A house without rooms carries an empty list. */
  @Test
  void testListHousesWithRooms_ReturnsEmptyRooms_WhenHouseHasNoRooms() {
    when(houseRepository.findOwnedOrderedByName(USER_ID)).thenReturn(List.of(house()));
    when(roomRepository.findByHouseIdsOrderedByName(List.of(HOUSE_ID))).thenReturn(List.of());

    List<HouseWithRoomsResponse> houses = locationService.listHousesWithRooms(USER_ID);

    assertEquals(1, houses.size());
    assertTrue(houses.get(0).getRooms().isEmpty(), "a house without rooms must get an empty list");
  }

  /** A caller with no houses gets an empty list and issues no room query. */
  @Test
  void testListHousesWithRooms_ReturnsEmptyList_WhenCallerHasNoHouses() {
    when(houseRepository.findOwnedOrderedByName(USER_ID)).thenReturn(List.of());

    assertTrue(
        locationService.listHousesWithRooms(USER_ID).isEmpty(),
        "a caller with no houses must get an empty list");
  }

  private static List<Long> roomsOf(HouseWithRoomsResponse house) {
    return house.getRooms().stream().map(RoomResponse::getId).toList();
  }

  private static House house() {
    House house = new House();
    house.setId(HOUSE_ID);
    house.setUserId(USER_ID);
    house.setName("Home");
    house.setCreatedAt(NOW);
    return house;
  }

  private static Room room(Long id, Long houseId, String name) {
    Room room = new Room();
    room.setId(id);
    room.setHouseId(houseId);
    room.setName(name);
    room.setCreatedAt(NOW);
    return room;
  }
}
