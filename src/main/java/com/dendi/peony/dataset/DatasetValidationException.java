package com.dendi.peony.dataset;

import java.util.List;

/**
 * Raised when the curated plant dataset cannot be loaded or fails validation.
 *
 * <p>The message lists every problem detected in one pass so a malformed dataset can be corrected
 * without re-running the seeder for each error. The exception is unchecked because the seed runner
 * must fail the process rather than degrade silently.
 */
public class DatasetValidationException extends IllegalArgumentException {

  private final List<String> problems;

  /**
   * Creates an exception carrying every validation problem.
   *
   * @param problems human-readable descriptions of each rule violation
   */
  public DatasetValidationException(List<String> problems) {
    super(buildMessage(problems));
    this.problems = List.copyOf(problems);
  }

  /**
   * Creates an exception for a seed file that could not be read at all.
   *
   * @param message description of the load failure
   * @param cause the underlying I/O or parsing failure
   */
  public DatasetValidationException(String message, Throwable cause) {
    super(message, cause);
    this.problems = List.of(message);
  }

  /**
   * Returns every validation problem, in the order it was detected.
   *
   * @return immutable list of problem descriptions
   */
  public List<String> problems() {
    return problems;
  }

  private static String buildMessage(List<String> problems) {
    return "The curated dataset is invalid:"
        + System.lineSeparator()
        + " - "
        + String.join(System.lineSeparator() + " - ", problems);
  }
}
