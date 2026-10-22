package com.dendi.peony;

import java.util.Map;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;

/**
 * Supplies the development JWT signing secret to the test JVM.
 *
 * <p>The test suite boots the application under the default profile, so the profile files that
 * carry the development secret ({@code application-local.yaml}, {@code application-test.yaml}) are
 * not loaded. This test-scoped processor fills that gap without touching the main classpath, so
 * production startup still fails fast on a missing secret.
 *
 * <p>The source is inserted below command-line arguments and system properties, so an explicit
 * value (e.g. the blank {@code --peony.jwt.secret=} in {@link JwtSecretFailFastTest} or a {@code
 * JWT_SECRET} environment variable) always wins.
 *
 * <p>The value must match the development secret in {@code application-local.yaml} and {@code
 * application-test.yaml}.
 */
public class TestJwtSecretEnvironmentPostProcessor implements EnvironmentPostProcessor {

  /** Development secret; must match application-local.yaml and application-test.yaml. */
  static final String DEV_SECRET = "peony-dev-jwt-secret-do-not-use-in-production-0123456789";

  @Override
  public void postProcessEnvironment(
      ConfigurableEnvironment environment, SpringApplication application) {
    MutablePropertySources sources = environment.getPropertySources();
    MapPropertySource devSecret =
        new MapPropertySource("peonyTestJwtSecret", Map.of("peony.jwt.secret", DEV_SECRET));
    if (sources.contains("systemEnvironment")) {
      sources.addAfter("systemEnvironment", devSecret);
    } else {
      sources.addFirst(devSecret);
    }
  }
}
