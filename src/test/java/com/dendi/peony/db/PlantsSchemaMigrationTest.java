package com.dendi.peony.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
 * Tests the plants schema migrations (001 init, 002 completion) against a real PostgreSQL database.
 * Each scenario runs on a dedicated database inside the container so Liquibase state never leaks
 * between tests.
 */
@Testcontainers
class PlantsSchemaMigrationTest {

  private static final String MASTER_CHANGELOG = "db/changelog/changelog-master.yaml";
  private static final String INIT_CHANGELOG = "db/changelog/changes/001-init-tables.yaml";
  private static final String COMPLETE_CHANGESET_ID = "002-complete-plants-schema";

  /** The changelogs that precede the catalogue-source changeset (008). */
  private static final List<String> PRECEDING_CHANGELOGS =
      List.of(
          "db/changelog/changes/001-init-tables.yaml",
          "db/changelog/changes/002-complete-plants-schema.yaml",
          "db/changelog/changes/003-users-tier.yaml",
          "db/changelog/changes/004-locations-and-images.yaml",
          "db/changelog/changes/005-jwt-refresh-tokens.yaml",
          "db/changelog/changes/006-user-plants-listing.yaml",
          "db/changelog/changes/007-user-plant-individuals.yaml");

  /** Every plant column after the catalogue-source changeset (008). */
  private static final Set<String> MVP_COLUMNS =
      Set.of(
          "id",
          "name",
          "scientific_name",
          "description",
          "watering_frequency",
          "created_at",
          "family",
          "tags",
          "light_needs",
          "humidity",
          "temp_range",
          "toxic_to_pets",
          "toxic_to_humans",
          "difficulty",
          "native_region",
          "mature_size",
          "source");

  /** The columns added by the completion changeset; the dataset may have gaps, so all nullable. */
  private static final Set<String> COMPLETED_COLUMNS =
      Set.of(
          "family",
          "tags",
          "light_needs",
          "humidity",
          "temp_range",
          "toxic_to_pets",
          "toxic_to_humans",
          "difficulty",
          "native_region",
          "mature_size");

  @Container
  static PostgreSQLContainer postgres =
      new PostgreSQLContainer(DockerImageName.parse("postgres:latest"));

  /**
   * Runs the full master changelog on an empty database. Precondition: fresh database. The
   * successful {@link #apply} call proves the whole changelog (including non-plants changesets)
   * applies cleanly; the count only pins the plants changesets so later changesets do not break
   * this test.
   */
  @Test
  void testCompletePlantsSchema_AppliesCleanly_OnFreshDatabase() throws Exception {
    String database = createDatabase("fresh");
    apply(database, MASTER_CHANGELOG);

    try (Connection connection = connect(database);
        Statement statement = connection.createStatement();
        ResultSet resultSet =
            statement.executeQuery(
                "SELECT COUNT(*) FROM databasechangelog"
                    + " WHERE id IN ('001-create-users-table', '"
                    + COMPLETE_CHANGESET_ID
                    + "')")) {
      resultSet.next();
      assertEquals(2, resultSet.getInt(1));
    }
  }

  /**
   * Simulates a deployment already at the initial schema: 001 applied alone, then the full
   * changelog. Precondition: database with only the initial changeset applied.
   */
  @Test
  void testCompletePlantsSchema_AppliesCleanly_OnDatabaseAlreadyAtInitialSchema() throws Exception {
    String database = createDatabase("existing");
    apply(database, INIT_CHANGELOG);
    apply(database, MASTER_CHANGELOG);

    try (Connection connection = connect(database);
        Statement statement = connection.createStatement();
        ResultSet resultSet =
            statement.executeQuery(
                "SELECT COUNT(*) FROM databasechangelog WHERE id = '"
                    + COMPLETE_CHANGESET_ID
                    + "'")) {
      resultSet.next();
      assertEquals(1, resultSet.getInt(1));
    }
  }

  /** Inserts two plants sharing one watering interval after the migration. */
  @Test
  void testCompletePlantsSchema_AllowsDuplicateWateringFrequency_AfterMigration() throws Exception {
    String database = createDatabase("duplicate");
    apply(database, MASTER_CHANGELOG);

    try (Connection connection = connect(database);
        Statement statement = connection.createStatement()) {
      statement.executeUpdate(
          "INSERT INTO plants (name, scientific_name, description, watering_frequency)"
              + " VALUES ('Monstera', 'Monstera deliciosa', 'Swiss cheese plant', 7)");
      statement.executeUpdate(
          "INSERT INTO plants (name, scientific_name, description, watering_frequency)"
              + " VALUES ('Fiddle-leaf fig', 'Ficus lyrata', 'Fiddle-leaf fig', 7)");

      ResultSet resultSet =
          statement.executeQuery("SELECT COUNT(*) FROM plants WHERE watering_frequency = 7");
      resultSet.next();
      assertEquals(2, resultSet.getInt(1));
    }
  }

  /** Inspects the completed plants table. Precondition: full changelog applied. */
  @Test
  void testCompletePlantsSchema_ExposesAllMvpFields_OnCompletedSchema() throws Exception {
    String database = createDatabase("fields");
    apply(database, MASTER_CHANGELOG);

    try (Connection connection = connect(database)) {
      Map<String, PlantColumn> columns = plantsColumns(connection);
      assertEquals(MVP_COLUMNS, columns.keySet());

      assertEquals("text", columns.get("family").dataType());
      assertEquals("ARRAY", columns.get("tags").dataType());
      assertEquals("text", columns.get("light_needs").dataType());
      assertEquals("text", columns.get("humidity").dataType());
      assertEquals("text", columns.get("temp_range").dataType());
      assertEquals("boolean", columns.get("toxic_to_pets").dataType());
      assertEquals("boolean", columns.get("toxic_to_humans").dataType());
      assertEquals("text", columns.get("difficulty").dataType());
      assertEquals("text", columns.get("native_region").dataType());
      assertEquals("text", columns.get("mature_size").dataType());
      assertEquals("text", columns.get("source").dataType());

      for (String column : COMPLETED_COLUMNS) {
        assertTrue(columns.get(column).nullable(), column + " must be nullable");
      }
      assertTrue(columns.get("source").nullable(), "source must be nullable");
      assertTrue(columns.get("name").nullable(), "name must be nullable for scan-created rows");
      assertTrue(
          columns.get("watering_frequency").nullable(),
          "watering_frequency must be nullable for scan-created rows");
      assertEquals("gin", tagsIndexType(connection));
    }
  }

  /**
   * The PlantNet response ledger carries the documented aggregate columns, keyed by the normalized
   * binomial. Precondition: full changelog applied.
   */
  @Test
  void testPlantNetResponsesSchema_DefinesLedgerColumns_WhenMigrated() throws Exception {
    String database = createDatabase("plantnet_responses");
    apply(database, MASTER_CHANGELOG);

    try (Connection connection = connect(database)) {
      Map<String, PlantColumn> columns = tableColumns(connection, "plantnet_responses");
      assertEquals(
          Set.of(
              "scientific_name",
              "common_name",
              "response_count",
              "avg_confidence",
              "first_seen",
              "last_seen"),
          columns.keySet());

      assertEquals("text", columns.get("scientific_name").dataType());
      assertFalse(columns.get("scientific_name").nullable());
      assertEquals("text", columns.get("common_name").dataType());
      assertTrue(columns.get("common_name").nullable());
      assertEquals("bigint", columns.get("response_count").dataType());
      assertFalse(columns.get("response_count").nullable());
      assertEquals("numeric", columns.get("avg_confidence").dataType());
      assertFalse(columns.get("avg_confidence").nullable());
      assertEquals("timestamp without time zone", columns.get("first_seen").dataType());
      assertFalse(columns.get("first_seen").nullable());
      assertEquals("timestamp without time zone", columns.get("last_seen").dataType());
      assertFalse(columns.get("last_seen").nullable());

      assertEquals("scientific_name", primaryKeyColumn(connection, "plantnet_responses"));
      assertEquals(
          6, numericMetadata(connection, "plantnet_responses", "avg_confidence", "precision"));
      assertEquals(5, numericMetadata(connection, "plantnet_responses", "avg_confidence", "scale"));
    }
  }

  /**
   * A scan-created catalogue row carries only identity and taxonomy: the common name and watering
   * frequency, which PlantNet cannot supply, must be insertable as null after the migration.
   */
  @Test
  void testCatalogueSourceSchema_AllowsUnknownIdentityAndCare_WhenRowCreatedFromScan()
      throws Exception {
    String database = createDatabase("scan_row");
    apply(database, MASTER_CHANGELOG);

    try (Connection connection = connect(database);
        Statement statement = connection.createStatement()) {
      statement.executeUpdate(
          "INSERT INTO plants (name, scientific_name, description, watering_frequency, source)"
              + " VALUES (NULL, 'Caladium bicolor', '', NULL, 'PLANTNET_SCAN')");

      ResultSet resultSet =
          statement.executeQuery(
              "SELECT name, watering_frequency, source FROM plants"
                  + " WHERE scientific_name = 'Caladium bicolor'");
      resultSet.next();
      assertEquals(null, resultSet.getString("name"));
      assertEquals(null, resultSet.getObject("watering_frequency"));
      assertEquals("PLANTNET_SCAN", resultSet.getString("source"));
    }
  }

  /**
   * Rows that existed before the catalogue-source migration have no known provenance; the changeset
   * backfills them as {@code MANUAL} without touching their other fields.
   */
  @Test
  void testCatalogueSourceSchema_BackfillsExistingRowsAsManual_WhenMigrationApplied()
      throws Exception {
    String database = createDatabase("source_backfill");
    for (String changelog : PRECEDING_CHANGELOGS) {
      apply(database, changelog);
    }
    try (Connection connection = connect(database);
        Statement statement = connection.createStatement()) {
      statement.executeUpdate(
          "INSERT INTO plants (name, scientific_name, description, watering_frequency)"
              + " VALUES ('Monstera', 'Monstera deliciosa', 'Swiss cheese plant', 7)");
      statement.executeUpdate(
          "INSERT INTO plants (name, scientific_name, description, watering_frequency)"
              + " VALUES ('Fiddle-leaf fig', 'Ficus lyrata', 'Fiddle-leaf fig', 10)");
    }

    apply(database, MASTER_CHANGELOG);

    try (Connection connection = connect(database);
        Statement statement = connection.createStatement();
        ResultSet resultSet =
            statement.executeQuery(
                "SELECT scientific_name, name, watering_frequency, source FROM plants"
                    + " ORDER BY scientific_name")) {
      resultSet.next();
      assertEquals("Ficus lyrata", resultSet.getString("scientific_name"));
      assertEquals("Fiddle-leaf fig", resultSet.getString("name"));
      assertEquals(10, resultSet.getInt("watering_frequency"));
      assertEquals("MANUAL", resultSet.getString("source"));

      resultSet.next();
      assertEquals("Monstera deliciosa", resultSet.getString("scientific_name"));
      assertEquals("Monstera", resultSet.getString("name"));
      assertEquals(7, resultSet.getInt("watering_frequency"));
      assertEquals("MANUAL", resultSet.getString("source"));
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

  private Map<String, PlantColumn> plantsColumns(Connection connection) throws SQLException {
    Map<String, PlantColumn> columns = new HashMap<>();
    try (Statement statement = connection.createStatement();
        ResultSet resultSet =
            statement.executeQuery(
                "SELECT column_name, data_type, is_nullable FROM information_schema.columns"
                    + " WHERE table_schema = 'public' AND table_name = 'plants'")) {
      while (resultSet.next()) {
        columns.put(
            resultSet.getString("column_name"),
            new PlantColumn(
                resultSet.getString("data_type"),
                resultSet.getString("is_nullable").equals("YES")));
      }
    }
    return columns;
  }

  private Map<String, PlantColumn> tableColumns(Connection connection, String table)
      throws SQLException {
    Map<String, PlantColumn> columns = new HashMap<>();
    try (Statement statement = connection.createStatement();
        ResultSet resultSet =
            statement.executeQuery(
                "SELECT column_name, data_type, is_nullable FROM information_schema.columns"
                    + " WHERE table_schema = 'public' AND table_name = '"
                    + table
                    + "'")) {
      while (resultSet.next()) {
        columns.put(
            resultSet.getString("column_name"),
            new PlantColumn(
                resultSet.getString("data_type"),
                resultSet.getString("is_nullable").equals("YES")));
      }
    }
    return columns;
  }

  private String primaryKeyColumn(Connection connection, String table) throws SQLException {
    try (Statement statement = connection.createStatement();
        ResultSet resultSet =
            statement.executeQuery(
                "SELECT kcu.column_name FROM information_schema.table_constraints tc"
                    + " JOIN information_schema.key_column_usage kcu"
                    + " ON tc.constraint_name = kcu.constraint_name"
                    + " AND tc.table_schema = kcu.table_schema"
                    + " WHERE tc.table_schema = 'public' AND tc.table_name = '"
                    + table
                    + "' AND tc.constraint_type = 'PRIMARY KEY'")) {
      if (!resultSet.next()) {
        fail("expected a primary key on " + table);
      }
      return resultSet.getString("column_name");
    }
  }

  private int numericMetadata(Connection connection, String table, String column, String field)
      throws SQLException {
    try (Statement statement = connection.createStatement();
        ResultSet resultSet =
            statement.executeQuery(
                "SELECT numeric_"
                    + field
                    + " FROM information_schema.columns"
                    + " WHERE table_schema = 'public' AND table_name = '"
                    + table
                    + "' AND column_name = '"
                    + column
                    + "'")) {
      if (!resultSet.next()) {
        fail("expected column " + table + "." + column);
      }
      return resultSet.getInt(1);
    }
  }

  private String tagsIndexType(Connection connection) throws SQLException {
    try (Statement statement = connection.createStatement();
        ResultSet resultSet =
            statement.executeQuery(
                "SELECT am.amname FROM pg_index idx"
                    + " JOIN pg_class index_class ON index_class.oid = idx.indexrelid"
                    + " JOIN pg_class table_class ON table_class.oid = idx.indrelid"
                    + " JOIN pg_am am ON am.oid = index_class.relam"
                    + " WHERE table_class.relname = 'plants'"
                    + " AND index_class.relname = 'idx_plants_tags'")) {
      if (!resultSet.next()) {
        fail("expected index idx_plants_tags on plants");
      }
      return resultSet.getString("amname");
    }
  }

  private record PlantColumn(String dataType, boolean nullable) {}
}
