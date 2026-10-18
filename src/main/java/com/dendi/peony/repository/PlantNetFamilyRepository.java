package com.dendi.peony.repository;

import com.dendi.peony.entity.PlantNetFamily;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository interface for PlantNet family caching operations. Provides methods to read/write
 * cached family data from PlantNet API.
 */
@Repository
public interface PlantNetFamilyRepository extends JpaRepository<PlantNetFamily, Long> {

  /** Find family by taxon ID (unique identifier from PlantNet API). */
  Optional<PlantNetFamily> findByTaxonId(String taxonId);

  /** Check if family with given taxon ID exists in cache. */
  boolean existsByTaxonId(String taxonId);

  /** Delete family by taxon ID (useful for cache invalidation). */
  void deleteByTaxonId(String taxonId);

  /** Delete all cached family records (clear cache). */
  void deleteAll();

  /** Count total cached families. */
  long count();
}
