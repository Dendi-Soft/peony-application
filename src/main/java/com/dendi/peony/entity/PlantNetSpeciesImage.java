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

/**
 * A short-lived PlantNet species-image candidate (changeset 011, issue #45).
 *
 * <p>Maps to {@code plantnet_species_images}. The row is keyed by the normalized binomial and holds
 * the medium image URL plus its credits. It is staged at recognition time and consumed when a scan
 * of the species fills an image-less catalogue entry; stale rows expire through the configurable
 * staging TTL.
 */
@Entity
@Table(name = "plantnet_species_images")
@Getter
@Setter
@NoArgsConstructor
public class PlantNetSpeciesImage implements Serializable {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  /** Normalized binomial used as the staging key. */
  @Column(name = "scientific_name", nullable = false, unique = true)
  private String scientificName;

  /** Absolute medium-size image URL ({@code url.m}) to download once. */
  @Column(name = "image_url", nullable = false)
  private String imageUrl;

  /** Organ shown in the image; null when the API did not supply one. */
  @Column(name = "organ")
  private String organ;

  /** Image author credit; null when unknown. */
  @Column(name = "author")
  private String author;

  /** Canonical accepted licence. */
  @Column(name = "license", nullable = false)
  private String license;

  /** Attribution citation supplied with the image; null when unknown. */
  @Column(name = "citation")
  private String citation;

  /** When the candidate was staged; drives the TTL cleanup. */
  @Column(name = "staged_at", nullable = false)
  private LocalDateTime stagedAt;
}
