package com.dendi.peony.service.plant;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dendi.peony.api.model.CreatePlantFromScanRequest;
import com.dendi.peony.api.model.UserPlantResponse;
import com.dendi.peony.entity.UserTier;
import org.junit.jupiter.api.Test;

/**
 * Tests the scan registration orchestration (issue #45): the created individual is re-read only
 * when the species-image fill succeeded, and any fill failure still returns the successful scan.
 */
class PlantScanServiceTest {

  private static final long USER_ID = 1L;

  private static final long USER_PLANT_ID = 7L;

  private static final long PLANT_ID = 42L;

  private static final UserTier TIER = UserTier.FREE;

  private final PlantService plantService = mock(PlantService.class);

  private final UserPlantService userPlantService = mock(UserPlantService.class);

  private final SpeciesImageService speciesImageService = mock(SpeciesImageService.class);

  private final PlantScanService service =
      new PlantScanService(plantService, userPlantService, speciesImageService);

  /** A successful fill re-reads the individual so the response carries the image credit. */
  @Test
  void testRegisterFromScan_ReReadsIndividual_WhenImageFilled() {
    CreatePlantFromScanRequest request = mock(CreatePlantFromScanRequest.class);
    UserPlantResponse created = created();
    UserPlantResponse refreshed = new UserPlantResponse();
    when(plantService.registerFromScan(request, USER_ID, TIER)).thenReturn(created);
    when(speciesImageService.fillIfMissing(PLANT_ID)).thenReturn(true);
    when(userPlantService.get(USER_PLANT_ID, USER_ID, TIER)).thenReturn(refreshed);

    assertSame(refreshed, service.registerFromScan(request, USER_ID, TIER));
    verify(userPlantService).get(USER_PLANT_ID, USER_ID, TIER);
  }

  /** No fill means the created individual is returned without an extra read. */
  @Test
  void testRegisterFromScan_ReturnsCreated_WhenNoImageFilled() {
    CreatePlantFromScanRequest request = mock(CreatePlantFromScanRequest.class);
    UserPlantResponse created = created();
    when(plantService.registerFromScan(request, USER_ID, TIER)).thenReturn(created);
    when(speciesImageService.fillIfMissing(PLANT_ID)).thenReturn(false);

    assertSame(created, service.registerFromScan(request, USER_ID, TIER));
    verify(userPlantService, never()).get(any(), any(), any());
  }

  /** A fill crash after the scan commits must not fail the successful registration. */
  @Test
  void testRegisterFromScan_ReturnsCreated_WhenFillThrows() {
    CreatePlantFromScanRequest request = mock(CreatePlantFromScanRequest.class);
    UserPlantResponse created = created();
    when(plantService.registerFromScan(request, USER_ID, TIER)).thenReturn(created);
    when(speciesImageService.fillIfMissing(PLANT_ID)).thenThrow(new IllegalStateException("boom"));

    assertSame(created, service.registerFromScan(request, USER_ID, TIER));
    verify(userPlantService, never()).get(any(), any(), any());
  }

  private static UserPlantResponse created() {
    UserPlantResponse created = new UserPlantResponse();
    created.setId(USER_PLANT_ID);
    created.setPlantId(PLANT_ID);
    return created;
  }
}
