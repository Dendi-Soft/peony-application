package com.dendi.peony.service.plantrecognition;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;

/** Utility class for JSON deserialization. */
public final class JsonUtil {

  private static final ObjectMapper OBJECT_MAPPER =
      new ObjectMapper()
          .registerModule(new JavaTimeModule())
          .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

  /**
   * Deserializes JSON string to object.
   *
   * @param json JSON string
   * @param clazz Target type
   * @param <T> Target type parameter
   * @return Deserialized object
   */
  public static <T> T fromJson(String json, Class<T> clazz) {
    if (json == null || json.isBlank()) {
      return null;
    }
    try {
      return OBJECT_MAPPER.readValue(json, clazz);
    } catch (IOException e) {
      throw new RuntimeException("Failed to deserialize JSON: " + json, e);
    }
  }

  /**
   * Serializes object to JSON string.
   *
   * @param obj Object to serialize
   * @param <T> Type parameter
   * @return JSON string
   */
  public static <T> String toJson(T obj) {
    if (obj == null) {
      return null;
    }
    try {
      return OBJECT_MAPPER.writeValueAsString(obj);
    } catch (IOException e) {
      throw new RuntimeException("Failed to serialize object: " + obj, e);
    }
  }

  /**
   * URL-encodes a string for use in query parameters.
   *
   * @param value String to encode
   * @return URL-encoded string
   */
  public static String encodeUrl(String value) {
    if (value == null || value.isEmpty()) {
      return "";
    }
    try {
      return java.net.URLEncoder.encode(value, "UTF-8");
    } catch (java.io.UnsupportedEncodingException e) {
      // UTF-8 is always supported in Java
      throw new RuntimeException("Unsupported encoding", e);
    }
  }
}
