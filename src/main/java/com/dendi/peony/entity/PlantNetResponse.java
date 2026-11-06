package com.dendi.peony.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One aggregated species entry in the PlantNet response ledger (changeset 012, issue #9).
 *
 * <p>Maps to {@code plantnet_responses}. The row is keyed by the normalized, author-free binomial
 * and holds only taxonomy and confidence aggregates. Raw responses and photos are deliberately not
 * retained (ADR-002). The row has no generated identifier: the scientific name is the natural key.
 */
@Entity
@Table(name = "plantnet_responses")
@Getter
@Setter
@NoArgsConstructor
public class PlantNetResponse implements Serializable {

  /** Normalized, author-free binomial used as the ledger key (lowercased). */
  @Id
  @Column(name = "scientific_name", nullable = false)
  private String scientificName;

  /** First non-blank common name supplied by PlantNet; null when none was ever seen. */
  @Column(name = "common_name")
  private String commonName;

  /** Number of recognize responses that named this species as the top hit. */
  @Column(name = "response_count", nullable = false)
  private Long responseCount;

  /** Running arithmetic mean of the top-hit confidence scores (0..1). */
  @Column(name = "avg_confidence", nullable = false)
  private BigDecimal avgConfidence;

  /** When the species first appeared as a top hit. */
  @Column(name = "first_seen", nullable = false)
  private LocalDateTime firstSeen;

  /** When the species last appeared as a top hit. */
  @Column(name = "last_seen", nullable = false)
  private LocalDateTime lastSeen;
}
