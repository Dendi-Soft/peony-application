package com.dendi.peony.repository;

import com.dendi.peony.entity.SearchResults;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository interface for PlantNet search results caching operations. Provides methods to
 * read/write cached search results from PlantNet API.
 */
@Repository
public interface SearchResultsRepository extends JpaRepository<SearchResults, Long> {

  /** Find search results by query string (exact match). */
  Optional<SearchResults> findByQuery(String query);

  /** Check if search results for given query exist in cache. */
  boolean existsByQuery(String query);

  /** Delete search results by query (useful for cache invalidation). */
  void deleteByQuery(String query);

  /** Delete all cached search results (clear cache). */
  void deleteAll();

  /** Count total cached search result entries. */
  long count();
}
