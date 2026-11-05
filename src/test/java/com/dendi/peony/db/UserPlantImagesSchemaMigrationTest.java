package com.dendi.peony.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;
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
 * Tests the per-individual image migration (issue #44) against a real PostgreSQL database: the
 * columns and their types, the unique image slot per individual, the immutable image id, and the
 * cascade that removes an image when its individual is deleted. Each scenario runs on a dedicated
 * database inside the container so Liquibase state never leaks between tests.
 */
@Testcontainers
class UserPlantImagesSchemaMigrationTest {

  private static final String MASTER_CHANGELOG = "db/changelog/changelog-master.yaml";
  private static final String IMAGES_CHANGESET_ID = "009-user-plant-images";
  private static final List<String> PRECEDING_CHANGELOGS =
      List.of(
          "db/changelog/changes/001-init-tables.yaml",
          "db/changelog/changes/002-complete-plants-schema.yaml",
          "db/changelog/changes/003-users-tier.yaml",
          "db/changelog/changes/004-locations-and-images.yaml",
          "db/changelog/changes/005-jwt-refresh-tokens.yaml",
          "db/changelog/changes/006-user-plants-listing.yaml",
          "db/changelog/changes/007-user-plant-individuals.yaml",
          "db/changelog/changes/008-catalogue-source.yaml");

  @Container
  static PostgreSQLContainer postgres =
      new PostgreSQLContainer(DockerImageName.parse("postgres:latest"));

  /**
   * Precondition: a fresh database. Running the full master changelog applies the image changeset
   * exactly once.
   */
  @Test
  void testUserPlantImagesMigration_AppliesCleanly_OnFreshDatabase() throws Exception {
    String database = createDatabase("images_fresh");
    apply(database, MASTER_CHANGELOG);

    assertEquals(1, changesetCount(database, IMAGES_CHANGESET_ID));
  }

  /**
   * Precondition: a database already at the preceding schema (changesets 001-008). Applying the
   * full changelog upgrades it cleanly and records the image changeset once.
   */
  @Test
  void testUserPlantImagesMigration_AppliesCleanly_OnDatabaseAlreadyAtPrecedingSchema()
      throws Exception {
    String database = createDatabase("images_upgrade");
    applyPreceding(database);
    apply(database, MASTER_CHANGELOG);

    assertEquals(1, changesetCount(database, IMAGES_CHANGESET_ID));
  }

  /**
   * The new table carries every column the storage record needs, all required: the owning
   * individual, the immutable client-facing image id, both file names, the display dimensions and
   * size, and the creation timestamp.
   */
  @Test
  void testUserPlantImagesSchema_DefinesRequiredColumns_WhenMigrated() throws Exception {
    String database = createDatabase("images_columns");
    apply(database, MASTER_CHANGELOG);

    try (Connection connection = connect(database)) {
      assertColumn(connection, "id", "bigint", false);
      assertColumn(connection, "user_plant_id", "bigint", false);
      assertColumn(connection, "image_id", "uuid", false);
      assertColumn(connection, "filename", "text", false);
      assertColumn(connection, "thumb_filename", "text", false);
      assertColumn(connection, "width", "integer", false);
      assertColumn(connection, "height", "integer", false);
      assertColumn(connection, "byte_size", "bigint", false);
      assertColumn(connection, "created_at", "timestamp without time zone", false);
    }
  }

  /**
   * Precondition: one individual already has an image row. A second row for the same individual
   * violates the one-image-per-individual rule.
   */
  @Test
  void testUserPlantImagesSchema_RejectsSecondRow_WhenIndividualAlreadyHasImage() throws Exception {
    String database = createDatabase("images_unique_user_plant");
    apply(database, MASTER_CHANGELOG);

    try (Connection connection = connect(database);
        Statement statement = connection.createStatement()) {
      long userPlantId = seedIndividual(statement, "images-unique-user-plant");
      insertImage(statement, userPlantId, UUID.randomUUID());

      assertThrows(
          SQLException.class,
          () -> insertImage(statement, userPlantId, UUID.randomUUID()),
          "an individual may hold only one image");
    }
  }

  /**
   * Precondition: one individual's image uses an image id. The immutable id is unique across the
   * table, so another individual cannot reuse it.
   */
  @Test
  void testUserPlantImagesSchema_RejectsDuplicateImageId_WhenImageIdAlreadyUsed() throws Exception {
    String database = createDatabase("images_unique_image_id");
    apply(database, MASTER_CHANGELOG);

    try (Connection connection = connect(database);
        Statement statement = connection.createStatement()) {
      UUID imageId = UUID.randomUUID();
      long firstUserPlantId = seedIndividual(statement, "images-unique-image-first");
      long secondUserPlantId = seedIndividual(statement, "images-unique-image-second");
      insertImage(statement, firstUserPlantId, imageId);

      assertThrows(
          SQLException.class,
          () -> insertImage(statement, secondUserPlantId, imageId),
          "an image id must address exactly one stored image");
    }
  }

  /**
   * Precondition: an individual with an image row. Deleting the individual cascades to its image
   * row, so no orphaned metadata survives the plant.
   */
  @Test
  void testUserPlantImagesSchema_DeletesImageRow_WhenIndividualDeleted() throws Exception {
    String database = createDatabase("images_cascade");
    apply(database, MASTER_CHANGELOG);

    try (Connection connection = connect(database);
        Statement statement = connection.createStatement()) {
      long userPlantId = seedIndividual(statement, "images-cascade");
      insertImage(statement, userPlantId, UUID.randomUUID());

      statement.executeUpdate("DELETE FROM user_plants WHERE id = " + userPlantId);

      assertEquals(
          0,
          count(statement, "SELECT COUNT(*) FROM user_plant_images"),
          "deleting the individual must remove its image row");
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

  private long seedIndividual(Statement statement, String username) throws SQLException {
    long userId =
        insertAndReturnId(
            statement,
            "INSERT INTO users (username, email, password_hash, first_name, last_name) VALUES ('"
                + username
                + "', '"
                + username
                + "@example.com', 'hash', 'Test', 'User')");
    long plantId =
        insertAndReturnId(
            statement,
            "INSERT INTO plants (name, scientific_name, description, watering_frequency)"
                + " VALUES ('Monstera', 'Monstera "
                + username
                + "', 'seeded for migration tests', 7)");
    return insertAndReturnId(
        statement,
        "INSERT INTO user_plants (user_id, plant_id, nickname, last_watered_at, next_water_due_at)"
            + " VALUES ("
            + userId
            + ", "
            + plantId
            + ", '', NULL, NULL)");
  }

  private void insertImage(Statement statement, long userPlantId, UUID imageId)
      throws SQLException {
    statement.executeUpdate(
        "INSERT INTO user_plant_images"
            + " (user_plant_id, image_id, filename, thumb_filename, width, height, byte_size,"
            + " created_at)"
            + " VALUES ("
            + userPlantId
            + ", '"
            + imageId
            + "', '"
            + imageId
            + ".jpg', '"
            + imageId
            + "-thumb.jpg', 1280, 960, 123456, CURRENT_TIMESTAMP)");
  }

  private long insertAndReturnId(Statement statement, String insertSql) throws SQLException {
    try (ResultSet resultSet = statement.executeQuery(insertSql + " RETURNING id")) {
      resultSet.next();
      return resultSet.getLong(1);
    }
  }

  private int count(Statement statement, String sql) throws SQLException {
    try (ResultSet resultSet = statement.executeQuery(sql)) {
      resultSet.next();
      return resultSet.getInt(1);
    }
  }

  private void assertColumn(
      Connection connection, String column, String expectedType, boolean nullable)
      throws SQLException {
    try (Statement statement = connection.createStatement();
        ResultSet resultSet =
            statement.executeQuery(
                "SELECT data_type, is_nullable FROM information_schema.columns"
                    + " WHERE table_schema = 'public' AND table_name = 'user_plant_images'"
                    + " AND column_name = '"
                    + column
                    + "'")) {
      assertTrue(resultSet.next(), "expected column " + column + " on user_plant_images");
      assertEquals(expectedType, resultSet.getString("data_type"));
      assertEquals(
          nullable ? "YES" : "NO",
          resultSet.getString("is_nullable"),
          "unexpected nullability for " + column);
    }
  }
}
