package com.dendi.peony.service.plant;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.dendi.peony.entity.PlantLocation;
import com.dendi.peony.entity.Room;
import com.dendi.peony.exception.HouseNotFoundException;
import com.dendi.peony.exception.RoomNotFoundException;
import com.dendi.peony.repository.HouseRepository;
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
 * Tests that listing a plant's locations reports missing room or house rows with the 404 domain
 * exceptions instead of an unmapped 500.
 */
@ExtendWith(MockitoExtension.class)
class LocationServiceTest {

  private static final Long PLANT_ID = 1L;
  private static final Long ROOM_ID = 10L;
  private static final Long HOUSE_ID = 20L;

  @Mock private PlantRepository plantRepository;

  @Mock private HouseRepository houseRepository;

  @Mock private RoomRepository roomRepository;

  @Mock private PlantLocationRepository plantLocationRepository;

  @InjectMocks private LocationService locationService;

  /** A stale assignment whose room row is gone must surface as RoomNotFound (404), not 500. */
  @Test
  void testList_ThrowsRoomNotFound_WhenAssignmentRoomIsMissing() {
    when(plantRepository.existsById(PLANT_ID)).thenReturn(true);
    when(plantLocationRepository.findByPlantIdOrderByCreatedAtAsc(PLANT_ID))
        .thenReturn(List.of(location(ROOM_ID)));
    when(roomRepository.findAllById(List.of(ROOM_ID))).thenReturn(List.of());

    assertThrows(RoomNotFoundException.class, () -> locationService.list(PLANT_ID));
  }

  /** A stale assignment whose room's house row is gone must surface as HouseNotFound (404). */
  @Test
  void testList_ThrowsHouseNotFound_WhenHouseOfAssignmentIsMissing() {
    when(plantRepository.existsById(PLANT_ID)).thenReturn(true);
    when(plantLocationRepository.findByPlantIdOrderByCreatedAtAsc(PLANT_ID))
        .thenReturn(List.of(location(ROOM_ID)));
    when(roomRepository.findAllById(List.of(ROOM_ID))).thenReturn(List.of(room(HOUSE_ID)));
    when(houseRepository.findAllById(List.of(HOUSE_ID))).thenReturn(List.of());

    assertThrows(HouseNotFoundException.class, () -> locationService.list(PLANT_ID));
  }

  private static PlantLocation location(Long roomId) {
    PlantLocation location = new PlantLocation();
    location.setRoomId(roomId);
    return location;
  }

  private static Room room(Long houseId) {
    Room room = new Room();
    room.setId(ROOM_ID);
    room.setHouseId(houseId);
    return room;
  }
}
