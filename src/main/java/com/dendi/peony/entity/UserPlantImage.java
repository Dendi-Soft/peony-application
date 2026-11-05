package com.dendi.peony.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Maps an individual plant to its personal photo files (changeset 009).
 *
 * <p>Maps to the {@code user_plant_images} table: at most one row per {@code user_plants}
 * individual. {@code imageId} is the immutable client-facing identifier used to version image URLs;
 * {@code filename} and {@code thumbFilename} name the display and thumbnail JPEGs stored under
 * {@code peony.user-images.dir}. The bytes live on disk, not in the database.
 */
@Entity
@Table(name = "user_plant_images")
@Getter
@Setter
@NoArgsConstructor
public class UserPlantImage implements Serializable {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_plant_id", nullable = false, unique = true)
  private Long userPlantId;

  @Column(name = "image_id", nullable = false, unique = true)
  private UUID imageId;

  @Column(nullable = false)
  private String filename;

  @Column(name = "thumb_filename", nullable = false)
  private String thumbFilename;

  @Column(nullable = false)
  private Integer width;

  @Column(nullable = false)
  private Integer height;

  @Column(name = "byte_size", nullable = false)
  private Long byteSize;

  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;
}
