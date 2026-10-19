package com.dendi.peony.service.plantrecognition;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.Setter;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** PlantNet API client for plant species recognition with rate limiting support. */
public class PlantNetClient {

  private static final String BASE_URL = "https://my-api.plantnet.org/v2";

  // Query parameters matching the official PlantNet API request:
  // include-related-images=false&no-reject=false&nb-results=10&lang=en&detailed=false
  private static final String INCLUDE_RELATED_IMAGES = "false";
  private static final String NO_REJECT = "false";
  private static final int NB_RESULTS = 10;
  private static final String LANG = "en";
  private static final boolean DETAILED = false;

  @Setter private OkHttpClient httpClient;
  @Setter private boolean useAuthentication;
  @Setter private String apiKey;
  @Setter private PlantNetCacheService cacheService;

  public PlantNetClient() {
    this(false, null);
  }

  public PlantNetClient(boolean useAuth, String apikey) {
    this(useAuth, apikey, null);
  }

  public PlantNetClient(PlantNetCacheService cache) {
    this(false, null, cache);
  }

  public PlantNetClient(boolean useAuth, String apiKey, PlantNetCacheService cache) {
    this.useAuthentication = useAuth;
    this.apiKey = apiKey;
    this.cacheService = cache;

    OkHttpClient.Builder builder =
        new OkHttpClient.Builder()
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS);

    // PlantNet API v2 uses query parameter for API key, not Bearer token header
    if (useAuthentication && apiKey != null && !apiKey.isEmpty()) {
      builder.addInterceptor(
          chain -> {
            Request original = chain.request();
            return chain.proceed(
                original.newBuilder().addHeader("Accept", "application/json").build());
          });
    } else {
      // Without API key, just set Accept header (PlantNet may work without auth for demo)
      builder.addInterceptor(
          chain ->
              chain.proceed(
                  chain.request().newBuilder().addHeader("Accept", "application/json").build()));
    }

    this.httpClient = builder.build();
  }

  private static final Logger log = LoggerFactory.getLogger(PlantNetClient.class);

  /** Uploads an image and gets recognition results. */
  public com.dendi.peony.dto.plantrecognition.PlantRecognitionResult recognize(
      byte[] imageData, String format) throws IOException {
    return recognize(imageData, format, null, null);
  }

  /** Uploads an image and gets recognition results with language preference. */
  @SuppressWarnings("SameParameterValue")
  public com.dendi.peony.dto.plantrecognition.PlantRecognitionResult recognize(
      byte[] imageData, String format, String languages) throws IOException {
    return recognize(imageData, format, null, null, null, languages);
  }

  /** Uploads one or more images and gets recognition results. */
  @SuppressWarnings("SameParameterValue")
  public com.dendi.peony.dto.plantrecognition.PlantRecognitionResult recognize(
      byte[] imageData1,
      String format,
      byte[] imageData2, // Optional second image
      String languages)
      throws IOException {
    return recognize(imageData1, format, null, imageData2, null, languages);
  }

  /** Uploads one or more images and gets recognition results with optional third image. */
  @SuppressWarnings("SameParameterValue")
  public com.dendi.peony.dto.plantrecognition.PlantRecognitionResult recognize(
      byte[] imageData1,
      String format,
      List<String> organs, // Optional: ["auto", "leaf", "flower", ...]
      byte[] imageData2, // Optional second image
      byte[] imageData3, // Optional third image
      String languages)
      throws IOException {

    MediaType mediaType = MediaType.parse(format);

    // Create multipart request body for PlantNet API
    MultipartBody.Builder builder =
        new MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "images",
                "image1." + format.replace("image/", "").replace("/", ""),
                RequestBody.create(mediaType, imageData1));

    // Add optional second and third images
    if (imageData2 != null && imageData2.length > 0) {
      builder.addFormDataPart(
          "images",
          "image2." + format.replace("image/", "").replace("/", ""),
          RequestBody.create(mediaType, imageData2));
    }

    if (imageData3 != null && imageData3.length > 0) {
      builder.addFormDataPart(
          "images",
          "image3." + format.replace("image/", "").replace("/", ""),
          RequestBody.create(mediaType, imageData3));
    }

    // Add optional organs parameter for organ detection hinting
    if (organs != null && !organs.isEmpty()) {
      builder.addFormDataPart("organs", String.join(",", organs));
    } else {
      builder.addFormDataPart("organs", "auto"); // Default to auto-detection
    }

    if (languages != null) {
      builder.addFormDataPart("language", languages);
    }

    MultipartBody requestPayload = builder.build();

    // Build URL with query parameters matching PlantNet API specification exactly:
    // https://my-api.plantnet.org/v2/identify/all?include-related-images=false&no-reject=false&nb-results=10&lang=en&detailed=false&api-key=<KEY>
    String baseUrl = BASE_URL + "/identify/all";
    StringBuilder urlBuilder = new StringBuilder(baseUrl);

    // Add required query parameters from curl command:
    // include-related-images=false&no-reject=false&nb-results=10&lang=en&detailed=false
    urlBuilder
        .append("?include-related-images=")
        .append(INCLUDE_RELATED_IMAGES)
        .append("&no-reject=")
        .append(NO_REJECT)
        .append("&nb-results=")
        .append(NB_RESULTS)
        .append("&lang=")
        .append(LANG)
        .append("&detailed=")
        .append(DETAILED);

    // Add API key as query parameter if authentication is enabled
    if (useAuthentication && apiKey != null && !apiKey.isEmpty()) {
      urlBuilder.append("&api-key=").append(JsonUtil.encodeUrl(apiKey));
    }

    String url = urlBuilder.toString();
    Request request = new Request.Builder().url(url).post(requestPayload).build();

    // Retry logic with backoff for rate limiting issues from PlantNet API side
    int maxAttempts = 5;
    long delayMs = 1000L; // Start with 1 second between retries

    Response response = null;

    for (int attempt = 1; attempt <= maxAttempts; attempt++) {
      try {
        request = new Request.Builder().url(url).post(requestPayload).build();
        log.debug("Attempting PlantNet API call {}...", attempt);
        response = httpClient.newCall(request).execute();

      } catch (IOException e) {
        log.warn("Attempt {} failed: {}. Retrying...", attempt, e.getMessage(), e);

        if (attempt < maxAttempts && !Thread.currentThread().isInterrupted()) {
          try {
            java.util.concurrent.TimeUnit.MILLISECONDS.sleep(delayMs * attempt);
          } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new IOException("Retry interrupted", ie);
          }
        } else if (!Thread.currentThread().isInterrupted()) {
          throw new IOException("Max retries reached for " + url, e);
        }
      }
    }

    // Handle non-2xx responses with rate limiting logic
    if (!response.isSuccessful()) {
      String body = response.body().string();

      // Handle api-version confusion - this indicates rate limiting from PlantNet's side
      if (body != null && body.contains("api-version")) {
        log.info("PlantNet API returned rate limit indicator: {}", body);

        throw new IOException(
            "Rate limited by PlantNet API: Invalid api-version error. Please wait and retry later.");

      } else if (response.code() == 429) {
        // Standard rate limiting response
        log.warn("PlantNet API returned 429 Too Many Requests");

        throw new IOException(
            "Rate limited by PlantNet API: too many requests. Please wait and retry later.");

      } else if (response.code() == 503) {
        // Service unavailable
        log.warn("PlantNet API returned 503 Service Unavailable");

        throw new IOException(
            "PlantNet API temporarily unavailable: service maintenance or overload.");

      } else {
        throw new IOException("Failed to recognize image: " + response.code() + " - " + body);
      }
    }

    // Success - parse the result
    com.dendi.peony.dto.plantrecognition.PlantRecognitionResult result;

    try {
      String json = response.body().string();

      result =
          JsonUtil.fromJson(
              json, com.dendi.peony.dto.plantrecognition.PlantRecognitionResult.class);

      // Extract additional metadata from API response (apiVersion, remainingRequests)
      extractMetadataFromResponse(json, result);

    } catch (Exception e) {
      log.warn("Failed to parse JSON response: {}", e.getMessage());

      throw new IOException("Invalid API response", e);
    }

    return result;
  }

  /** Extracts additional metadata from raw PlantNet API response. */
  private void extractMetadataFromResponse(
      String jsonResponse, com.dendi.peony.dto.plantrecognition.PlantRecognitionResult result) {
    try {
      // Use Jackson to parse the top-level fields directly
      var apiResponse = JsonUtil.fromJson(jsonResponse, ApiMetadataWrapper.class);

      if (apiResponse != null) {
        // Extract API version for tracking which model was used
        if (apiResponse.getApiVersion() != null) {
          result.setApiVersion(apiResponse.getApiVersion());
        }

        // Extract remaining requests for quota monitoring
        if (apiResponse.getRemainingRequests() != null) {
          result.setRemainingRequests(apiResponse.getRemainingRequests());
        }
      }

      // Parse hits with full details from API response
      parseHitsFromApiResponse(jsonResponse, result);

    } catch (Exception e) {
      log.debug("Failed to extract metadata from response: {}", e.getMessage());
    }
  }

  /** Parses hit objects from the PlantNet API JSON response. */
  private void parseHitsFromApiResponse(
      String jsonResponse, com.dendi.peony.dto.plantrecognition.PlantRecognitionResult result) {
    try {
      // Parse full results array from API response
      var apiResponse = JsonUtil.fromJson(jsonResponse, ApiFullResponse.class);

      if (apiResponse != null && apiResponse.getResults() != null) {
        List<com.dendi.peony.dto.plantrecognition.HitResponse> hits = new ArrayList<>();

        for (var hit : apiResponse.getResults()) {
          com.dendi.peony.dto.plantrecognition.HitResponse parsedHit =
              new com.dendi.peony.dto.plantrecognition.HitResponse();

          // Populate species data directly from API response
          var speciesData = hit.getSpecies();
          if (speciesData != null) {
            parsedHit.setScore(hit.getScore());

            // Set full scientific name with authority
            parsedHit.getSpecies().setScientificName(speciesData.getScientificName());

            // Set scientific name without authority (e.g., "Caladium bicolor")
            if (speciesData.getScientificNameWithoutAuthor() != null) {
              parsedHit
                  .getSpecies()
                  .setScientificNameWithoutAuthor(speciesData.getScientificNameWithoutAuthor());
            } else {
              // Fallback: extract from full name without authority part
              String fullName = speciesData.getScientificName();
              if (fullName != null && fullName.contains(" ")) {
                int lastSpaceIndex = fullName.lastIndexOf(' ');
                parsedHit
                    .getSpecies()
                    .setScientificNameWithoutAuthor(fullName.substring(0, lastSpaceIndex));
              } else {
                parsedHit.getSpecies().setScientificNameWithoutAuthor(fullName);
              }
            }

            // Set authority part (e.g., "(Aiton) Vent.")
            if (speciesData.getScientificNameAuthorship() != null) {
              parsedHit
                  .getSpecies()
                  .setScientificNameAuthorship(speciesData.getScientificNameAuthorship());
            } else {
              // Fallback: extract authority from full name after last space
              String fullName = speciesData.getScientificName();
              if (fullName != null && fullName.contains(" ")) {
                int lastSpaceIndex = fullName.lastIndexOf(' ');
                parsedHit
                    .getSpecies()
                    .setScientificNameAuthorship(fullName.substring(lastSpaceIndex + 1));
              } else {
                parsedHit.getSpecies().setScientificNameAuthorship("");
              }
            }

            // Set genus information from nested object if available
            var genusData = speciesData.getGenus();
            if (genusData != null) {
              parsedHit.getSpecies().getGenus().setScientificName(genusData.getScientificName());
              parsedHit
                  .getSpecies()
                  .getGenus()
                  .setScientificNameWithoutAuthor(genusData.getScientificNameWithoutAuthor());
            }

            // Set family information from nested object if available
            var familyData = speciesData.getFamily();
            if (familyData != null) {
              parsedHit.getSpecies().getFamily().setScientificName(familyData.getScientificName());
              parsedHit
                  .getSpecies()
                  .getFamily()
                  .setScientificNameWithoutAuthor(familyData.getScientificNameWithoutAuthor());
            }

            // Set common names if available
            if (speciesData.getCommonNames() != null && !speciesData.getCommonNames().isEmpty()) {
              parsedHit.getSpecies().setCommonNames(new ArrayList<>(speciesData.getCommonNames()));
            }
          } else {
            // Fallback: use legacy extraction for backwards compatibility
            String fullScientificName = "";
            if (hit.getScore() != null) {
              fullScientificName = extractGenusFromScientificName(fullScientificName);
            }

            parsedHit.setScore(hit.getScore());
          }

          // Set external IDs from gbif/powo if available
          var gbifData = hit.getGbif();
          if (gbifData != null && gbifData.getId() != null) {
            parsedHit.getGbif().setId(gbifData.getId());
          }

          var powoData = hit.getPowo();
          if (powoData != null && powoData.getId() != null) {
            parsedHit.getPowo().setId(powoData.getId());
          }

          hits.add(parsedHit);
        }

        result.setHits(hits);
      }

    } catch (Exception e) {
      log.debug("Failed to parse hits from response: {}", e.getMessage());
    }
  }

  /** Extracts genus name from full scientific name. */
  private String extractGenusFromScientificName(String scientificName) {
    if (scientificName == null || scientificName.isEmpty()) {
      return "";
    }

    // Remove authority part (everything after the last space)
    int lastSpaceIndex = scientificName.lastIndexOf(' ');
    if (lastSpaceIndex > 0) {
      return scientificName.substring(0, lastSpaceIndex);
    }

    return scientificName;
  }

  /** Wrapper class for extracting top-level API response fields. */
  private static class ApiMetadataWrapper {
    @JsonProperty("api_version")
    private String apiVersion;

    @JsonProperty("remaining_identification_requests")
    private Integer remainingIdentificationRequests;

    public String getApiVersion() {
      return apiVersion;
    }

    public void setApiVersion(String apiVersion) {
      this.apiVersion = apiVersion;
    }

    public Integer getRemainingRequests() {
      return remainingIdentificationRequests != null ? remainingIdentificationRequests : 0;
    }

    public void setRemainingRequests(Integer requests) {
      this.remainingIdentificationRequests = requests;
    }
  }

  /** Wrapper class for parsing full API response with results array. */
  private static class ApiFullResponse {
    @JsonProperty("results")
    private List<ApiHit> results;

    public List<ApiHit> getResults() {
      return results;
    }

    public void setResults(List<ApiHit> results) {
      this.results = results;
    }
  }

  /** Wrapper class for individual hit in API response. */
  private static class ApiHit {
    @JsonProperty("score")
    private Double score;

    @JsonProperty("species")
    private ApiSpecies species = new ApiSpecies();

    @JsonProperty("gbif")
    private ApiGbif gbif = new ApiGbif();

    @JsonProperty("powo")
    private ApiPowo powo = new ApiPowo();

    public Double getScore() {
      return score;
    }

    public void setScore(Double score) {
      this.score = score;
    }

    public ApiSpecies getSpecies() {
      return species;
    }

    public void setSpecies(ApiSpecies species) {
      this.species = species;
    }

    public ApiGbif getGbif() {
      return gbif;
    }

    public void setGbif(ApiGbif gbif) {
      this.gbif = gbif;
    }

    public ApiPowo getPowo() {
      return powo;
    }

    public void setPowo(ApiPowo powo) {
      this.powo = powo;
    }
  }

  /** Wrapper class for GBIF data in API response. */
  private static class ApiGbif {
    @JsonProperty("id")
    private String id;

    public String getId() {
      return id;
    }

    public void setId(String id) {
      this.id = id;
    }
  }

  /** Wrapper class for POWO data in API response. */
  private static class ApiPowo {
    @JsonProperty("id")
    private String id;

    public String getId() {
      return id;
    }

    public void setId(String id) {
      this.id = id;
    }
  }

  /** Wrapper class for species data in API response. */
  private static class ApiSpecies {
    @JsonProperty("scientificName")
    private String scientificName;

    @JsonProperty("scientificNameWithoutAuthor")
    private String scientificNameWithoutAuthor;

    @JsonProperty("scientificNameAuthorship")
    private String scientificNameAuthorship;

    @JsonProperty("genus")
    private ApiGenus genus = new ApiGenus();

    @JsonProperty("family")
    private ApiFamily family = new ApiFamily();

    @JsonProperty("commonNames")
    private List<String> commonNames = new ArrayList<>();

    public String getScientificName() {
      return scientificName;
    }

    public void setScientificName(String scientificName) {
      this.scientificName = scientificName;
    }

    public String getScientificNameWithoutAuthor() {
      return scientificNameWithoutAuthor;
    }

    public void setScientificNameWithoutAuthor(String scientificNameWithoutAuthor) {
      this.scientificNameWithoutAuthor = scientificNameWithoutAuthor;
    }

    public String getScientificNameAuthorship() {
      return scientificNameAuthorship;
    }

    public void setScientificNameAuthorship(String scientificNameAuthorship) {
      this.scientificNameAuthorship = scientificNameAuthorship;
    }

    public ApiGenus getGenus() {
      return genus;
    }

    public void setGenus(ApiGenus genus) {
      this.genus = genus;
    }

    public ApiFamily getFamily() {
      return family;
    }

    public void setFamily(ApiFamily family) {
      this.family = family;
    }

    public List<String> getCommonNames() {
      return commonNames;
    }

    public void setCommonNames(List<String> commonNames) {
      this.commonNames = commonNames != null ? commonNames : new ArrayList<>();
    }
  }

  /** Wrapper class for genus data in API response. */
  private static class ApiGenus {
    @JsonProperty("scientificName")
    private String scientificName;

    @JsonProperty("scientificNameWithoutAuthor")
    private String scientificNameWithoutAuthor;

    @JsonProperty("scientificNameAuthorship")
    private String scientificNameAuthorship;

    public String getScientificName() {
      return scientificName;
    }

    public void setScientificName(String scientificName) {
      this.scientificName = scientificName;
    }

    public String getScientificNameWithoutAuthor() {
      return scientificNameWithoutAuthor;
    }

    public void setScientificNameWithoutAuthor(String scientificNameWithoutAuthor) {
      this.scientificNameWithoutAuthor = scientificNameWithoutAuthor;
    }

    public String getScientificNameAuthorship() {
      return scientificNameAuthorship;
    }

    public void setScientificNameAuthorship(String scientificNameAuthorship) {
      this.scientificNameAuthorship = scientificNameAuthorship;
    }
  }

  /** Wrapper class for family data in API response. */
  private static class ApiFamily {
    @JsonProperty("scientificName")
    private String scientificName;

    @JsonProperty("scientificNameWithoutAuthor")
    private String scientificNameWithoutAuthor;

    @JsonProperty("scientificNameAuthorship")
    private String scientificNameAuthorship;

    public String getScientificName() {
      return scientificName;
    }

    public void setScientificName(String scientificName) {
      this.scientificName = scientificName;
    }

    public String getScientificNameWithoutAuthor() {
      return scientificNameWithoutAuthor;
    }

    public void setScientificNameWithoutAuthor(String scientificNameWithoutAuthor) {
      this.scientificNameWithoutAuthor = scientificNameWithoutAuthor;
    }

    public String getScientificNameAuthorship() {
      return scientificNameAuthorship;
    }

    public void setScientificNameAuthorship(String scientificNameAuthorship) {
      this.scientificNameAuthorship = scientificNameAuthorship;
    }
  }

  /** Extract taxon ID from PlantNet API response JSON (for cache key lookup). */
  private String extractTaxonId(String jsonResponse) {
    try {
      // Use the parsed results to find the first hit's species data
      var apiResponse = JsonUtil.fromJson(jsonResponse, ApiFullResponse.class);

      if (apiResponse != null
          && apiResponse.getResults() != null
          && !apiResponse.getResults().isEmpty()) {
        // The taxonId is typically in the "query" or can be extracted from species data
        // For now, we'll use a simple pattern to find numeric IDs in the response
        Pattern pattern =
            java.util.regex.Pattern.compile("\"(?:latinName|hit_id|[0-9_]+)\":\\s*([0-9]+)");
        Matcher matcher = pattern.matcher(jsonResponse);

        if (matcher.find()) {
          return matcher.group(1);
        }
      }
    } catch (Exception e) {
      log.debug("Could not extract taxon ID from response", e);
    }

    return null;
  }
}
