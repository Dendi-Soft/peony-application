package com.dendi.peony.entity;

import static java.time.Instant.now;
import static lombok.AccessLevel.PRIVATE;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * JPA entity representing cached PlantNet search results. PERSISTENT STORAGE: This table stores API
 * responses permanently. Not a temporary cache. Cache expiration is handled by L1 caffeine/redis
 * layers, not this database table.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor(access = PRIVATE)
@Builder(toBuilder = true)
@ToString(exclude = "id")
@EqualsAndHashCode(callSuper = false)
@Entity
@Table(name = "plantnet_search_cache")
public class SearchResults {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, length = 500)
  private String query;

  @Column(name = "query_timestamp", nullable = false, updatable = false)
  private Instant queryTimestamp;

  @Column(nullable = false)
  private Integer resultsCount;

  /** JSON string containing search results from PlantNet API */
  @Lob private String searchResultsJson;

  /**
   * Creates a new SearchResults entity.
   *
   * @param query the search query string
   * @param queryTimestamp timestamp of the query
   * @param resultsCount number of results found
   * @param searchResultsJson JSON string containing results
   */
  public SearchResults(
      String query, Instant queryTimestamp, Integer resultsCount, String searchResultsJson) {
    this.query = query;
    this.queryTimestamp = queryTimestamp != null ? queryTimestamp : now();
    this.resultsCount = resultsCount;
    this.searchResultsJson = searchResultsJson;
  }
}
