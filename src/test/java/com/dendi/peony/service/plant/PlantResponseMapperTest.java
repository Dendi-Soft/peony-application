package com.dendi.peony.service.plant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.dendi.peony.api.model.ImageSource;
import com.dendi.peony.api.model.PlantImageCredit;
import com.dendi.peony.api.model.PlantResponse;
import com.dendi.peony.api.model.UserPlantResponse;
import com.dendi.peony.entity.Plant;
import com.dendi.peony.entity.PlantImage;
import com.dendi.peony.entity.PlantImageSource;
import com.dendi.peony.entity.PlantSource;
import com.dendi.peony.entity.UserTier;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the image-attribution mapping of {@link PlantResponseMapper} (issue #45): the
 * catalogue credits are ordered by gallery position, legacy rows keep their position but no
 * attribution, and the flattened individual response never carries the credits array.
 */
class PlantResponseMapperTest {

  private static final UserTier TIER = UserTier.FREE;

  /** Credits are ordered ascending by position no matter the input order. */
  @Test
  void testToModel_SortsCreditsAscending_WhenImagesOutOfOrder() {
    Plant plant = plant(42L);
    PlantImage second = image(42L, 1, PlantImageSource.SEED, "Second author");
    PlantImage first = image(42L, 0, PlantImageSource.PLANTNET, "First author");

    PlantResponse response = PlantResponseMapper.toModel(plant, TIER, List.of(second, first));

    assertEquals(2, response.getImageCredits().size());
    assertEquals(0, response.getImageCredits().get(0).getPosition());
    assertEquals(ImageSource.PLANTNET, response.getImageCredits().get(0).getSource());
    assertEquals("First author", response.getImageCredits().get(0).getAuthor());
    assertEquals(1, response.getImageCredits().get(1).getPosition());
    assertEquals(ImageSource.SEED, response.getImageCredits().get(1).getSource());
    assertEquals("Second author", response.getImageCredits().get(1).getAuthor());
  }

  /** A plant without images omits the credits field entirely (null, not an empty list). */
  @Test
  void testToModel_OmitsCredits_WhenPlantHasNoImages() {
    PlantResponse empty = PlantResponseMapper.toModel(plant(42L), TIER, List.of());
    PlantResponse absent = PlantResponseMapper.toModel(plant(42L), TIER, null);

    assertNull(empty.getImageCredits(), "an empty gallery must omit imageCredits");
    assertNull(absent.getImageCredits(), "a null gallery must omit imageCredits");
  }

  /** A legacy row keeps its position but omits every attribution field. */
  @Test
  void testToModel_OmitsAttribution_WhenImagePredatesAttribution() {
    PlantImage legacy = image(42L, 0, null, null);

    PlantResponse response = PlantResponseMapper.toModel(plant(42L), TIER, List.of(legacy));

    PlantImageCredit credit = response.getImageCredits().get(0);
    assertEquals(0, credit.getPosition());
    assertNull(credit.getSource());
    assertNull(credit.getAuthor());
    assertNull(credit.getLicense());
    assertNull(credit.getCitation());
  }

  /** The individual response never carries the credits array the generator flattens into it. */
  @Test
  void testApplyPlantFields_ClearsGeneratedCreditList_WhenTargetIsUserPlantResponse() {
    UserPlantResponse model =
        PlantResponseMapper.applyPlantFields(new UserPlantResponse(), plant(42L), TIER);

    assertNull(
        model.getImageCredits(),
        "UserPlantResponse must not expose the flattened imageCredits array");
  }

  /** A null image has no credit, which keeps the individual field omitted. */
  @Test
  void testToCredit_ReturnsNull_WhenImageAbsent() {
    assertNull(PlantResponseMapper.toCredit(null));
  }

  /** A fully attributed image carries every credit field. */
  @Test
  void testToCredit_MapsAttribution_WhenImageIsPlantNetFallback() {
    PlantImage image = image(42L, 0, PlantImageSource.PLANTNET, "Jane Doe");
    image.setLicense("cc-by-sa");
    image.setCitation("PlantNet citation");

    PlantImageCredit credit = PlantResponseMapper.toCredit(image);

    assertEquals(0, credit.getPosition());
    assertEquals(ImageSource.PLANTNET, credit.getSource());
    assertEquals("Jane Doe", credit.getAuthor());
    assertEquals("cc-by-sa", credit.getLicense());
    assertEquals("PlantNet citation", credit.getCitation());
  }

  private static Plant plant(Long id) {
    Plant plant = new Plant();
    plant.setId(id);
    plant.setScientificName("Ajuga reptans");
    plant.setDescription("");
    plant.setSource(PlantSource.MANUAL);
    plant.setCreatedAt(LocalDateTime.now());
    return plant;
  }

  private static PlantImage image(
      Long plantId, int position, PlantImageSource source, String author) {
    PlantImage image = new PlantImage();
    image.setPlantId(plantId);
    image.setPosition(position);
    image.setFilename("image-" + position + ".jpg");
    image.setSource(source);
    image.setAuthor(author);
    return image;
  }
}
