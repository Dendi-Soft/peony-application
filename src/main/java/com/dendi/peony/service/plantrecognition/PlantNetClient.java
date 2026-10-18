package com.dendi.peony.service.plantrecognition;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import lombok.Setter;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * PlantNet API client for plant species recognition.
 *
 * <p>Official API: https://api.plantnet.org/
 *
 * <p>This implementation uses OkHttp to interact with the PlantNet REST API for image upload and
 * species identification. Supports optional caching via PlantNetCacheService.
 */
public class PlantNetClient {

  private static final String BASE_URL = "https://api.plantnet.org/v2";
  private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

  @Setter private final OkHttpClient httpClient;

  @Setter private final boolean useAuthentication;

  @Setter private final String apiKey;

  @Setter private PlantNetCacheService cacheService;

  public PlantNetClient() {
    this(false, null);
  }

  public PlantNetClient(boolean useAuthentication, String apiKey) {
    this(useAuthentication, apiKey, null);
  }

  /**
   * Creates a PlantNet client with optional caching.
   *
   * @param cacheService Optional cache service for rate limit mitigation
   */
  public PlantNetClient(PlantNetCacheService cacheService) {
    this(false, null, cacheService);
  }

  /** Creates a PlantNet client with authentication and optional caching. */
  public PlantNetClient(
      boolean useAuthentication, String apiKey, PlantNetCacheService cacheService) {
    this.useAuthentication = useAuthentication;
    this.apiKey = apiKey;
    this.cacheService = cacheService;

    OkHttpClient.Builder builder =
        new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS);

    if (useAuthentication && apiKey != null && !apiKey.isEmpty()) {
      builder.addInterceptor(
          chain ->
              chain.proceed(
                  chain
                      .request()
                      .newBuilder()
                      .addHeader("Authorization", "Bearer " + apiKey)
                      .addHeader("Accept", "application/json")
                      .build()));
    } else {
      // Public API - no authentication required for basic endpoints
      builder.addInterceptor(
          chain ->
              chain.proceed(
                  chain.request().newBuilder().addHeader("Accept", "application/json").build()));
    }

    this.httpClient = builder.build();
  }

  /**
   * Uploads an image and gets recognition results. If cacheService is provided, will attempt to
   * retrieve from cache first.
   *
   * @param imageData Base64-encoded image data
   * @param format Image MIME type (e.g., "image/jpeg")
   * @return Recognition results with species predictions
   */
  public PlantRecognitionResult recognize(byte[] imageData, String format) throws IOException {
    return recognize(imageData, format, "en");
  }

  /**
   * Uploads an image and gets recognition results with language preference. If cacheService is
   * provided, will attempt to retrieve from cache first.
   */
  public PlantRecognitionResult recognize(byte[] imageData, String format, String languages)
      throws IOException {
    MediaType mediaType = MediaType.parse(format);

    RequestBody body = RequestBody.create(mediaType, imageData);

    // Build URL with query parameters directly
    String url =
        BASE_URL
            + "/images?format="
            + URLEncoder.encode(format.replace("image/", ""), StandardCharsets.UTF_8)
            + "&languages="
            + URLEncoder.encode(languages, StandardCharsets.UTF_8);

    Request request = new Request.Builder().url(url).post(body).build();

    Response response = httpClient.newCall(request).execute();

    if (!response.isSuccessful()) {
      throw new IOException(
          "Failed to recognize image: " + response.code() + " - " + response.body().string());
    }

    PlantRecognitionResult result =
        JsonUtil.fromJson(response.body().string(), PlantRecognitionResult.class);

    // Cache the results if cache service is available and response has species data
    if (cacheService != null && result.isSuccessful() && !result.hits().isEmpty()) {
      PlantHit hit = result.hit();
      if (hit != null) {
        try {
          var taxonIdObj = hit.taxonId();
          // Handle both Long and String taxon IDs from the API response
          String taxonId = taxonIdObj == null ? "null" : taxonIdObj.toString();
          // Cache the species response from the API
          cacheService.cacheSpecies(taxonId, response.body().string());
        } catch (IOException e) {
          throw new IOException("Failed to cache recognition result", e);
        }
      }
    }

    return result;
  }

  /**
   * Gets species information by taxon ID. If cacheService is provided, will attempt to retrieve
   * from cache first.
   *
   * @param taxonId Taxon identifier from PlantNet API
   * @return Species details
   */
  public PlantSpecies getSpecies(String taxonId) throws IOException {
    // Try cached result if cache service is available
    String cached = cacheService != null ? cacheService.getCachedSpecies(taxonId) : null;
    if (cached != null) {
      return JsonUtil.fromJson(cached, PlantSpecies.class);
    }

    Request request = new Request.Builder().url(BASE_URL + "/taxons/" + taxonId).get().build();

    Response response = httpClient.newCall(request).execute();

    if (!response.isSuccessful()) {
      throw new IOException("Failed to get species: " + response.code());
    }

    PlantSpecies result = JsonUtil.fromJson(response.body().string(), PlantSpecies.class);

    // Cache the results if cache service is available
    if (cacheService != null) {
      try {
        cacheService.cacheSpecies(taxonId, response.body().string());
      } catch (IOException e) {
        throw new IOException("Failed to cache species data", e);
      }
    }

    return result;
  }

  /**
   * Gets family information by ID. If cacheService is provided, will attempt to retrieve from cache
   * first.
   *
   * @param id Family ID from PlantNet API
   * @return Family details
   */
  public PlantFamily getFamily(String id) throws IOException {
    // Try cached result if cache service is available
    String cached = cacheService != null ? cacheService.getCachedFamily(id) : null;
    if (cached != null) {
      return JsonUtil.fromJson(cached, PlantFamily.class);
    }

    Request request = new Request.Builder().url(BASE_URL + "/taxons/families/" + id).get().build();

    Response response = httpClient.newCall(request).execute();

    if (!response.isSuccessful()) {
      throw new IOException("Failed to get family: " + response.code());
    }

    PlantFamily result = JsonUtil.fromJson(response.body().string(), PlantFamily.class);

    // Cache the results if cache service is available
    if (cacheService != null) {
      try {
        cacheService.cacheFamily(id, response.body().string());
      } catch (IOException e) {
        throw new IOException("Failed to cache family data", e);
      }
    }

    return result;
  }

  /**
   * Searches for taxa by name. If cacheService is provided, will attempt to retrieve from cache
   * first.
   *
   * @param query Search term (Latin or common name)
   * @return List of matching taxa with confidence scores
   */
  public TaxonSearchResults searchTaxa(String query) throws IOException {
    // Try cached result if cache service is available
    String cached = cacheService != null ? cacheService.getCachedSearch(query) : null;
    if (cached != null) {
      return JsonUtil.fromJson(cached, TaxonSearchResults.class);
    }

    // Build URL with query parameter directly
    String url = BASE_URL + "/taxons/search?q=" + URLEncoder.encode(query, StandardCharsets.UTF_8);

    Request request = new Request.Builder().url(url).get().build();

    Response response = httpClient.newCall(request).execute();

    if (!response.isSuccessful()) {
      throw new IOException("Search failed: " + response.code());
    }

    TaxonSearchResults result =
        JsonUtil.fromJson(response.body().string(), TaxonSearchResults.class);

    // Cache the results if cache service is available
    if (cacheService != null) {
      try {
        cacheService.cacheSearch(query, response.body().string());
      } catch (IOException e) {
        throw new IOException("Failed to cache search results", e);
      }
    }

    return result;
  }

  /**
   * Downloads a standard image.
   *
   * @param taxonId Taxon identifier
   * @param imageId Image ID within the taxon
   * @return Image bytes and format info
   */
  public PlantNetImage getStandardImage(String taxonId, String imageId) throws IOException {
    return downloadImage(taxonId, imageId);
  }

  /**
   * Downloads a treatment image.
   *
   * @param taxonId Taxon identifier
   * @param imageId Image ID within the taxon
   * @return Image bytes and format info
   */
  public PlantNetImage getTreatmentImage(String taxonId, String imageId) throws IOException {
    return downloadImage(taxonId, imageId);
  }

  /** Downloads any standard/treatment image. */
  private PlantNetImage downloadImage(String id1, String id2) throws IOException {
    // Build URL with query parameters directly
    String url = BASE_URL + "/standards/" + id1 + "/images/" + id2 + "?format=jpeg&height=500";

    Request request = new Request.Builder().url(url).get().build();

    Response response = httpClient.newCall(request).execute();

    if (!response.isSuccessful()) {
      throw new IOException("Failed to download image: " + response.code());
    }

    PlantNetImage image = new PlantNetImage(response.body().bytes(), "image/jpeg", 500);

    return image;
  }

  /** Gets list of images for a taxon type. */
  public TaxonImageList getImageList(String id) throws IOException {
    Request request = new Request.Builder().url(BASE_URL + "/taxons/" + id + "/images").build();

    Response response = httpClient.newCall(request).execute();

    if (!response.isSuccessful()) {
      throw new IOException("Failed to get image list: " + response.code());
    }

    TaxonImageList result = JsonUtil.fromJson(response.body().string(), TaxonImageList.class);

    return result;
  }

  /** Uploads and recognizes in one call. */
  public PlantRecognitionResult uploadAndRecognize(byte[] imageData, String format)
      throws IOException {
    MediaType mediaType = MediaType.parse(format);

    RequestBody body = RequestBody.create(mediaType, imageData);

    // Build URL with query parameter directly
    String url =
        BASE_URL
            + "/images/upload?format="
            + URLEncoder.encode(format.replace("image/", ""), StandardCharsets.UTF_8);

    Request request = new Request.Builder().url(url).post(body).build();

    Response response = httpClient.newCall(request).execute();

    if (!response.isSuccessful()) {
      throw new IOException(
          "Upload and recognize failed: " + response.code() + " - " + response.body().string());
    }

    PlantRecognitionResult result =
        JsonUtil.fromJson(response.body().string(), PlantRecognitionResult.class);

    return result;
  }

  // ==================== Record Classes ====================

  /** Image download result. */
  public record PlantNetImage(byte[] imageData, String format, int height) {
    @Override
    public String toString() {
      return "PlantNetImage{" + "format='" + format + '\'' + ", height=" + height + '}';
    }
  }

  /** Reference to a plant image. */
  public record PlantNetImageReference(String id, String url, int height, String format) {
    @Override
    public String toString() {
      return "PlantNetImageReference{"
          + "id='"
          + id
          + '\''
          + ", url='"
          + (url != null ? url : "")
          + '\''
          + ", height="
          + height
          + '}';
    }
  }

  /** Top-level recognition response from PlantNet API. */
  public record PlantRecognitionResult(
      String query, List<PlantHit> hits, long timestamp, int version) {

    /** Creates a new PlantRecognitionResult. */
    public static PlantRecognitionResult of(
        String query, List<PlantHit> hits, long timestamp, int version) {
      return new PlantRecognitionResult(query, hits, timestamp, version);
    }

    /** Gets the top prediction (first hit). */
    public PlantHit getTopPrediction() {
      return hits.isEmpty() ? null : hits.get(0);
    }

    /** Checks if recognition was successful. */
    public boolean isSuccessful() {
      return !hits.isEmpty();
    }

    /** Gets all hits (package-private accessor). */
    public List<PlantHit> hits() {
      return hits;
    }

    /** Gets the first hit (package-private accessor). */
    public PlantHit hit() {
      return hits.isEmpty() ? null : hits.get(0);
    }

    @Override
    public String toString() {
      return "PlantRecognitionResult{"
          + "query='"
          + query
          + '\''
          + ", hits="
          + hits.size()
          + ", timestamp="
          + timestamp
          + '}';
    }
  }

  /** Hit represents a species prediction with its details. */
  public record PlantHit(
      String id,
      Integer family,
      String genus,
      PlantSpecies species,
      String score,
      List<String> imagesUsed,
      Long taxonId) {

    /**
     * Creates a new PlantHit.
     *
     * @param id the hit identifier
     * @param family the family ID
     * @param genus the predicted genus name
     * @param species full species record
     * @param score confidence score as string
     * @param imagesUsed list of image IDs used for recognition
     * @param taxonId unique taxon identifier
     */
    public static PlantHit of(
        String id,
        Integer family,
        String genus,
        PlantSpecies species,
        String score,
        List<String> imagesUsed,
        Long taxonId) {
      return new PlantHit(id, family, genus, species, score, imagesUsed, taxonId);
    }

    /** Gets the species prediction (package-private accessor). */
    public PlantSpecies species() {
      return species;
    }

    /** Parses score from string to double. */
    public double getScoreDouble() {
      return Double.parseDouble(score);
    }

    /**
     * Gets family name if available. Note: PlantNet's free tier may not populate family information
     * consistently.
     */
    public String getFamilyName() {
      // Free tier API structure differs - returning null for now
      return null;
    }

    @Override
    public String toString() {
      return "PlantHit{"
          + "id='"
          + id
          + '\''
          + ", genus='"
          + genus
          + '\''
          + ", score="
          + score
          + '}';
    }
  }

  /** Species information from PlantNet database. */
  public record PlantSpecies(
      String id,
      Integer family,
      String genus,
      String speciesName,
      List<PlantStandard> standards,
      List<PlantTreatment> treatments,
      String scientificName,
      String authority,
      List<String> imagesUsed,
      Long taxonId,
      String commonNames,
      boolean isAccepted) {

    /**
     * Creates a new PlantSpecies.
     *
     * @param id the species identifier from PlantNet API
     * @param family the family ID
     * @param genus the predicted genus name
     * @param speciesName the species epithet
     * @param standards list of reference specimen data
     * @param treatments list of scientific treatment documents
     * @param scientificName full scientific name
     * @param authority taxonomic authority (e.g., "(L.) H.Karst.")
     * @param imagesUsed list of image IDs used for this record
     * @param taxonId unique taxon identifier
     * @param commonNames JSON string with language:name pairs
     * @param isAccepted whether this is an accepted name
     */
    public static PlantSpecies of(
        String id,
        Integer family,
        String genus,
        String speciesName,
        List<PlantStandard> standards,
        List<PlantTreatment> treatments,
        String scientificName,
        String authority,
        List<String> imagesUsed,
        Long taxonId,
        String commonNames,
        boolean isAccepted) {
      return new PlantSpecies(
          id,
          family,
          genus,
          speciesName,
          standards,
          treatments,
          scientificName,
          authority,
          imagesUsed,
          taxonId,
          commonNames,
          isAccepted);
    }

    /** Gets the full scientific name. */
    public String getFullScientificName() {
      if (genus != null && speciesName != null) {
        return genus + " " + speciesName;
      }
      return speciesName;
    }

    /** Parses common names from JSON string. */
    public List<CommonName> getCommonNames() {
      try {
        if (commonNames == null || commonNames.isBlank()) {
          return List.of();
        }

        String[] nameArray = commonNames.split(",");
        var result = new java.util.ArrayList<CommonName>();

        for (String s : nameArray) {
          String trimmed = s.trim();
          if (!trimmed.isEmpty()) {
            String[] parts = trimmed.split(":");
            if (parts.length >= 2) {
              CommonName cn = new CommonName(parts[0].trim(), parts[1].trim());
              result.add(cn);
            }
          }
        }
        return result;
      } catch (Exception e) {
        return List.of();
      }
    }

    @Override
    public String toString() {
      return "PlantSpecies{"
          + "id='"
          + id
          + '\''
          + ", genus='"
          + genus
          + '\''
          + ", species='"
          + speciesName
          + '\''
          + '}';
    }
  }

  /** Standard information about the reference specimen. */
  public record PlantStandard(
      Long id,
      Integer family,
      String genus,
      PlantSpecies species,
      String score,
      List<String> imagesUsed,
      Long taxonId,
      List<PlantTreatment> treatments,
      java.util.List<CommonName> commonNames,
      String imageCount) {

    /** Creates a new PlantStandard. */
    public static PlantStandard of(
        Long id,
        Integer family,
        String genus,
        PlantSpecies species,
        String score,
        List<String> imagesUsed,
        Long taxonId,
        List<PlantTreatment> treatments,
        java.util.List<CommonName> commonNames,
        String imageCount) {
      return new PlantStandard(
          id,
          family,
          genus,
          species,
          score,
          imagesUsed,
          taxonId,
          treatments,
          commonNames,
          imageCount);
    }

    /** Parses score from string to double. */
    public double getScoreDouble() {
      return Double.parseDouble(score);
    }

    @Override
    public String toString() {
      return "PlantStandard{" + "genus='" + genus + '\'' + ", score=" + score + '}';
    }
  }

  /** Treatment information from scientific literature. */
  public record PlantTreatment(
      String id,
      List<String> imagesUsed,
      Long taxonId,
      String title,
      Integer family,
      String genus,
      PlantSpecies species,
      String score,
      List<Map<String, Object>> traits) {

    /** Creates a new PlantTreatment. */
    public static PlantTreatment of(
        String id,
        List<String> imagesUsed,
        Long taxonId,
        String title,
        Integer family,
        String genus,
        PlantSpecies species,
        String score,
        List<Map<String, Object>> traits) {
      return new PlantTreatment(
          id, imagesUsed, taxonId, title, family, genus, species, score, traits);
    }

    /** Parses score from string to double. */
    public double getScoreDouble() {
      return Double.parseDouble(score);
    }

    @Override
    public String toString() {
      return "PlantTreatment{" + "title='" + title + '\'' + ", score=" + score + '}';
    }
  }

  /** Taxon search results. */
  public record TaxonSearchResults(
      String query, List<PlantHit> results, long timestamp, int version) {

    /**
     * Creates a new TaxonSearchResults.
     *
     * @param query the search term used
     * @param results list of matching taxa with predictions
     * @param timestamp Unix timestamp of the search result
     * @param version API response version
     */
    public static TaxonSearchResults of(
        String query, List<PlantHit> results, long timestamp, int version) {
      return new TaxonSearchResults(query, results, timestamp, version);
    }

    @Override
    public String toString() {
      return "TaxonSearchResults{" + "query='" + query + '\'' + ", results=" + results.size() + '}';
    }
  }

  /** Image list for a taxon. */
  public record TaxonImageList(
      String query, List<PlantNetImageReference> images, long timestamp, int version) {

    public static TaxonImageList of(
        String query, List<PlantNetImageReference> images, long timestamp, int version) {
      return new TaxonImageList(query, images, timestamp, version);
    }

    @Override
    public String toString() {
      return "TaxonImageList{"
          + "query='"
          + query
          + '\''
          + ", images="
          + (images != null ? images.size() : 0)
          + '}';
    }
  }

  /** Family information for a taxon. */
  public record PlantFamily(String id, Integer rank, String name, List<String> imagesUsed) {

    public static PlantFamily of(String id, Integer rank, String name, List<String> imagesUsed) {
      return new PlantFamily(id, rank, name, imagesUsed);
    }

    @Override
    public String toString() {
      return "PlantFamily{" + "id='" + id + '\'' + ", name='" + name + '\'' + '}';
    }
  }
}
