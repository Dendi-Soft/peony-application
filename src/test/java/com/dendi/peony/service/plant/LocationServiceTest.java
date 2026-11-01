package com.dendi.peony.service.plant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.dendi.peony.api.model.PlantLocationResponse;
import com.dendi.peony.entity.House;
import com.dendi.peony.entity.PlantLocation;
import com.dendi.peony.entity.Room;
import com.dendi.peony.exception.PlantNotFoundException;
import com.dendi.peony.repository.HouseRepository;
import com.dendi.peony.repository.OwnedPlantLocation;
import com.dendi.peony.repository.PlantLocationRepository;
import com.dendi.peony.repository.PlantRepository;
import com.dendi.peony.repository.RoomRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Tests that listing a plant's locations delegates to the caller-scoped query and maps the joined
 * room and house, while keeping the 404 for a plant that does not exist.
 */
@ExtendWith(MockitoExtension.class)
class LocationServiceTest {

  private static final Long PLANT_ID = 1L;
  private static final Long USER_ID = 5L;
  private static final Long ROOM_ID = 10L;
  private static final Long HOUSE_ID = 20L;

  @Mock private PlantRepository plantRepository;

  @Mock private HouseRepository houseRepository;

  @Mock private RoomRepository roomRepository;

  @Mock private PlantLocationRepository plantLocationRepository;

  @InjectMocks private LocationService locationService;

  /**
   * Precondition: the existing plant has one assignment returned by the caller-scoped query. The
   * service maps the joined room and house into the response instead of loading them separately.
   */
  @Test
  void testList_ReturnsCallerOwnedLocation_WhenPlantExistsAndCallerHasAssignment() {
    when(plantRepository.existsById(PLANT_ID)).thenReturn(true);
    when(plantLocationRepository.findByPlantIdsOwnedByUser(List.of(PLANT_ID), USER_ID))
        .thenReturn(List.of(ownedLocation()));

    List<PlantLocationResponse> locations = locationService.list(PLANT_ID, USER_ID);

    assertEquals(1, locations.size(), "the caller's assignment must be listed");
    PlantLocationResponse location = locations.get(0);
    assertEquals(1L, location.getId());
    assertEquals(ROOM_ID, location.getRoom().getId());
    assertEquals("Kitchen", location.getRoom().getName());
    assertEquals(HOUSE_ID, location.getRoom().getHouseId());
    assertEquals(HOUSE_ID, location.getHouse().getId());
    assertEquals("Home", location.getHouse().getName());
  }

  /**
   * Precondition: the plant exists but the caller-scoped query finds no assignment for the caller,
   * even though other users may have assigned the same plant. The service returns an empty list.
   */
  @Test
  void testList_ReturnsEmptyList_WhenPlantExistsButCallerOwnsNoLocations() {
    when(plantRepository.existsById(PLANT_ID)).thenReturn(true);
    when(plantLocationRepository.findByPlantIdsOwnedByUser(List.of(PLANT_ID), USER_ID))
        .thenReturn(List.of());

    assertTrue(locationService.list(PLANT_ID, USER_ID).isEmpty(), "no location may be listed");
  }

  /** A plant that does not exist is still reported as missing (404), not as an empty list. */
  @Test
  void testList_ThrowsPlantNotFound_WhenPlantMissing() {
    when(plantRepository.existsById(PLANT_ID)).thenReturn(false);

    assertThrows(PlantNotFoundException.class, () -> locationService.list(PLANT_ID, USER_ID));
  }

  private static OwnedPlantLocation ownedLocation() {
    PlantLocation location = new PlantLocation();
    location.setId(1L);
    location.setPlantId(PLANT_ID);
    location.setRoomId(ROOM_ID);
    Room room = new Room();
    room.setId(ROOM_ID);
    room.setHouseId(HOUSE_ID);
    room.setName("Kitchen");
    House house = new House();
    house.setId(HOUSE_ID);
    house.setUserId(USER_ID);
    house.setName("Home");
    return new OwnedPlantLocation(location, room, house);
  }
}
