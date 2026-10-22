package com.dendi.peony;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Pins the fail-fast startup contract for the JWT signing secret (spec #17): with no local/test
 * profile active and no JWT_SECRET in the environment, the application must refuse to start instead
 * of signing tokens with a missing key.
 *
 * <p>The test JVM normally gets a development secret from a test-scoped environment post processor;
 * the command-line argument below overrides it with a blank value to simulate the unset secret.
 */
@Testcontainers
class JwtSecretFailFastTest {

  @Container
  static PostgreSQLContainer postgres =
      new PostgreSQLContainer(DockerImageName.parse("postgres:latest"));

  /**
   * Booting with a blank peony.jwt.secret fails the context refresh and names the offending
   * property. Precondition: default profile (no local/test dev secret from the profile files). The
   * settings are passed as command-line arguments ({@code --key=value} form, the only form Spring
   * Framework 7 parses as option properties), which outrank the test-scoped dev secret and the main
   * configuration.
   */
  @Test
  void testStartup_FailsFast_WhenJwtSecretUnset() {
    Throwable failure =
        assertThrows(
            BeanCreationException.class,
            () -> {
              ConfigurableApplicationContext context =
                  new SpringApplicationBuilder(PeonyApplication.class)
                      .web(WebApplicationType.SERVLET)
                      .build()
                      .run(
                          "--spring.datasource.url=" + postgres.getJdbcUrl(),
                          "--spring.datasource.username=" + postgres.getUsername(),
                          "--spring.datasource.password=" + postgres.getPassword(),
                          "--peony.jwt.secret=");
              context.close();
            });

    assertTrue(
        messageChain(failure).contains("peony.jwt.secret"),
        "the startup failure must name the missing property, but was: " + messageChain(failure));
  }

  /** Joins the exception and its cause chain into one searchable string. */
  private static String messageChain(Throwable throwable) {
    StringBuilder message = new StringBuilder();
    for (Throwable current = throwable; current != null; current = current.getCause()) {
      message.append(current).append('\n');
      if (current.getCause() == current) {
        break;
      }
    }
    return message.toString();
  }
}
