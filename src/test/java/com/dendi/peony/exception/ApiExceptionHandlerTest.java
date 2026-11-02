package com.dendi.peony.exception;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Tests the API error envelope at the handler seam, without a database: persistence conflicts from
 * a concurrent write must answer 409 rather than escape as a 500.
 */
class ApiExceptionHandlerTest {

  private final MockMvc mockMvc =
      MockMvcBuilders.standaloneSetup(new ConflictController())
          .setControllerAdvice(new ApiExceptionHandler())
          .build();

  /**
   * A duplicate scientific name that slips past the find-then-insert check (two concurrent manual
   * registrations) surfaces as a data-integrity violation and maps to the contract's 409 response.
   */
  @Test
  void testDataIntegrityViolation_Returns409_WhenConcurrentInsertDuplicatesScientificName()
      throws Exception {
    mockMvc
        .perform(post("/test/plant-conflict").contentType(MediaType.APPLICATION_JSON_VALUE))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error").value("Plant already exists"));
  }

  /** Stands in for the plant service at the point where the unique constraint fires. */
  @RestController
  static class ConflictController {

    @PostMapping("/test/plant-conflict")
    void create() {
      throw new DataIntegrityViolationException("duplicate key value violates unique constraint");
    }
  }
}
