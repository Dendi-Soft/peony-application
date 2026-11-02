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
 * A room nested under a house (changeset 005).
 *
 * <p>Maps to the {@code rooms} table. Rooms carry no user of their own: they are scoped through the
 * owning house, and an individual plant optionally points at one room through
 * {@code user_plants.room_id}.
 */
@Entity
@Table(name = "rooms")
@Getter
@Setter
@NoArgsConstructor
public class Room implements Serializable {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "house_id", nullable = false)
  private Long houseId;

  @Column(nullable = false)
  private String name;

  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;
}
