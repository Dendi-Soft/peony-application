package com.dendi.peony.repository;

import com.dendi.peony.entity.UserUploadCounter;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Data access for per-user monthly photo upload counters. */
public interface UserUploadCounterRepository extends JpaRepository<UserUploadCounter, Long> {

  /**
   * Finds the counter for a user and period, locking the row for writing so concurrent uploads
   * increment it one at a time.
   *
   * @param userId user the counter belongs to
   * @param period calendar period ({@code yyyy-MM})
   * @return the counter, if one exists yet
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select c from UserUploadCounter c where c.userId = :userId and c.period = :period")
  Optional<UserUploadCounter> findLockedForUpdate(
      @Param("userId") Long userId, @Param("period") String period);

  /**
   * Finds the counter for a user and period without locking it, for read-only quota snapshots.
   *
   * <p>A concurrent upload may move the counter while it is read; the snapshot may lag by a
   * request, which is acceptable for display-only usage.
   *
   * @param userId user the counter belongs to
   * @param period calendar period ({@code yyyy-MM})
   * @return the counter, if one exists yet
   */
  Optional<UserUploadCounter> findByUserIdAndPeriod(Long userId, String period);
}
