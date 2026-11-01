package com.dendi.peony.service.plant;

import com.dendi.peony.api.model.PlantLocationResponse;
import com.dendi.peony.api.model.UserPlantResponse;
import com.dendi.peony.entity.UserPlant;
import com.dendi.peony.entity.UserTier;
import com.dendi.peony.repository.PlantLocationRepository;
import com.dendi.peony.repository.UserPlantRepository;
import com.dendi.peony.repository.UserPlantWithPlant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
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

  /**
   * Lists the caller's user plants, newest first.
   *
   * <p>Plants and their locations are loaded in two joined queries (one for the plants, one for the
   * caller-owned assignments with their rooms and houses), so the query count does not grow with
   * the number of rows.
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

  /**
   * Loads the caller-owned locations of the listed plants in one joined query, grouped by plant.
   */
  private Map<Long, List<PlantLocationResponse>> loadCallerLocations(
      List<UserPlantWithPlant> rows, Long userId) {
    List<Long> plantIds = rows.stream().map(row -> row.plant().getId()).distinct().toList();
    return plantLocationRepository.findByPlantIdsOwnedByUser(plantIds, userId).stream()
        .collect(
            Collectors.groupingBy(
                owned -> owned.location().getPlantId(),
                Collectors.mapping(PlantLocationMapper::toModel, Collectors.toList())));
  }

  private static UserPlantResponse toModel(
      UserPlantWithPlant row, UserTier tier, Map<Long, List<PlantLocationResponse>> locations) {
    UserPlant userPlant = row.userPlant();
    UserPlantResponse model =
        PlantResponseMapper.applyPlantFields(new UserPlantResponse(), row.plant(), tier);
    model.setNickname(userPlant.getNickname());
    model.setLastWateredAt(toWireDateTime(userPlant.getLastWateredAt()));
    model.setNextWaterDueAt(toWireDateTime(userPlant.getNextWaterDueAt()));
    model.setLocations(locations.getOrDefault(row.plant().getId(), List.of()));
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
}
