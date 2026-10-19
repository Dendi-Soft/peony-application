package com.dendi.peony.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
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

  /** Every MVP plant field from issue #9's Phase 2 list, mapped to the plants table. */
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
          "mature_size");

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

  /** Runs the full master changelog on an empty database. Precondition: fresh database. */
  @Test
  void testCompletePlantsSchema_AppliesCleanly_OnFreshDatabase() throws Exception {
    String database = createDatabase("fresh");
    apply(database, MASTER_CHANGELOG);

    try (Connection connection = connect(database);
        Statement statement = connection.createStatement();
        ResultSet resultSet = statement.executeQuery("SELECT COUNT(*) FROM databasechangelog")) {
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

      for (String column : COMPLETED_COLUMNS) {
        assertTrue(columns.get(column).nullable(), column + " must be nullable");
      }
      assertEquals("gin", tagsIndexType(connection));
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
    return DriverManager.getConnection(
        postgres
            .getJdbcUrl()
            .replaceFirst("/" + postgres.getDatabaseName() + "$", "/" + databaseName),
        postgres.getUsername(),
        postgres.getPassword());
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
