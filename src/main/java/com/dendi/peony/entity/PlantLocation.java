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
 * Assigns a plant to a room (changeset 006).
 *
 * <p>Maps to the {@code plant_locations} table. A plant may be assigned to several locations; the
 * same plant-room pair cannot be assigned twice (unique constraint).
 */
@Entity
@Table(name = "plant_locations")
@Getter
@Setter
@NoArgsConstructor
public class PlantLocation implements Serializable {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "plant_id", nullable = false)
  private Long plantId;

  @Column(name = "room_id", nullable = false)
  private Long roomId;

  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;
}
