package com.dendi.peony.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
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
 * Tests the user_plants schema hardening migration (issue #25) against a real PostgreSQL database:
 * the user_id index and the unique (user_id, plant_id) constraint. Each scenario runs on a
 * dedicated database inside the container so Liquibase state never leaks between tests.
 */
@Testcontainers
class UserPlantsSchemaMigrationTest {

  private static final String MASTER_CHANGELOG = "db/changelog/changelog-master.yaml";
  private static final String CHANGESET_ID = "006-user-plants-listing";
  private static final List<String> PRECEDING_CHANGELOGS =
      List.of(
          "db/changelog/changes/001-init-tables.yaml",
          "db/changelog/changes/002-complete-plants-schema.yaml",
          "db/changelog/changes/003-users-tier.yaml",
          "db/changelog/changes/004-locations-and-images.yaml",
          "db/changelog/changes/005-jwt-refresh-tokens.yaml");

  @Container
  static PostgreSQLContainer postgres =
      new PostgreSQLContainer(DockerImageName.parse("postgres:latest"));

  /**
   * Runs the full master changelog on an empty database. Precondition: fresh database. The
   * successful {@link #apply} call proves the whole changelog applies cleanly; the count pins the
   * user_plants changeset.
   */
  @Test
  void testUserPlantsMigration_AppliesCleanly_OnFreshDatabase() throws Exception {
    String database = createDatabase("my_plants_fresh");
    apply(database, MASTER_CHANGELOG);

    assertEquals(1, changesetCount(database, CHANGESET_ID));
  }

  /**
   * Simulates a deployment already at the pre-#25 schema: changesets 001-005 applied alone, then the
   * full changelog. Precondition: database at the schema preceding this ticket.
   */
  @Test
  void testUserPlantsMigration_AppliesCleanly_OnDatabaseAlreadyAtPrecedingSchema() throws Exception {
    String database = createDatabase("my_plants_upgrade");
    for (String changeLog : PRECEDING_CHANGELOGS) {
      apply(database, changeLog);
    }
    apply(database, MASTER_CHANGELOG);

    assertEquals(1, changesetCount(database, CHANGESET_ID));
  }

  /** The user_id index exists on the migrated user_plants table so per-user listing is indexed. */
  @Test
  void testUserPlantsSchema_CreatesUserIdIndex_OnMigratedSchema() throws Exception {
    String database = createDatabase("my_plants_index");
    apply(database, MASTER_CHANGELOG);

    try (Connection connection = connect(database)) {
      assertEquals("btree", indexType(connection, "user_plants", "idx_user_plants_user"));
    }
  }

  /** The (user_id, plant_id) pair is unique: linking the same plant twice is rejected. */
  @Test
  void testUserPlantsSchema_RejectsDuplicateLink_WhenUserAndPlantPairRepeated() throws Exception {
    String database = createDatabase("my_plants_duplicate");
    apply(database, MASTER_CHANGELOG);

    try (Connection connection = connect(database);
        Statement statement = connection.createStatement()) {
      statement.executeUpdate(
          "INSERT INTO users (username, email, password_hash, first_name, last_name)"
              + " VALUES ('link-owner', 'link-owner@example.com', 'hash', 'Ada', 'Lovelace')");
      statement.executeUpdate(
          "INSERT INTO plants (name, scientific_name, description, watering_frequency)"
              + " VALUES ('Monstera', 'Monstera my plants duplicate', 'Swiss cheese plant', 7)");
      statement.executeUpdate(
          "INSERT INTO user_plants (user_id, plant_id, nickname, last_watered_at,"
              + " next_water_due_at)"
              + " SELECT u.id, p.id, '', now(), now() + interval '7 days' FROM users u, plants p"
              + " WHERE u.username = 'link-owner'"
              + " AND p.scientific_name = 'Monstera my plants duplicate'");

      try {
        statement.executeUpdate(
            "INSERT INTO user_plants (user_id, plant_id, nickname, last_watered_at,"
                + " next_water_due_at)"
                + " SELECT u.id, p.id, 'second', now(), now() + interval '7 days'"
                + " FROM users u, plants p"
                + " WHERE u.username = 'link-owner'"
                + " AND p.scientific_name = 'Monstera my plants duplicate'");
        fail("expected a unique violation for the duplicate (user_id, plant_id) pair");
      } catch (SQLException expected) {
        assertEquals("23505", expected.getSQLState(), "must be a unique constraint violation");
      }
    }
  }

  /**
   * The unique constraint is per pair: two users may link the same catalog plant, which the
   * caller-scoped listing depends on.
   */
  @Test
  void testUserPlantsSchema_AllowsSamePlant_WhenLinkedByDifferentUsers() throws Exception {
    String database = createDatabase("my_plants_shared");
    apply(database, MASTER_CHANGELOG);

    try (Connection connection = connect(database);
        Statement statement = connection.createStatement()) {
      statement.executeUpdate(
          "INSERT INTO users (username, email, password_hash, first_name, last_name)"
              + " VALUES ('shared-a', 'shared-a@example.com', 'hash', 'Ada', 'Lovelace'),"
              + " ('shared-b', 'shared-b@example.com', 'hash', 'Grace', 'Hopper')");
      statement.executeUpdate(
          "INSERT INTO plants (name, scientific_name, description, watering_frequency)"
              + " VALUES ('Monstera', 'Monstera my plants shared', 'Swiss cheese plant', 7)");
      statement.executeUpdate(
          "INSERT INTO user_plants (user_id, plant_id, nickname, last_watered_at,"
              + " next_water_due_at)"
              + " SELECT u.id, p.id, '', now(), now() + interval '7 days' FROM users u, plants p"
              + " WHERE u.username IN ('shared-a', 'shared-b')"
              + " AND p.scientific_name = 'Monstera my plants shared'");

      ResultSet resultSet =
          statement.executeQuery(
              "SELECT COUNT(*) FROM user_plants up JOIN plants p ON p.id = up.plant_id"
                  + " WHERE p.scientific_name = 'Monstera my plants shared'");
      resultSet.next();
      assertEquals(2, resultSet.getInt(1), "each user may link the same catalog plant");
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
}
