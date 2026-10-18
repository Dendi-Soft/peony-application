package com.dendi.peony.entity;

import static lombok.AccessLevel.PRIVATE;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * JPA entity representing cached PlantNet species data. PERSISTENT STORAGE: This table stores API
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
@Table(name = "plantnet_species")
public class PlantNetSpecies {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  /** Unique taxon identifier from PlantNet API */
  @Column(nullable = false, unique = true)
  private String taxonId;

  @Column(nullable = false)
  private String genus;

  @Column(nullable = false)
  private String speciesName;

  @Column(nullable = false, columnDefinition = "VARCHAR(500)")
  private String scientificName;

  /** Authority of the species (e.g., '(L.) H.Karst.') */
  @Column(length = 255)
  private String authority;

  /** JSON string containing common names with language codes */
  @Lob private String commonNames;

  @Column(nullable = false)
  private boolean isAccepted;

  @Column(name = "family_id", length = 255)
  private String familyId;

  @Column(nullable = false)
  private Integer imageCount;

  /** Timestamp when data was fetched from API */
  @Column(name = "api_timestamp", nullable = false, updatable = false)
  private Instant apiTimestamp;

  /** Last update timestamp */
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  /**
   * Creates a new PlantNetSpecies entity.
   *
   * @param taxonId unique identifier from PlantNet API
   * @param genus the genus name
   * @param speciesName the species epithet
   * @param scientificName full scientific name
   * @param authority taxonomic authority (e.g., "(L.) H.Karst.")
   * @param commonNames JSON string with language:name pairs
   * @param isAccepted whether this is an accepted name
   * @param familyId the family identifier
   * @param imageCount number of images available
   * @param apiTimestamp timestamp when data was fetched from API
   * @param updatedAt last update timestamp
   */
  public PlantNetSpecies(
      String taxonId,
      String genus,
      String speciesName,
      String scientificName,
      String authority,
      String commonNames,
      boolean isAccepted,
      String familyId,
      Integer imageCount,
      Instant apiTimestamp,
      Instant updatedAt) {
    this.taxonId = taxonId;
    this.genus = genus;
    this.speciesName = speciesName;
    this.scientificName = scientificName;
    this.authority = authority;
    this.commonNames = commonNames;
    this.isAccepted = isAccepted;
    this.familyId = familyId;
    this.imageCount = imageCount;
    this.apiTimestamp = apiTimestamp != null ? apiTimestamp : Instant.now();
    this.updatedAt = updatedAt != null ? updatedAt : Instant.now();
  }

  /**
   * Parses common names from JSON string. Returns empty list if commonNames is null, blank, or
   * invalid.
   */
  @Data
  public static class CommonNameParser {
    public static List<String> parseCommonNames(String commonNames) {
      if (commonNames == null || commonNames.isBlank()) {
        return List.of();
      }
      try {
        String[] nameArray = commonNames.split(",");
        var result = new java.util.ArrayList<String>();
        for (String s : nameArray) {
          String trimmed = s.trim();
          if (!trimmed.isEmpty()) {
            int colonIndex = trimmed.indexOf(':');
            if (colonIndex > 0) {
              result.add(trimmed.substring(colonIndex + 1).trim());
            } else {
              result.add(trimmed);
            }
          }
        }
        return result;
      } catch (Exception e) {
        return List.of();
      }
    }
  }
}
