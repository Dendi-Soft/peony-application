package com.dendi.peony.repository;

import com.dendi.peony.entity.PlantNetResponse;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Data access for the PlantNet response ledger (issue #9). */
public interface PlantNetResponseRepository extends JpaRepository<PlantNetResponse, String> {

  /**
   * Inserts a first ledger row, or folds a sighting into the existing row, in a single statement.
   *
   * <p>The update path increments the response count and keeps the confidence as a running
   * arithmetic mean: {@code (oldMean * oldCount + newScore) / (oldCount + 1)}. The first sighting
   * timestamp is preserved because it is history, while the last sighting timestamp always
   * advances. An existing non-blank common name is preserved; a blank or null one is filled from
   * the incoming hit, since PlantNet only sometimes supplies a common name.
   *
   * @param scientificName normalized, author-free binomial (unique ledger key)
   * @param commonName first non-blank common name from the hit, or null
   * @param confidence top-hit confidence score in 0..1
   * @param now when the sighting was recorded
   * @return number of rows inserted or updated (always 1)
   */
  @Modifying(clearAutomatically = true)
  @Query(
      value =
          """
          INSERT INTO plantnet_responses
              (scientific_name, common_name, response_count, avg_confidence, first_seen, last_seen)
          VALUES (:scientificName, :commonName, 1, :confidence, :now, :now)
          ON CONFLICT (scientific_name) DO UPDATE SET
              response_count = plantnet_responses.response_count + 1,
              avg_confidence = (plantnet_responses.avg_confidence
                                * plantnet_responses.response_count + EXCLUDED.avg_confidence)
                               / (plantnet_responses.response_count + 1),
              common_name = COALESCE(NULLIF(btrim(plantnet_responses.common_name), ''),
                                     EXCLUDED.common_name),
              last_seen = EXCLUDED.last_seen
          """,
      nativeQuery = true)
  int recordResponse(
      @Param("scientificName") String scientificName,
      @Param("commonName") String commonName,
      @Param("confidence") BigDecimal confidence,
      @Param("now") LocalDateTime now);
}
