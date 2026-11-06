package com.dendi.peony.repository;

import com.dendi.peony.entity.PlantNetSpeciesImage;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Data access for the short-lived PlantNet species-image staging table (issue #45). */
public interface PlantNetSpeciesImageRepository extends JpaRepository<PlantNetSpeciesImage, Long> {

  /**
   * Inserts or replaces the staged candidate for a normalized binomial in a single statement.
   *
   * <p>The upsert keeps the staging table at one row per species: a later recognition of the same
   * species overwrites the URL, credits, and staging timestamp instead of growing the table or
   * racing on the unique constraint.
   *
   * @param scientificName normalized binomial (unique staging key)
   * @param imageUrl absolute medium image URL
   * @param organ organ shown in the image, or null
   * @param author image author credit, or null
   * @param license canonical accepted licence
   * @param citation attribution citation, or null
   * @param stagedAt when the candidate was staged
   * @return number of rows inserted or updated (always 1)
   */
  @Modifying(clearAutomatically = true)
  @Query(
      value =
          """
          INSERT INTO plantnet_species_images
              (scientific_name, image_url, organ, author, license, citation, staged_at)
          VALUES (:scientificName, :imageUrl, :organ, :author, :license, :citation, :stagedAt)
          ON CONFLICT (scientific_name) DO UPDATE
          SET image_url = EXCLUDED.image_url,
              organ = EXCLUDED.organ,
              author = EXCLUDED.author,
              license = EXCLUDED.license,
              citation = EXCLUDED.citation,
              staged_at = EXCLUDED.staged_at
          """,
      nativeQuery = true)
  int upsertStaged(
      @Param("scientificName") String scientificName,
      @Param("imageUrl") String imageUrl,
      @Param("organ") String organ,
      @Param("author") String author,
      @Param("license") String license,
      @Param("citation") String citation,
      @Param("stagedAt") LocalDateTime stagedAt);

  /**
   * Finds a staged candidate that is still fresh.
   *
   * @param scientificName normalized binomial
   * @param cutoff oldest acceptable staging timestamp (exclusive)
   * @return the staged row, if present and fresh
   */
  Optional<PlantNetSpeciesImage> findByScientificNameAndStagedAtAfter(
      String scientificName, LocalDateTime cutoff);

  /**
   * Deletes staging rows older than the TTL.
   *
   * @param cutoff oldest acceptable staging timestamp (exclusive)
   * @return number of deleted rows
   */
  long deleteByStagedAtBefore(LocalDateTime cutoff);
}
