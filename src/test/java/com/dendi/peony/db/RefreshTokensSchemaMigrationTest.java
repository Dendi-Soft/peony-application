package com.dendi.peony.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Tests the refresh_tokens schema migration (changeset 005, spec #17) against a real PostgreSQL
 * database. Each scenario runs on a dedicated database inside the container so Liquibase state
 * never leaks between tests.
 */
@Testcontainers
class RefreshTokensSchemaMigrationTest {

  private static final String MASTER_CHANGELOG = "db/changelog/changelog-master.yaml";
  private static final String CHANGESET_ID = "005-create-refresh-tokens";
  private static final List<String> PRECEDING_CHANGELOGS =
      List.of(
          "db/changelog/changes/001-init-tables.yaml",
          "db/changelog/changes/002-complete-plants-schema.yaml",
          "db/changelog/changes/003-users-tier.yaml",
          "db/changelog/changes/004-locations-and-images.yaml");

  /** Every column of the refresh_tokens table, in the shape spec #17 prescribes. */
  private static final Set<String> EXPECTED_COLUMNS =
      Set.of("id", "token_hash", "user_id", "expires_at", "created_at", "revoked_at");

  @Container
  static PostgreSQLContainer postgres =
      new PostgreSQLContainer(DockerImageName.parse("postgres:latest"));

  /**
   * Runs the full master changelog on an empty database. Precondition: fresh database. The
   * successful {@link #apply} call proves the whole changelog applies cleanly; the count pins the
   * refresh_tokens changeset.
   */
  @Test
  void testRefreshTokensMigration_AppliesCleanly_OnFreshDatabase() throws Exception {
    String database = createDatabase("jwt_fresh");
    apply(database, MASTER_CHANGELOG);

    assertEquals(1, changesetCount(database, CHANGESET_ID));
    assertTrue(tableExists(database, "refresh_tokens"));
  }

  /**
   * Simulates a deployment already at the pre-#17 schema: changesets 001-004 applied alone, then
   * the full changelog. Precondition: database at the schema preceding this ticket.
   */
  @Test
  void testRefreshTokensMigration_AppliesCleanly_OnDatabaseAlreadyAtPrecedingSchema()
      throws Exception {
    String database = createDatabase("jwt_upgrade");
    for (String changeLog : PRECEDING_CHANGELOGS) {
      apply(database, changeLog);
    }
    apply(database, MASTER_CHANGELOG);

    assertEquals(1, changesetCount(database, CHANGESET_ID));
    assertTrue(tableExists(database, "refresh_tokens"));
  }

  /** The token_hash column is unique: a second row with the same hash is rejected. */
  @Test
  void testRefreshTokensSchema_RejectsDuplicateTokenHash_WhenHashAlreadyTaken() throws Exception {
    String database = createDatabase("jwt_duplicate");
    apply(database, MASTER_CHANGELOG);

    try (Connection connection = connect(database);
        Statement statement = connection.createStatement()) {
      statement.executeUpdate(
          "INSERT INTO users (username, email, password_hash, first_name, last_name)"
              + " VALUES ('hash-owner', 'hash-owner@example.com', 'hash', 'Ada', 'Lovelace')");
      statement.executeUpdate(
          "INSERT INTO refresh_tokens (token_hash, user_id, expires_at)"
              + " SELECT 'c2FtcGxldG9rZW5oYXNo', id, now() + interval '30 days' FROM users"
              + " WHERE username = 'hash-owner'");

      try {
        statement.executeUpdate(
            "INSERT INTO refresh_tokens (token_hash, user_id, expires_at)"
                + " SELECT 'c2FtcGxldG9rZW5oYXNo'::text, id, now() + interval '30 days' FROM users"
                + " WHERE username = 'hash-owner'");
        fail("expected a unique violation for the duplicate token_hash");
      } catch (SQLException expected) {
        assertEquals("23505", expected.getSQLState(), "must be a unique constraint violation");
      }
    }
  }

  /** Deleting a user removes their refresh tokens (FK ON DELETE CASCADE). */
  @Test
  void testRefreshTokensSchema_DeletesTokens_WhenUserDeleted() throws Exception {
    String database = createDatabase("jwt_cascade");
    apply(database, MASTER_CHANGELOG);

    try (Connection connection = connect(database);
        Statement statement = connection.createStatement()) {
      statement.executeUpdate(
          "INSERT INTO users (username, email, password_hash, first_name, last_name)"
              + " VALUES ('cascade-owner', 'cascade-owner@example.com', 'hash', 'Ada', 'Lovelace')");
      statement.executeUpdate(
          "INSERT INTO refresh_tokens (token_hash, user_id, expires_at)"
              + " SELECT 'Y2FzY2FkZXRva2VuaGFzaA==', id, now() + interval '30 days' FROM users"
              + " WHERE username = 'cascade-owner'");
      statement.executeUpdate("DELETE FROM users WHERE username = 'cascade-owner'");

      ResultSet resultSet =
          statement.executeQuery("SELECT COUNT(*) FROM refresh_tokens WHERE user_id IS NOT NULL");
      resultSet.next();
      assertEquals(0, resultSet.getInt(1), "tokens must cascade with their user");
    }
  }

  /**
   * Inspects the migrated table: the spec #17 column set, the timestamptz types, the 64-char hash
   * width, and the created_at default. Precondition: full changelog applied.
   */
  @Test
  void testRefreshTokensSchema_ExposesExpectedColumns_OnMigratedSchema() throws Exception {
    String database = createDatabase("jwt_columns");
    apply(database, MASTER_CHANGELOG);

    try (Connection connection = connect(database);
        Statement statement = connection.createStatement()) {
      Map<String, RefreshTokenColumn> columns = refreshTokensColumns(connection);
      assertEquals(EXPECTED_COLUMNS, columns.keySet());

      assertEquals("bigint", columns.get("id").dataType());
      assertEquals("character varying", columns.get("token_hash").dataType());
      assertEquals(64, columns.get("token_hash").characterMaxLength());
      assertEquals("bigint", columns.get("user_id").dataType());
      assertEquals("timestamp with time zone", columns.get("expires_at").dataType());
      assertEquals("timestamp with time zone", columns.get("created_at").dataType());
      assertEquals("timestamp with time zone", columns.get("revoked_at").dataType());

      for (String column : EXPECTED_COLUMNS) {
        assertTrue(columns.get(column).nullable() || !"revoked_at".equals(column), column);
      }
      assertTrue(columns.get("revoked_at").nullable(), "revoked_at must be nullable");

      // created_at carries a now() default: inserting without it must still succeed
      statement.executeUpdate(
          "INSERT INTO users (username, email, password_hash, first_name, last_name)"
              + " VALUES ('column-owner', 'column-owner@example.com', 'hash', 'Ada', 'Lovelace')");
      statement.executeUpdate(
          "INSERT INTO refresh_tokens (token_hash, user_id, expires_at)"
              + " SELECT 'ZGVmYXVsdHRva2VuaGFzaA==', id, now() + interval '30 days' FROM users"
              + " WHERE username = 'column-owner'");
      ResultSet resultSet =
          statement.executeQuery(
              "SELECT COUNT(*) FROM refresh_tokens WHERE created_at IS NOT NULL");
      resultSet.next();
      assertEquals(1, resultSet.getInt(1), "created_at must default to now()");
    }
  }

  /** The user_id index from spec #17 exists on the migrated table. */
  @Test
  void testRefreshTokensSchema_CreatesUserIndex_OnMigratedSchema() throws Exception {
    String database = createDatabase("jwt_index");
    apply(database, MASTER_CHANGELOG);

    try (Connection connection = connect(database)) {
      assertEquals("btree", indexType(connection, "refresh_tokens", "idx_refresh_tokens_user"));
    }
  }

  private String createDatabase(String name) throws SQLException {
    String databaseName = "peony_" + name;
    try (Connection connection = connect(postgres.getDatabaseName());
        Statement statement = connection.createStatement()) {
      statement.executeUpdate("CREATE DATABASE " + databaseName);
    }
    return databaseName;
  }

  private Connection connect(String databaseName) throws SQLException {
    return JdbcUrlUtil.createConnection(postgres, databaseName);
  }

  private void apply(String databaseName, String changeLogPath) throws Exception {
    try (Connection connection = connect(databaseName);
        JdbcConnection jdbc = new JdbcConnection(connection)) {
      new Liquibase(changeLogPath, new ClassLoaderResourceAccessor(), jdbc)
          .update(new Contexts(), new LabelExpression());
    }
  }

  private int changesetCount(String databaseName, String changesetId) throws SQLException {
    try (Connection connection = connect(databaseName);
        Statement statement = connection.createStatement();
        ResultSet resultSet =
            statement.executeQuery(
                "SELECT COUNT(*) FROM databasechangelog WHERE id = '" + changesetId + "'")) {
      resultSet.next();
      return resultSet.getInt(1);
    }
  }

  private boolean tableExists(String databaseName, String table) throws SQLException {
    try (Connection connection = connect(databaseName);
        Statement statement = connection.createStatement();
        ResultSet resultSet =
            statement.executeQuery(
                "SELECT COUNT(*) FROM information_schema.tables"
                    + " WHERE table_schema = 'public' AND table_name = '"
                    + table
                    + "'")) {
      resultSet.next();
      return resultSet.getInt(1) == 1;
    }
  }

  private Map<String, RefreshTokenColumn> refreshTokensColumns(Connection connection)
      throws SQLException {
    Map<String, RefreshTokenColumn> columns = new HashMap<>();
    try (Statement statement = connection.createStatement();
        ResultSet resultSet =
            statement.executeQuery(
                "SELECT column_name, data_type, is_nullable, character_maximum_length"
                    + " FROM information_schema.columns"
                    + " WHERE table_schema = 'public' AND table_name = 'refresh_tokens'")) {
      while (resultSet.next()) {
        columns.put(
            resultSet.getString("column_name"),
            new RefreshTokenColumn(
                resultSet.getString("data_type"),
                resultSet.getString("is_nullable").equals("YES"),
                resultSet.getObject("character_maximum_length") == null
                    ? null
                    : resultSet.getInt("character_maximum_length")));
      }
    }
    return columns;
  }

  private String indexType(Connection connection, String table, String index) throws SQLException {
    try (Statement statement = connection.createStatement();
        ResultSet resultSet =
            statement.executeQuery(
                "SELECT am.amname FROM pg_index idx"
                    + " JOIN pg_class index_class ON index_class.oid = idx.indexrelid"
                    + " JOIN pg_class table_class ON table_class.oid = idx.indrelid"
                    + " JOIN pg_am am ON am.oid = index_class.relam"
                    + " WHERE table_class.relname = '"
                    + table
                    + "'"
                    + " AND index_class.relname = '"
                    + index
                    + "'")) {
      if (!resultSet.next()) {
        fail("expected index " + index + " on " + table);
      }
      return resultSet.getString("amname");
    }
  }

  private record RefreshTokenColumn(
      String dataType, boolean nullable, Integer characterMaxLength) {}
}
