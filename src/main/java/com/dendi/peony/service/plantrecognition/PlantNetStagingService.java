package com.dendi.peony.service.plantrecognition;

import com.dendi.peony.config.PlantNetStagingProperties;
import com.dendi.peony.entity.PlantNetSpeciesImage;
import com.dendi.peony.repository.PlantNetSpeciesImageRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Stores and reads the short-lived PlantNet species-image candidates (issue #45, ADR-008).
 *
 * <p>A recognition stages at most one acceptable candidate per species. Keeping the candidate in a
 * TTL-bounded table rather than on the catalogue row means a scan that is never kept leaves no
 * trace after expiry, while a later scan of the same species can still fill an image-less
 * catalogue entry. Staging is keyed by the normalized binomial; lookups first try the name as
 * given and then its binomial so names carrying authorship still match.
 */
@Service
public class PlantNetStagingService {

  private final PlantNetSpeciesImageRepository repository;

  private final PlantNetStagingProperties properties;

  private final Clock clock;

  /**
   * Creates the staging service.
   *
   * @param repository staging-table data access
   * @param properties staging TTL configuration
   * @param clock clock used for staging timestamps and expiry
   */
  public PlantNetStagingService(
      PlantNetSpeciesImageRepository repository,
      PlantNetStagingProperties properties,
      Clock clock) {
    this.repository = repository;
    this.properties = properties;
    this.clock = clock;
  }

  /**
   * Stages the accepted candidates of one recognition.
   *
   * <p>Expired rows are pruned first, then each candidate is upserted so a species always has one
   * row carrying the newest candidate. An empty candidate list is a no-op: a scan without a usable
   * image must not remove a previously staged candidate, which retires solely through the TTL.
   *
   * @param candidates accepted candidates, possibly empty
   */
  @Transactional
  public void stage(List<RelatedImage> candidates) {
    if (candidates == null || candidates.isEmpty()) {
      return;
    }
    LocalDateTime stagedAt = LocalDateTime.now(clock);
    pruneExpired(stagedAt);
    for (RelatedImage candidate : candidates) {
      repository.upsertStaged(
          candidate.scientificName(),
          candidate.mediumUrl(),
          candidate.organ(),
          candidate.author(),
          candidate.license(),
          candidate.citation(),
          stagedAt);
    }
  }

  /**
   * Finds a still-fresh staged candidate for a scientific name.
   *
   * <p>The name as given is tried first, then its binomial, both normalized. Expired rows are
   * pruned opportunistically before the lookup.
   *
   * @param name scientific name, with or without authorship
   * @return the fresh candidate, if one is staged
   */
  @Transactional
  public Optional<PlantNetSpeciesImage> findFreshForName(String name) {
    LocalDateTime now = LocalDateTime.now(clock);
    pruneExpired(now);
    LocalDateTime cutoff = now.minus(properties.ttl());
    for (String key : lookupKeys(name)) {
      Optional<PlantNetSpeciesImage> fresh =
          repository.findByScientificNameAndStagedAtAfter(key, cutoff);
      if (fresh.isPresent()) {
        return fresh;
      }
    }
    return Optional.empty();
  }

  /**
   * Removes a staged candidate after its image has been persisted.
   *
   * @param staged the consumed candidate
   */
  @Transactional
  public void consume(PlantNetSpeciesImage staged) {
    repository.findById(staged.getId()).ifPresent(repository::delete);
  }

  /**
   * Deletes every staging row older than the configured TTL.
   *
   * @return number of removed rows
   */
  @Transactional
  public long pruneExpired() {
    return pruneExpired(LocalDateTime.now(clock));
  }

  private long pruneExpired(LocalDateTime now) {
    return repository.deleteByStagedAtBefore(now.minus(properties.ttl()));
  }

  private static List<String> lookupKeys(String name) {
    LinkedHashSet<String> keys = new LinkedHashSet<>();
    addKey(keys, ScientificNames.normalize(name));
    addKey(keys, ScientificNames.normalize(ScientificNames.binomial(name)));
    return List.copyOf(keys);
  }

  private static void addKey(LinkedHashSet<String> keys, String key) {
    if (key != null) {
      keys.add(key);
    }
  }
}
