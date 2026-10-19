package com.dendi.peony.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A plant in the shared catalog.
 *
 * <p>Maps to the {@code plants} table (changeset 001) completed by changeset 002 with the tag,
 * care, toxicity, and growth fields. Entries are created by the curated dataset (#9) or through
 * manual registration (#10); per-user state lives in {@code user_plants}.
 */
@Entity
@Table(name = "plants")
@Getter
@Setter
@NoArgsConstructor
public class Plant implements Serializable {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private String name;

  @Column(name = "scientific_name", nullable = false, unique = true)
  private String scientificName;

  @Column(nullable = false)
  private String description;

  @Column(name = "watering_frequency", nullable = false)
  private Integer wateringFrequency;

  /** Botanical family; nullable because the dataset may have per-plant gaps. */
  private String family;

  /** Searchable tags backed by the GIN-indexed TEXT[] column. */
  @JdbcTypeCode(SqlTypes.ARRAY)
  @Column(name = "tags")
  private String[] tags;

  private String lightNeeds;

  private String humidity;

  private String tempRange;

  @Column(name = "toxic_to_pets")
  private Boolean toPets;

  @Column(name = "toxic_to_humans")
  private Boolean toHumans;

  private String difficulty;

  private String nativeRegion;

  private String matureSize;

  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;
}
