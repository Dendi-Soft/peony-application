package com.dendi.peony.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Utility for manipulating PostgreSQL JDBC URLs in migration tests. */
final class JdbcUrlUtil {

  /**
   * Constructs a connection URL targeting the specified database while preserving any existing
   * query parameters from the container's JDBC URL.
   *
   * @param postgres the Testcontainers PostgreSQL container
   * @param targetDatabaseName the name of the target database to connect to
   * @return a JDBC URL for connecting to {@code targetDatabaseName} with preserved query params
   */
  static String buildTargetUrl(PostgreSQLContainer postgres, String targetDatabaseName) {
    String url = postgres.getJdbcUrl();
    int queryIndex = url.indexOf('?');
    String base = queryIndex == -1 ? url : url.substring(0, queryIndex);
    String querySuffix = queryIndex == -1 ? "" : url.substring(queryIndex);
    String target =
        base.replaceFirst("/" + postgres.getDatabaseName() + "$", "/" + targetDatabaseName);
    return target + querySuffix;
  }

  /**
   * Creates a database connection targeting the specified database while preserving any existing
   * query parameters from the container's JDBC URL.
   *
   * @param postgres the Testcontainers PostgreSQL container
   * @param targetDatabaseName the name of the target database to connect to
   * @return an open {@code Connection} to the target database
   * @throws SQLException if connection fails
   */
  static Connection createConnection(PostgreSQLContainer postgres, String targetDatabaseName)
      throws SQLException {
    return DriverManager.getConnection(
        buildTargetUrl(postgres, targetDatabaseName),
        postgres.getUsername(),
        postgres.getPassword());
  }

  private JdbcUrlUtil() {}
}
