package com.dendi.peony.repository;

import com.dendi.peony.entity.PlantNetSpecies;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

/**
 * Repository interface for PlantNet species caching operations. Provides methods to read/write
 * cached species data from PlantNet API.
 */
@Repository
public interface PlantNetSpeciesRepository extends JpaRepository<PlantNetSpecies, Long> {

  /** Find species by taxon ID (unique identifier from PlantNet API). */
  Optional<PlantNetSpecies> findByTaxonId(String taxonId);

  /** Check if species with given taxon ID exists in cache. */
  boolean existsByTaxonId(String taxonId);

  /** Delete species by taxon ID (useful for cache invalidation). */
  void deleteByTaxonId(String taxonId);

  /** Delete all cached species records (clear cache). */
  void deleteAll();

  /** Find species by genus name (case-insensitive partial match). */
  @Query("SELECT s FROM PlantNetSpecies s WHERE LOWER(s.genus) LIKE %:genus%")
  List<PlantNetSpecies> findByGenusContaining(String genus);

  /** Count total cached species. */
  long count();
}
