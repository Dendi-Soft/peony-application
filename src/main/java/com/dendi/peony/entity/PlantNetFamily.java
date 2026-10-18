package com.dendi.peony.entity;

import static lombok.AccessLevel.PRIVATE;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * JPA entity representing cached PlantNet family data. PERSISTENT STORAGE: This table stores API
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
@Table(name = "plantnet_families")
public class PlantNetFamily {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  /** Unique taxon identifier from PlantNet API (e.g., "105") */
  @Column(nullable = false, unique = true)
  private String taxonId;

  @Column(nullable = false)
  private Integer rank;

  @Column(nullable = false)
  private String familyName;

  @Column(nullable = false)
  private Integer imageCount;

  /** Timestamp when data was fetched from API */
  @Column(name = "api_timestamp", nullable = false, updatable = false)
  private Instant apiTimestamp;

  /** Last update timestamp */
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  /**
   * Creates a new PlantNetFamily entity.
   *
   * @param taxonId unique identifier from PlantNet API
   * @param rank the taxonomic rank (e.g., family)
   * @param familyName the name of the family
   * @param imageCount number of images available
   * @param apiTimestamp timestamp when data was fetched from API
   * @param updatedAt last update timestamp
   */
  public PlantNetFamily(
      String taxonId,
      Integer rank,
      String familyName,
      Integer imageCount,
      Instant apiTimestamp,
      Instant updatedAt) {
    this.taxonId = taxonId;
    this.rank = rank;
    this.familyName = familyName;
    this.imageCount = imageCount;
    this.apiTimestamp = apiTimestamp != null ? apiTimestamp : Instant.now();
    this.updatedAt = updatedAt != null ? updatedAt : Instant.now();
  }
}
