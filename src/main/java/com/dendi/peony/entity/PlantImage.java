package com.dendi.peony.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * Maps a plant to a stored image file (changeset 007).
 *
 * <p>Maps to the {@code plant_images} table. {@code position} is the 0-based gallery index; the
 * image at position 0 doubles as the thumbnail. {@code source} records the image origin and the
 * attribution fields carry the licence and credit; all four are null for rows that predate
 * attribution (changeset 010). The bytes themselves live on disk, not in the database.
 */
@Entity
@Table(name = "plant_images")
@Getter
@Setter
@NoArgsConstructor
public class PlantImage implements Serializable {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "plant_id", nullable = false)
  private Long plantId;

  @Column(nullable = false)
  private Integer position;

  @Column(nullable = false)
  private String filename;

  /** Image origin; null for rows predating attribution. */
  @Enumerated(EnumType.STRING)
  @Column(name = "source")
  private PlantImageSource source;

  /** Image author credit; null when unknown. */
  @Column(name = "author")
  private String author;

  /** Canonical licence of the image; null when unknown. */
  @Column(name = "license")
  private String license;

  /** Attribution citation supplied with the image; null when unknown. */
  @Column(name = "citation")
  private String citation;

  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;
}
