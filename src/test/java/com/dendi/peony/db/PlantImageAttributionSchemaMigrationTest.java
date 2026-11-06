package com.dendi.peony.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
 * Tests the species-image migrations (issue #45) against a real PostgreSQL database: the
 * attribution columns on {@code plant_images}, the source check constraint, and the PlantNet
 * staging table with its unique species key and TTL index. Each scenario runs on a dedicated
 * database so Liquibase state never leaks between tests.
 */
@Testcontainers
class PlantImageAttributionSchemaMigrationTest {

  private static final String MASTER_CHANGELOG = "db/changelog/changelog-master.yaml";
  private static final String ATTRIBUTION_CHANGESET_ID = "010-plant-image-attribution";
  private static final String STAGING_CHANGESET_ID = "011-plantnet-species-images";

  private static final List<String> PRECEDING_CHANGELOGS =
      List.of(
          "db/changelog/changes/001-init-tables.yaml",
          "db/changelog/changes/002-complete-plants-schema.yaml",
          "db/changelog/changes/003-users-tier.yaml",
          "db/changelog/changes/004-locations-and-images.yaml",
          "db/changelog/changes/005-jwt-refresh-tokens.yaml",
          "db/changelog/changes/006-user-plants-listing.yaml",
          "db/changelog/changes/007-user-plant-individuals.yaml",
          "db/changelog/changes/008-catalogue-source.yaml",
          "db/changelog/changes/009-user-plant-images.yaml");

  @Container
  static PostgreSQLContainer postgres =
      new PostgreSQLContainer(DockerImageName.parse("postgres:latest"));

  /** Precondition: a fresh database. The full changelog applies both new changesets once. */
  @Test
  void testSpeciesImageMigrations_ApplyCleanly_OnFreshDatabase() throws Exception {
    String database = createDatabase("attribution_fresh");
    apply(database, MASTER_CHANGELOG);

    assertEquals(1, changesetCount(database, ATTRIBUTION_CHANGESET_ID));
    assertEquals(1, changesetCount(database, STAGING_CHANGESET_ID));
  }

  /**
   * Precondition: a database at the preceding schema (001-009). The full changelog upgrades it
   * cleanly and records each new changeset once.
   */
  @Test
  void testSpeciesImageMigrations_ApplyCleanly_OnPrecedingSchema() throws Exception {
    String database = createDatabase("attribution_upgrade");
    applyPreceding(database);
    apply(database, MASTER_CHANGELOG);

    assertEquals(1, changesetCount(database, ATTRIBUTION_CHANGESET_ID));
    assertEquals(1, changesetCount(database, STAGING_CHANGESET_ID));
  }

  /** The attribution columns are nullable so pre-existing catalogue rows stay unknown. */
  @Test
  void testPlantImageAttribution_AddsNullableColumns_WhenMigrated() throws Exception {
    String database = createDatabase("attribution_columns");
    apply(database, MASTER_CHANGELOG);

    try (Connection connection = connect(database)) {
      assertColumn(connection, "plant_images", "source", "text", true);
      assertColumn(connection, "plant_images", "author", "text", true);
      assertColumn(connection, "plant_images", "license", "text", true);
      assertColumn(connection, "plant_images", "citation", "text", true);
    }
  }

  /** The source column accepts only the two known origins, or null for legacy rows. */
  @Test
  void testPlantImageAttribution_ConstrainsSource_WhenAttributionPresent() throws Exception {
    String database = createDatabase("attribution_source_check");
    apply(database, MASTER_CHANGELOG);

    try (Connection connection = connect(database);
        Statement statement = connection.createStatement()) {
      long plantId = seedPlant(statement, "attribution-source");

      insertImage(statement, plantId, 0, null);
      insertImage(statement, plantId, 1, "SEED");
      insertImage(statement, plantId, 2, "PLANTNET");

      assertThrows(
          SQLException.class,
          () -> insertImage(statement, plantId, 3, "OTHER"),
          "an unknown image source must be rejected");
    }
  }

  /** The staging table carries every field needed to stage a credited candidate. */
  @Test
  void testPlantNetStagingSchema_DefinesRequiredColumns_WhenMigrated() throws Exception {
    String database = createDatabase("staging_columns");
    apply(database, MASTER_CHANGELOG);

    try (Connection connection = connect(database)) {
      assertColumn(connection, "plantnet_species_images", "id", "bigint", false);
      assertColumn(connection, "plantnet_species_images", "scientific_name", "text", false);
      assertColumn(connection, "plantnet_species_images", "image_url", "text", false);
      assertColumn(connection, "plantnet_species_images", "organ", "text", true);
      assertColumn(connection, "plantnet_species_images", "author", "text", true);
      assertColumn(connection, "plantnet_species_images", "license", "text", false);
      assertColumn(connection, "plantnet_species_images", "citation", "text", true);
      assertColumn(
          connection, "plantnet_species_images", "staged_at", "timestamp without time zone", false);
    }
  }

  /** A species holds at most one staged candidate, so a duplicate name is rejected. */
  @Test
  void testPlantNetStagingSchema_RejectsDuplicateSpecies_WhenAlreadyStaged() throws Exception {
    String database = createDatabase("staging_unique");
    apply(database, MASTER_CHANGELOG);

    try (Connection connection = connect(database);
        Statement statement = connection.createStatement()) {
      insertStaged(statement, "ajuga reptans", "https://bs.plantnet.org/image/m/one");

      assertThrows(
          SQLException.class,
          () -> insertStaged(statement, "ajuga reptans", "https://bs.plantnet.org/image/m/two"),
          "a species may hold only one staged candidate");
    }
  }

  /** The staging timestamp is indexed so TTL cleanup stays cheap. */
  @Test
  void testPlantNetStagingSchema_IndexesStagedAt_WhenMigrated() throws Exception {
    String database = createDatabase("staging_index");
    apply(database, MASTER_CHANGELOG);

    try (Connection connection = connect(database);
        Statement statement = connection.createStatement();
        ResultSet resultSet =
            statement.executeQuery(
                "SELECT indexname, indexdef FROM pg_indexes"
                    + " WHERE schemaname = 'public'"
                    + " AND tablename = 'plantnet_species_images'")) {
      boolean indexed = false;
      while (resultSet.next()) {
        if (resultSet.getString("indexdef").contains("(staged_at)")) {
          indexed = true;
        }
      }
      assertTrue(indexed, "expected an index on staged_at");
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

  private void applyPreceding(String databaseName) throws Exception {
    for (String changeLog : PRECEDING_CHANGELOGS) {
      apply(databaseName, changeLog);
    }
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

  private long seedPlant(Statement statement, String scientificName) throws SQLException {
    try (ResultSet resultSet =
        statement.executeQuery(
            "INSERT INTO plants (name, scientific_name, description, watering_frequency)"
                + " VALUES ('Seeded', '"
                + scientificName
                + "', 'seeded for migration tests', 7) RETURNING id")) {
      resultSet.next();
      return resultSet.getLong(1);
    }
  }

  private void insertImage(Statement statement, long plantId, int position, String source)
      throws SQLException {
    statement.executeUpdate(
        "INSERT INTO plant_images (plant_id, position, filename, source)"
            + " VALUES ("
            + plantId
            + ", "
            + position
            + ", 'image-"
            + position
            + ".jpg', "
            + (source == null ? "NULL" : "'" + source + "'")
            + ")");
  }

  private void insertStaged(Statement statement, String scientificName, String imageUrl)
      throws SQLException {
    statement.executeUpdate(
        "INSERT INTO plantnet_species_images"
            + " (scientific_name, image_url, license, staged_at)"
            + " VALUES ('"
            + scientificName
            + "', '"
            + imageUrl
            + "', 'cc-by-sa', CURRENT_TIMESTAMP)");
  }

  private void assertColumn(
      Connection connection, String table, String column, String expectedType, boolean nullable)
      throws SQLException {
    try (Statement statement = connection.createStatement();
        ResultSet resultSet =
            statement.executeQuery(
                "SELECT data_type, is_nullable FROM information_schema.columns"
                    + " WHERE table_schema = 'public' AND table_name = '"
                    + table
                    + "' AND column_name = '"
                    + column
                    + "'")) {
      assertTrue(resultSet.next(), "expected column " + column + " on " + table);
      assertEquals(expectedType, resultSet.getString("data_type"));
      assertEquals(
          nullable ? "YES" : "NO",
          resultSet.getString("is_nullable"),
          "unexpected nullability for " + table + "." + column);
    }
  }
}
