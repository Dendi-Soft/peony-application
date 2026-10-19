package com.dendi.peony.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.io.Serializable;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Per-user photo upload counter for one calendar month.
 *
 * <p>Maps to the {@code user_upload_counters} table (changeset 003). One row per user per {@code
 * period} (formatted {@code yyyy-MM}); the free-tier quota is enforced against {@code count}.
 */
@Entity
@Table(name = "user_upload_counters")
@Getter
@Setter
@NoArgsConstructor
public class UserUploadCounter implements Serializable {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  /** Calendar month the counter belongs to, formatted as {@code yyyy-MM}. */
  @Column(nullable = false, length = 7)
  private String period;

  /** Number of photo uploads recorded for the user in this period. */
  @Column(nullable = false)
  private int count;

  /**
   * Creates an empty counter for a user and period.
   *
   * @param userId user the counter belongs to
   * @param period calendar period ({@code yyyy-MM})
   */
  public UserUploadCounter(Long userId, String period) {
    this.userId = userId;
    this.period = period;
    this.count = 0;
  }

  /** Records one more upload in this period. */
  public void recordUpload() {
    this.count++;
  }
}
