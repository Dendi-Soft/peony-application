package com.dendi.peony.dto.plantrecognition;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

/**
 * Individual prediction from the PlantNet API; results are ordered best match first.
 *
 * <p>Null fields are omitted from the JSON, which is how the free-tier basic shape drops the
 * premium-only fields (external IDs, taxonomic wrappers).
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class HitResponse {

  /** Confidence score for this hit (0.0 to 1.0). */
  @JsonProperty("score")
  private Double score = 0.0;

  /** Species information including scientific name, genus, family, and common names. */
  @JsonProperty("species")
  private Species species = new Species();

  /** GBIF external taxon ID (e.g., "2868390"). */
  @JsonProperty("gbif")
  private Gbif gbif = new Gbif();

  /** POWO (Plants of the World Online) external concept ID (e.g., "30010088-2"). */
  @JsonProperty("powo")
  private Powo powo = new Powo();

  /** No-arg constructor for Jackson deserialization; fields are populated afterwards. */
  public HitResponse() {
    // Intentionally empty
  }

  /** Species information matching the PlantNet API response structure. */
  @Data
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public static class Species {

    /** Scientific name without authority (e.g., "Caladium bicolor") */
    @JsonProperty("scientificNameWithoutAuthor")
    private String scientificNameWithoutAuthor;

    /** Authority part of the name (e.g., "(Aiton) Vent.") */
    @JsonProperty("scientificNameAuthorship")
    private String scientificNameAuthorship;

    /** Full scientific name including authority (e.g., "Caladium bicolor (Aiton) Vent.") */
    @JsonProperty("scientificName")
    private String scientificName;

    /** Genus information */
    @JsonProperty("genus")
    private Genus genus = new Genus();

    /** Family information */
    @JsonProperty("family")
    private Family family = new Family();

    /** Common names (e.g., ["Heart of Jesus", "Caladium", "Angel wings"]) */
    @JsonProperty("commonNames")
    private List<String> commonNames = new ArrayList<>();
  }

  /** Genus information matching the PlantNet API response structure. */
  @Data
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public static class Genus {

    /** Scientific name without authority (e.g., "Caladium") */
    @JsonProperty("scientificNameWithoutAuthor")
    private String scientificNameWithoutAuthor;

    /** Authorship for the genus (usually empty) */
    @JsonProperty("scientificNameAuthorship")
    private String scientificNameAuthorship;

    /** Full scientific name (e.g., "Caladium") */
    @JsonProperty("scientificName")
    private String scientificName;
  }

  /** Family information matching the PlantNet API response structure. */
  @Data
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public static class Family {

    /** Scientific name without authority (e.g., "Araceae") */
    @JsonProperty("scientificNameWithoutAuthor")
    private String scientificNameWithoutAuthor;

    /** Authorship for the family (usually empty) */
    @JsonProperty("scientificNameAuthorship")
    private String scientificNameAuthorship;

    /** Full scientific name (e.g., "Araceae") */
    @JsonProperty("scientificName")
    private String scientificName;
  }

  /** GBIF external ID wrapper. */
  @Data
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public static class Gbif {

    /** GBIF taxon ID (e.g., "2868390") */
    @JsonProperty("id")
    private String id;
  }

  /** POWO (Plants of the World Online) external ID wrapper. */
  @Data
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public static class Powo {

    /** POWO taxon concept ID (e.g., "30010088-2") */
    @JsonProperty("id")
    private String id;
  }
}
