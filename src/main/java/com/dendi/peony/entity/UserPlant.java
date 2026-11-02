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
 * A physical plant specimen the user tracks: one individual of a catalog plant.
 *
 * <p>Maps to the {@code user_plants} table (changeset 001, individual model in changeset 007). One
 * user may own several individuals of the same catalog plant. {@code nickname} is the individual's
 * display name, {@code roomId} is its single optional location, and {@code lastWateredAt} / {@code
 * nextWaterDueAt} track the watering schedule.
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

  /** The individual's single location; null when it has none. */
  @Column(name = "room_id")
  private Long roomId;

  @Column(name = "last_watered_at")
  private LocalDateTime lastWateredAt;

  @Column(name = "next_water_due_at")
  private LocalDateTime nextWaterDueAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;
}
