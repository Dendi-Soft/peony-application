package com.dendi.peony.repository;

import com.dendi.peony.entity.Plant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
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
