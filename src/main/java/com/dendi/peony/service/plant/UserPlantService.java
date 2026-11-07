package com.dendi.peony.service.plant;

import com.dendi.peony.api.model.CreateUserPlantRequest;
import com.dendi.peony.api.model.UpdateUserPlantRequest;
import com.dendi.peony.api.model.UserPlantResponse;
import com.dendi.peony.entity.House;
import com.dendi.peony.entity.Plant;
import com.dendi.peony.entity.PlantImage;
import com.dendi.peony.entity.Room;
import com.dendi.peony.entity.UserPlant;
import com.dendi.peony.entity.UserPlantImage;
import com.dendi.peony.entity.UserTier;
import com.dendi.peony.exception.PlantNotFoundException;
import com.dendi.peony.exception.RoomNotFoundException;
import com.dendi.peony.exception.UserPlantNotFoundException;
import com.dendi.peony.repository.HouseRepository;
import com.dendi.peony.repository.PlantImageRepository;
import com.dendi.peony.repository.PlantRepository;
import com.dendi.peony.repository.RoomRepository;
import com.dendi.peony.repository.UserPlantProjection;
import com.dendi.peony.repository.UserPlantRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Manages the caller's individual plants (issue #37).
 *
 * <p>Each individual is one physical specimen of a catalog plant, with its own nickname, one
 * optional room, and its own watering state. Every operation is scoped to the caller's user id:
 * foreign or nonexistent individuals behave as not found, and a room is accepted only when it
 * belongs to one of the caller's houses. Both tiers see the same individuals; the tier only shapes
 * which plant fields are visible.
 */
@Service
@RequiredArgsConstructor
public class UserPlantService {

  private static final int NICKNAME_MAX_LENGTH = 255;

  private final UserPlantRepository userPlantRepository;
  private final PlantRepository plantRepository;
  private final PlantImageRepository plantImageRepository;
  private final HouseRepository houseRepository;
  private final RoomRepository roomRepository;
  private final UserPlantImageService userPlantImageService;
  private final Clock clock;

  /**
   * Lists the caller's individuals, newest first.
   *
   * @param userId id of the calling account
   * @param tier tier of the calling account
   * @return the caller's individuals, ordered by creation timestamp and id descending
   */
  @Transactional(readOnly = true)
  public List<UserPlantResponse> list(Long userId, UserTier tier) {
    return userPlantRepository.findAllWithPlantAndLocationByUserId(userId).stream()
        .map(row -> toModel(row, tier))
        .toList();
  }

  /**
   * Loads one caller-owned individual.
   *
   * @param userPlantId individual id
   * @param userId id of the calling account
   * @param tier tier of the calling account
   * @return the individual
   * @throws UserPlantNotFoundException when no such individual belongs to the caller
   */
  @Transactional(readOnly = true)
  public UserPlantResponse get(Long userPlantId, Long userId, UserTier tier) {
    return toModel(findOwnedRow(userPlantId, userId), tier);
  }

  /**
   * Adds an individual for an existing catalog plant.
   *
   * @param userId id of the calling account
   * @param tier tier of the calling account
   * @param request individual payload
   * @return the created individual
   * @throws PlantNotFoundException when no catalog plant exists with the id
   * @throws RoomNotFoundException when the room is missing or outside the caller's houses
   * @throws IllegalArgumentException when the plant id is missing, the nickname is too long, or a
   *     date is malformed
   */
  @Transactional
  public UserPlantResponse create(Long userId, UserTier tier, CreateUserPlantRequest request) {
    if (request.getPlantId() == null) {
      throw new IllegalArgumentException("plantId is required");
    }
    Plant plant =
        plantRepository
            .findById(request.getPlantId())
            .orElseThrow(() -> new PlantNotFoundException(request.getPlantId()));
    return createIndividual(
        plant,
        userId,
        tier,
        request.getNickname(),
        request.getRoomId(),
        request.getLastWateredAt());
  }

  /**
   * Creates one individual for an existing catalog plant with explicit individual state.
   *
   * <p>Used by the my-plants create route and scan-based registration: it parses the optional
   * watering date into the shared persistence path, which resolves the optional caller-owned room,
   * normalizes the nickname, and derives the next due date from the catalog frequency.
   *
   * @param plant catalog plant that was found or created
   * @param userId id of the owning account
   * @param tier tier of the owning account (shapes the response)
   * @param nickname display name; null/blank becomes an empty string
   * @param roomId id of a room in one of the caller's houses, or null for no location
   * @param lastWateredAt wire-format ISO-8601 local date-time, or null for unknown
   * @return the created individual
   * @throws RoomNotFoundException when the room is missing or outside the caller's houses
   * @throws IllegalArgumentException when the nickname is too long or the date is malformed
   */
  @Transactional
  public UserPlantResponse createIndividual(
      Plant plant, Long userId, UserTier tier, String nickname, Long roomId, String lastWateredAt) {
    return persistIndividual(
        plant,
        userId,
        tier,
        new IndividualState(nickname, roomId, parseWateringDate(lastWateredAt)));
  }

  /**
   * Replaces an individual's editable state.
   *
   * <p>The generated request model cannot distinguish an absent field from an explicit null, so
   * every optional field is treated as the new state: a null/blank nickname becomes an empty
   * string, a null room clears the location, and a null last-watered date clears the schedule.
   *
   * @param userPlantId individual id
   * @param userId id of the calling account
   * @param tier tier of the calling account
   * @param request new individual state
   * @return the updated individual
   * @throws UserPlantNotFoundException when no such individual belongs to the caller
   * @throws RoomNotFoundException when the room is missing or outside the caller's houses
   * @throws IllegalArgumentException when the nickname is too long or a date is malformed
   */
  @Transactional
  public UserPlantResponse update(
      Long userPlantId, Long userId, UserTier tier, UpdateUserPlantRequest request) {
    UserPlantProjection row = findOwnedRow(userPlantId, userId);
    UserPlant individual = row.userPlant();
    RoomAndHouse location = resolveLocation(request.getRoomId(), userId);
    LocalDateTime lastWateredAt = parseWateringDate(request.getLastWateredAt());

    individual.setNickname(normalizeNickname(request.getNickname()));
    individual.setRoomId(location == null ? null : location.room().getId());
    individual.setLastWateredAt(lastWateredAt);
    individual.setNextWaterDueAt(nextDue(lastWateredAt, row.plant().getWateringFrequency()));
    individual.setUpdatedAt(LocalDateTime.now(clock));
    userPlantRepository.save(individual);

    return toModel(
        projection(individual, row.plant(), location, row.image(), row.primaryImage()), tier);
  }

  /**
   * Deletes one caller-owned individual, including its personal photo. The catalog plant and its
   * room stay untouched.
   *
   * @param userPlantId individual id
   * @param userId id of the calling account
   * @throws UserPlantNotFoundException when no such individual belongs to the caller
   */
  @Transactional
  public void delete(Long userPlantId, Long userId) {
    UserPlant individual =
        userPlantRepository
            .findByIdAndUserId(userPlantId, userId)
            .orElseThrow(() -> new UserPlantNotFoundException(userPlantId));
    userPlantImageService.deleteForUserPlant(userPlantId);
    userPlantRepository.delete(individual);
  }

  /**
   * Records that the individual was watered now and recomputes the next due date.
   *
   * @param userPlantId individual id
   * @param userId id of the calling account
   * @param tier tier of the calling account
   * @return the updated individual
   * @throws UserPlantNotFoundException when no such individual belongs to the caller
   */
  @Transactional
  public UserPlantResponse water(Long userPlantId, Long userId, UserTier tier) {
    UserPlantProjection row = findOwnedRow(userPlantId, userId);
    UserPlant individual = row.userPlant();
    LocalDateTime now = LocalDateTime.now(clock);

    individual.setLastWateredAt(now);
    individual.setNextWaterDueAt(nextDue(now, row.plant().getWateringFrequency()));
    individual.setUpdatedAt(now);
    userPlantRepository.save(individual);

    RoomAndHouse location =
        row.room() == null || row.house() == null
            ? null
            : new RoomAndHouse(row.room(), row.house());
    return toModel(
        projection(individual, row.plant(), location, row.image(), row.primaryImage()), tier);
  }

  /**
   * Persists one individual for a catalog plant.
   *
   * <p>Shared by the my-plants create route and scan-based registration: it resolves the optional
   * caller-owned room, normalizes the nickname, and derives the next due date from the catalog
   * frequency. When the frequency is unknown, the individual keeps its last-watered date but has no
   * due date.
   */
  private UserPlantResponse persistIndividual(
      Plant plant, Long userId, UserTier tier, IndividualState state) {
    RoomAndHouse location = resolveLocation(state.roomId(), userId);
    LocalDateTime now = LocalDateTime.now(clock);

    UserPlant individual = new UserPlant();
    individual.setUserId(userId);
    individual.setPlantId(plant.getId());
    individual.setNickname(normalizeNickname(state.nickname()));
    individual.setRoomId(location == null ? null : location.room().getId());
    individual.setLastWateredAt(state.lastWateredAt());
    individual.setNextWaterDueAt(nextDue(state.lastWateredAt(), plant.getWateringFrequency()));
    individual.setCreatedAt(now);
    individual.setUpdatedAt(now);
    userPlantRepository.save(individual);

    PlantImage primaryImage =
        plantImageRepository.findFirstByPlantIdOrderByPositionAsc(plant.getId()).orElse(null);
    return toModel(projection(individual, plant, location, null, primaryImage), tier);
  }

  private UserPlantProjection findOwnedRow(Long userPlantId, Long userId) {
    return userPlantRepository
        .findWithPlantAndLocationByIdAndUserId(userPlantId, userId)
        .orElseThrow(() -> new UserPlantNotFoundException(userPlantId));
  }

  /** Pairs an individual with its catalog plant, optional caller-owned location, and photo. */
  private static UserPlantProjection projection(
      UserPlant individual,
      Plant plant,
      RoomAndHouse location,
      UserPlantImage image,
      PlantImage primaryImage) {
    return new UserPlantProjection(
        individual,
        plant,
        location == null ? null : location.room(),
        location == null ? null : location.house(),
        image,
        primaryImage);
  }

  /**
   * Resolves an optional room id to the caller-owned room and house, or null when no room is set.
   */
  private RoomAndHouse resolveLocation(Long roomId, Long userId) {
    if (roomId == null) {
      return null;
    }
    Room room =
        roomRepository.findById(roomId).orElseThrow(() -> new RoomNotFoundException(roomId));
    House house =
        houseRepository
            .findByIdAndUserId(room.getHouseId(), userId)
            .orElseThrow(() -> new RoomNotFoundException(roomId));
    return new RoomAndHouse(room, house);
  }

  private static String normalizeNickname(String nickname) {
    if (nickname == null || nickname.isBlank()) {
      return "";
    }
    String trimmed = nickname.trim();
    if (trimmed.length() > NICKNAME_MAX_LENGTH) {
      throw new IllegalArgumentException(
          "Nickname must be at most " + NICKNAME_MAX_LENGTH + " characters");
    }
    return trimmed;
  }

  private static LocalDateTime nextDue(LocalDateTime lastWateredAt, Integer frequencyDays) {
    if (lastWateredAt == null || frequencyDays == null) {
      return null;
    }
    return lastWateredAt.plusDays(frequencyDays);
  }

  /** Parses the wire format's ISO-8601 local date-time and reports malformed input as a 400. */
  private static LocalDateTime parseWateringDate(String value) {
    if (value == null) {
      return null;
    }
    try {
      return LocalDateTime.parse(value, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    } catch (DateTimeParseException exception) {
      throw new IllegalArgumentException(
          "lastWateredAt must be an ISO-8601 local date-time", exception);
    }
  }

  private static UserPlantResponse toModel(UserPlantProjection row, UserTier tier) {
    UserPlant individual = row.userPlant();
    UserPlantResponse model =
        PlantResponseMapper.applyPlantFields(new UserPlantResponse(), row.plant(), tier);
    model.setId(individual.getId());
    model.setPlantId(row.plant().getId());
    model.setNickname(individual.getNickname());
    model.setHasImage(row.image() != null);
    if (row.image() != null) {
      model.setImageId(row.image().getImageId());
    } else {
      // A personal photo wins: the credit describes the species image only.
      model.setImageCredit(PlantResponseMapper.toCredit(row.primaryImage()));
    }
    model.setLastWateredAt(toWireDateTime(individual.getLastWateredAt()));
    model.setNextWaterDueAt(toWireDateTime(individual.getNextWaterDueAt()));
    if (row.room() != null && row.house() != null) {
      model.setRoom(LocationMapper.toRoomModel(row.room()));
      model.setHouse(LocationMapper.toHouseModel(row.house()));
    }
    return model;
  }

  /**
   * Formats a timestamp as the spec's local date-time string.
   *
   * <p>The API contract types the watering dates as ISO-8601 local date-times without an offset;
   * {@link DateTimeFormatter#ISO_LOCAL_DATE_TIME} matches the TIMESTAMP column and adds no zone.
   */
  private static String toWireDateTime(LocalDateTime value) {
    return value == null ? null : DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(value);
  }

  /** The individual state supplied at creation: display name, optional room, and watering start. */
  private record IndividualState(String nickname, Long roomId, LocalDateTime lastWateredAt) {}

  /** A caller-owned room together with its owning house. */
  private record RoomAndHouse(Room room, House house) {}
}
