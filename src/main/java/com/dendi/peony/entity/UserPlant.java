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
 * Links a user account to a catalog plant: the user's own record for the plant.
 *
 * <p>Maps to the {@code user_plants} table (changeset 001). {@code nickname} is the per-user note
 * exposed by the notes endpoint (issue #10); {@code lastWateredAt} and {@code nextWaterDueAt} track
 * the watering schedule.
 */
@Entity
@Table(name = "user_plants")
@Getter
@Setter
@NoArgsConstructor
public class UserPlant implements Serializable {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(name = "plant_id", nullable = false)
  private Long plantId;

  @Column(nullable = false)
  private String nickname;

  @Column(name = "last_watered_at", nullable = false)
  private LocalDateTime lastWateredAt;

  @Column(name = "next_water_due_at", nullable = false)
  private LocalDateTime nextWaterDueAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;
}
