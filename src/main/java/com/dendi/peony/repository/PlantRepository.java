package com.dendi.peony.repository;

import com.dendi.peony.dataset.DatasetSeedEntry;
import com.dendi.peony.entity.Plant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Data access for catalog plants. */
public interface PlantRepository extends JpaRepository<Plant, Long> {

  /**
   * Finds the catalog entry with the given scientific name.
   *
   * @param scientificName scientific name to look up
   * @return the entry, if present
   */
  Optional<Plant> findByScientificName(String scientificName);

  /**
   * Inserts a scan-created catalog entry, or merges into an existing row with the same scientific
   * name in a single statement.
   *
   * <p>The upsert avoids the find-then-insert race: a concurrent insert waits on the unique
   * constraint and then takes the update path instead of aborting the transaction. Only blank
   * common-name and family values are filled from the incoming row; existing values, care fields,
   * and {@code source} are never overwritten.
   *
   * @param scientificName binomial without authority (unique catalog key)
   * @param name common name, or null when unknown
   * @param family botanical family, or null when unknown
   * @param createdAt creation timestamp for a newly inserted row
   * @return number of rows inserted or updated (always 1)
   */
  @Modifying(clearAutomatically = true)
  @Query(
      value =
          """
          INSERT INTO plants (scientific_name, name, family, description, source, created_at)
          VALUES (:scientificName, :name, :family, '', 'PLANTNET_SCAN', :createdAt)
          ON CONFLICT (scientific_name) DO UPDATE
          SET name = COALESCE(NULLIF(btrim(plants.name), ''), EXCLUDED.name),
              family = COALESCE(NULLIF(btrim(plants.family), ''), EXCLUDED.family)
          """,
      nativeQuery = true)
  int upsertScanCatalogueEntry(
      @Param("scientificName") String scientificName,
      @Param("name") String name,
      @Param("family") String family,
      @Param("createdAt") LocalDateTime createdAt);

  /**
   * Inserts a curated catalogue entry, or fills and replaces an existing row with the same
   * scientific name.
   *
   * <p>The insert records {@code source = 'SEED'} and {@code created_at = :createdAt}. The update
   * overwrites every curated identity and care column. That overwrite is intentional: the issue's
   * 2026-11-02 coordination comment says the seed must "fill/replace curated identity and care
   * fields on those rows." Only {@code source} and {@code created_at} are preserved, so a row first
   * created by a scan or manual registration keeps its creation origin (ADR-007). Do not narrow the
   * update to blank-only fields.
   *
   * @param row curated entry; its fields are bound through SpEL
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
              :#{#row.commonName}, :#{#row.scientificName}, :#{#row.family},
              :#{#row.description}, CAST(:#{#row.tags.toArray(new String[0])} AS text[]),
              :#{#row.lightNeeds}, :#{#row.waterFrequencyDays}, :#{#row.humidity},
              :#{#row.tempRange}, :#{#row.toxicToPets}, :#{#row.toxicToHumans},
              :#{#row.difficulty}, :#{#row.nativeRegion}, :#{#row.matureSize}, 'SEED', :createdAt)
          -- Curated identity and care fields intentionally replace scan/manual values (2026-11-02).
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
      @Param("row") DatasetSeedEntry row, @Param("createdAt") LocalDateTime createdAt);

  /**
   * Typahead search over names, scientific names, and tags (issue #10).
   *
   * <p>Names and scientific names match case-insensitively on a substring; tags match whole values
   * through the GIN-indexed array containment operator. A {@code null} query returns the whole
   * catalog.
   *
   * @param q typahead query, or {@code null} for the whole catalog
   * @return matching plants ordered by name
   */
  @Query(
      value =
          """
          SELECT p.* FROM plants p
          WHERE (:q IS NULL
                 OR p.name ILIKE '%' || :q || '%'
                 OR p.scientific_name ILIKE '%' || :q || '%'
                 OR p.tags @> ARRAY[:q]::text[])
          ORDER BY p.name, p.scientific_name
          """,
      nativeQuery = true)
  List<Plant> searchPlants(@Param("q") String q);
}
