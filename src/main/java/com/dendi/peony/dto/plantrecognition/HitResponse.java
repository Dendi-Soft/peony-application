package com.dendi.peony.dto.plantrecognition;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.Data;

/** Individual prediction result from PlantNet API. */
@Data
public class HitResponse {

  /** Confidence score for this hit (0.0 to 1.0) - from PlantNet API response */
  @JsonProperty("score")
  private Double score;

  /** Species information including scientific name, genus, family, common names */
  @JsonProperty("species")
  private Species species = new Species();

  /** GBIF external ID (e.g., "2868390") */
  @JsonProperty("gbif")
  private Gbif gbif = new Gbif();

  /** POWO (Plants of the World Online) external ID (e.g., "30010088-2") */
  @JsonProperty("powo")
  private Powo powo = new Powo();

  // Default constructor with nulls for Jackson deserialization
  public HitResponse() {
    this.score = 0.0;
  }

  /** Constructor with all fields set. */
  public HitResponse(Double score, Species species) {
    this.score = score;
    if (species == null) {
      this.species = new Species();
    } else {
      this.species = species;
    }
  }

  /** Builder class for fluent API construction. */
  public static class Builder {
    private Double score;
    private Species species = new Species();

    /** Set confidence score (fluent API). */
    public Builder withScore(Double score) {
      this.score = score;
      return this;
    }

    /** Set full scientific name (fluent API). */
    public Builder withScientificName(String scientificName) {
      if (this.species == null || this.species.scientificName == null) {
        this.species = new Species();
      }
      this.species.setScientificName(Objects.requireNonNullElse(scientificName, ""));
      return this;
    }

    /** Build the HitResponse object. */
    public HitResponse build() {
      if (score == null) {
        score = 0.0;
      }
      if (this.species == null || this.species.scientificName.isEmpty()) {
        return new HitResponse(score, new Species());
      }

      return new HitResponse(score, this.species);
    }
  }

  /** Set confidence score. */
  public void setScore(Double score) {
    this.score = score;
  }

  /** Set species information. */
  public void setSpecies(Species species) {
    if (species == null) {
      this.species = new Species();
    } else {
      this.species = species;
    }
  }

  /** Nested species information matching PlantNet API response structure. */
  @Data
  public static class Species {

    /** Scientific name without authority (e.g., "Caladium bicolor") */
    @JsonProperty("scientificNameWithoutAuthor")
    private String scientificNameWithoutAuthor;

    /** Scientific name with authority (e.g., "(Aiton) Vent.") */
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

  /** Genus information matching PlantNet API response structure. */
  @Data
  public static class Genus {

    /** Scientific name without authority (e.g., "Caladium") */
    @JsonProperty("scientificNameWithoutAuthor")
    private String scientificNameWithoutAuthor;

    /** Authorship for genus (usually empty) */
    @JsonProperty("scientificNameAuthorship")
    private String scientificNameAuthorship;

    /** Full scientific name (e.g., "Caladium") */
    @JsonProperty("scientificName")
    private String scientificName;
  }

  /** Family information matching PlantNet API response structure. */
  @Data
  public static class Family {

    /** Scientific name without authority (e.g., "Araceae") */
    @JsonProperty("scientificNameWithoutAuthor")
    private String scientificNameWithoutAuthor;

    /** Authorship for family (usually empty) */
    @JsonProperty("scientificNameAuthorship")
    private String scientificNameAuthorship;

    /** Full scientific name (e.g., "Araceae") */
    @JsonProperty("scientificName")
    private String scientificName;
  }

  /** GBIF external ID wrapper. */
  @Data
  public static class Gbif {

    /** GBIF taxon ID (e.g., "2868390") */
    @JsonProperty("id")
    private String id;
  }

  /** POWO (Plants of the World Online) external ID wrapper. */
  @Data
  public static class Powo {

    /** POWO taxon concept ID (e.g., "30010088-2") */
    @JsonProperty("id")
    private String id;
  }
}
