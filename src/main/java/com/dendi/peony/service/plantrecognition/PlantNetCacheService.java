package com.dendi.peony.service.plantrecognition;

import com.dendi.peony.entity.PlantNetFamily;
import com.dendi.peony.entity.PlantNetSpecies;
import com.dendi.peony.entity.SearchResults;
import com.dendi.peony.repository.PlantNetFamilyRepository;
import com.dendi.peony.repository.PlantNetSpeciesRepository;
import com.dendi.peony.repository.SearchResultsRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * PlantNet API cache service with multiple caching strategies: 1. L1: In-memory Caffeine cache
 * (fast, local, TTL-based expiration) 2. L2: Database-backed PERSISTENT STORAGE (permanent until
 * explicitly deleted)
 *
 * <p>Important: Database storage is NOT temporary - TTL applies ONLY to L1 caffeine/redis caches.
 */
@Service
public class PlantNetCacheService {

  private final ObjectMapper objectMapper;

  @Getter private final PlantNetSpeciesRepository speciesRepository;

  @Getter private final PlantNetFamilyRepository familyRepository;

  @Getter private final SearchResultsRepository searchResultsRepository;

  @Value("${spring.cache.db-backed.enabled:true}")
  private boolean dbBackedEnabled;

  /** In-memory cache using Caffeine for fast reads */
  @Getter private final ConcurrentHashMap<String, String> caffeineCache = new ConcurrentHashMap<>();

  /**
   * Constructor - injected by Spring framework.
   *
   * @param objectMapper JSON serialization/deserialization utility
   * @param speciesRepository Repository for PlantNetSpecies entities
   * @param familyRepository Repository for PlantNetFamily entities
   * @param searchResultsRepository Repository for SearchResults entities
   */
  public PlantNetCacheService(
      ObjectMapper objectMapper,
      PlantNetSpeciesRepository speciesRepository,
      PlantNetFamilyRepository familyRepository,
      SearchResultsRepository searchResultsRepository) {
    this.objectMapper = objectMapper;
    this.speciesRepository = speciesRepository;
    this.familyRepository = familyRepository;
    this.searchResultsRepository = searchResultsRepository;
  }

  /**
   * Gets cached species data. Tries multiple caching strategies in order: 1. Caffeine (fastest) 2.
   * Database-backed cache 3. Returns null to indicate cache miss (API call should be made)
   */
  public String getCachedSpecies(String taxonId) {
    // Try caffeine cache first (fastest)
    String cached = caffeineCache.get(taxonId);
    if (cached != null) {
      return cached;
    }

    // Try database-backed cache
    if (dbBackedEnabled) {
      var speciesOpt = speciesRepository.findByTaxonId(taxonId);
      if (speciesOpt.isPresent()) {
        PlantNetSpecies species = speciesOpt.get();
        try {
          String json = writeValueAsString(species);
          caffeineCache.put(taxonId, json);
          return json;
        } catch (IOException e) {
          throw new RuntimeException("Failed to serialize cached species", e);
        }
      }
    }

    return null; // Cache miss - API call needed
  }

  private String writeValueAsString(Object obj) throws IOException {
    try {
      return objectMapper.writeValueAsString(obj);
    } catch (Exception e) {
      throw new IOException("Serialization error", e);
    }
  }

  /** Gets cached family data. */
  public String getCachedFamily(String taxonId) {
    // Try caffeine cache first
    String cached = caffeineCache.get("plantnet:family:" + taxonId);
    if (cached != null) {
      return cached;
    }

    // Try database-backed cache
    if (dbBackedEnabled) {
      var familyOpt = familyRepository.findByTaxonId(taxonId);
      if (familyOpt.isPresent()) {
        PlantNetFamily family = familyOpt.get();
        try {
          String json = writeValueAsString(family);
          caffeineCache.put("plantnet:family:" + taxonId, json);
          return json;
        } catch (IOException e) {
          throw new RuntimeException("Failed to serialize cached family", e);
        }
      }
    }

    return null; // Cache miss - API call needed
  }

  /** Gets cached search results. */
  public String getCachedSearch(String query) {
    // Try caffeine cache first
    String cached = caffeineCache.get("plantnet:search:" + query);
    if (cached != null) {
      return cached;
    }

    // Try database-backed cache
    if (dbBackedEnabled) {
      var resultsOpt = searchResultsRepository.findByQuery(query);
      if (resultsOpt.isPresent()) {
        SearchResults results = resultsOpt.get();
        try {
          String json = writeValueAsString(results);
          caffeineCache.put("plantnet:search:" + query, json);
          return json;
        } catch (IOException e) {
          throw new RuntimeException("Failed to serialize cached search results", e);
        }
      }
    }

    return null; // Cache miss - API call needed
  }

  /** Caches species data from PlantNet API response. */
  public void cacheSpecies(String taxonId, String speciesResponse) throws IOException {
    // Deserialize to entity for database storage
    PlantNetSpecies species = objectMapper.readValue(speciesResponse, PlantNetSpecies.class);

    // Save to database
    if (dbBackedEnabled) {
      speciesRepository.save(species);
    }

    // Save to caffeine cache
    String json = objectMapper.writeValueAsString(species);
    caffeineCache.put(taxonId, json);
  }

  /** Caches family data from PlantNet API response. */
  public void cacheFamily(String taxonId, String familyResponse) throws IOException {
    // Deserialize to entity for database storage
    PlantNetFamily family = objectMapper.readValue(familyResponse, PlantNetFamily.class);

    // Save to database
    if (dbBackedEnabled) {
      familyRepository.save(family);
    }

    // Save to caffeine cache
    String json = objectMapper.writeValueAsString(family);
    caffeineCache.put("plantnet:family:" + taxonId, json);
  }

  /** Caches search results from PlantNet API response. */
  public void cacheSearch(String query, String searchResultsJson) throws IOException {
    // Deserialize to entity for database storage
    SearchResults results = objectMapper.readValue(searchResultsJson, SearchResults.class);

    // Save to database
    if (dbBackedEnabled) {
      searchResultsRepository.save(results);
    }

    // Save to caffeine cache
    String json = objectMapper.writeValueAsString(results);
    caffeineCache.put("plantnet:search:" + query, json);
  }

  /** Clears all caches. Use for cache invalidation scenarios. */
  public void clearAllCaches() {
    caffeineCache.clear();

    if (dbBackedEnabled) {
      speciesRepository.deleteAll();
      familyRepository.deleteAll();
      searchResultsRepository.deleteAll();
    }
  }

  /** Clears caffeine in-memory cache only. */
  public void clearCaffeineCache() {
    caffeineCache.clear();
  }
}
