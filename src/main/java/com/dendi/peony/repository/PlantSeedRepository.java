package com.dendi.peony.repository;

import com.dendi.peony.entity.Plant;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Data access for the curated-dataset seeder (issue #9). */
public interface PlantSeedRepository extends JpaRepository<Plant, Long> {

  /**
   * Inserts a curated catalogue entry, or enriches an existing row with the same scientific name.
   *
   * <p>The insert records {@code source = 'SEED'} and {@code created_at = :createdAt}. The update
   * overwrites every curated content column but deliberately leaves {@code source} and {@code
   * created_at} untouched, so a row first created by a scan or manual registration keeps that
   * creation origin after curation (ADR-007).
   *
   * @param commonName common display name
   * @param scientificName accepted binomial (unique catalogue key)
   * @param family botanical family
   * @param description curated description
   * @param tags searchable tags, bound as a PostgreSQL {@code text[]}
   * @param lightNeeds curated light guidance
   * @param waterFrequencyDays days between waterings
   * @param humidity curated humidity guidance
   * @param tempRange curated temperature range
   * @param toxicToPets whether the plant is toxic to pets
   * @param toxicToHumans whether the plant is toxic to humans
   * @param difficulty care difficulty
   * @param nativeRegion approximate native region
   * @param matureSize mature size guidance
   * @param createdAt creation timestamp for a newly inserted row
   * @return number of rows inserted or updated (always 1)
   */
  @Modifying(clearAutomatically = true)
  @Query(
      value =
          """
          INSERT INTO plants (
              name, scientific_name, family, description, tags, light_needs,
              watering_frequency, humidity, temp_range, toxic_to_pets, toxic_to_humans,
              difficulty, native_region, mature_size, source, created_at)
          VALUES (
              :commonName, :scientificName, :family, :description, CAST(:tags AS text[]),
              :lightNeeds, :waterFrequencyDays, :humidity, :tempRange, :toxicToPets,
              :toxicToHumans, :difficulty, :nativeRegion, :matureSize, 'SEED', :createdAt)
          ON CONFLICT (scientific_name) DO UPDATE
          SET name = EXCLUDED.name,
              family = EXCLUDED.family,
              description = EXCLUDED.description,
              tags = EXCLUDED.tags,
              light_needs = EXCLUDED.light_needs,
              watering_frequency = EXCLUDED.watering_frequency,
              humidity = EXCLUDED.humidity,
              temp_range = EXCLUDED.temp_range,
              toxic_to_pets = EXCLUDED.toxic_to_pets,
              toxic_to_humans = EXCLUDED.toxic_to_humans,
              difficulty = EXCLUDED.difficulty,
              native_region = EXCLUDED.native_region,
              mature_size = EXCLUDED.mature_size
          """,
      nativeQuery = true)
  int upsertSeedEntry(
      @Param("commonName") String commonName,
      @Param("scientificName") String scientificName,
      @Param("family") String family,
      @Param("description") String description,
      @Param("tags") String[] tags,
      @Param("lightNeeds") String lightNeeds,
      @Param("waterFrequencyDays") Integer waterFrequencyDays,
      @Param("humidity") String humidity,
      @Param("tempRange") String tempRange,
      @Param("toxicToPets") Boolean toxicToPets,
      @Param("toxicToHumans") Boolean toxicToHumans,
      @Param("difficulty") String difficulty,
      @Param("nativeRegion") String nativeRegion,
      @Param("matureSize") String matureSize,
      @Param("createdAt") LocalDateTime createdAt);

  /**
   * Fills the next watering due date for individuals of seeded species.
   *
   * <p>Only individuals with a last-watered date and no due date are scheduled, using the curated
   * watering frequency. An existing due date is never overwritten, so re-running the seeder is
   * idempotent and cannot move a user's schedule (ADR-007).
   *
   * @param seededNames scientific names of the curated species
   * @return number of individuals whose due date was filled
   */
  @Modifying(clearAutomatically = true)
  @Query(
      value =
          """
          UPDATE user_plants up
          SET next_water_due_at = up.last_watered_at
              + (p.watering_frequency * INTERVAL '1 day')
          FROM plants p
          WHERE up.plant_id = p.id
            AND up.last_watered_at IS NOT NULL
            AND up.next_water_due_at IS NULL
            AND p.watering_frequency IS NOT NULL
            AND p.scientific_name IN (:seededNames)
          """,
      nativeQuery = true)
  int backfillNextWaterDueAt(@Param("seededNames") List<String> seededNames);
}
