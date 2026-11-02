package com.dendi.peony.repository;

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
   * @param source creation origin recorded on insert ({@code PLANTNET_SCAN})
   * @param createdAt creation timestamp for a newly inserted row
   * @return number of rows inserted or updated (always 1)
   */
  @Modifying(clearAutomatically = true)
  @Query(
      value =
          """
          INSERT INTO plants (scientific_name, name, family, description, source, created_at)
          VALUES (:scientificName, :name, :family, '', :source, :createdAt)
          ON CONFLICT (scientific_name) DO UPDATE
          SET name = COALESCE(NULLIF(btrim(plants.name), ''), EXCLUDED.name),
              family = COALESCE(NULLIF(btrim(plants.family), ''), EXCLUDED.family)
          """,
      nativeQuery = true)
  int upsertScanCatalogueEntry(
      @Param("scientificName") String scientificName,
      @Param("name") String name,
      @Param("family") String family,
      @Param("source") String source,
      @Param("createdAt") LocalDateTime createdAt);

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
