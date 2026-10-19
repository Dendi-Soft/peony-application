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
 * Maps a plant to a stored image file (changeset 007).
 *
 * <p>Maps to the {@code plant_images} table. {@code position} is the 0-based gallery index; the
 * image at position 0 doubles as the thumbnail. The bytes themselves live on disk, not in the
 * database.
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

  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;
}
