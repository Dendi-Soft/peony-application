package com.dendi.peony.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
 * Tests the user-plants individual-specimen migration (issue #37) against a real PostgreSQL
 * database: the composite non-unique index, the nullable watering dates, the room link, the
 * plant_locations data migration, and the table's removal. Each scenario runs on a dedicated
 * database inside the container so Liquibase state never leaks between tests.
 */
@Testcontainers
class UserPlantsSchemaMigrationTest {

  private static final String MASTER_CHANGELOG = "db/changelog/changelog-master.yaml";
  private static final String INDIVIDUALS_CHANGESET_ID = "007-user-plant-individuals";
  private static final List<String> PRECEDING_CHANGELOGS =
      List.of(
          "db/changelog/changes/001-init-tables.yaml",
          "db/changelog/changes/002-complete-plants-schema.yaml",
          "db/changelog/changes/003-users-tier.yaml",
          "db/changelog/changes/004-locations-and-images.yaml",
          "db/changelog/changes/005-jwt-refresh-tokens.yaml",
          "db/changelog/changes/006-user-plants-listing.yaml");

  @Container
  static PostgreSQLContainer postgres =
      new PostgreSQLContainer(DockerImageName.parse("postgres:latest"));

  /**
   * Runs the full master changelog on an empty database. Precondition: fresh database. The
   * successful {@link #apply} call proves the whole changelog applies cleanly; the count pins the
   * individuals changeset.
   */
  @Test
  void testUserPlantsMigration_AppliesCleanly_OnFreshDatabase() throws Exception {
    String database = createDatabase("individuals_fresh");
    apply(database, MASTER_CHANGELOG);

    assertEquals(1, changesetCount(database, INDIVIDUALS_CHANGESET_ID));
  }

  /**
   * Simulates a deployment already at the pre-#37 schema: changesets 001-006 applied alone, then
   * the full changelog. Precondition: database at the schema preceding this ticket.
   */
  @Test
  void testUserPlantsMigration_AppliesCleanly_OnDatabaseAlreadyAtPrecedingSchema()
      throws Exception {
    String database = createDatabase("individuals_upgrade");
    applyPreceding(database);
    apply(database, MASTER_CHANGELOG);

    assertEquals(1, changesetCount(database, INDIVIDUALS_CHANGESET_ID));
  }

  /**
   * After the migration a user may track two individuals of the same catalog plant: the old unique
   * (user_id, plant_id) constraint is gone.
   */
  @Test
  void testUserPlantsSchema_AllowsDuplicateIndividuals_WhenSameUserAddsSamePlantTwice()
      throws Exception {
    String database = createDatabase("individuals_duplicate");
    apply(database, MASTER_CHANGELOG);

    try (Connection connection = connect(database);
        Statement statement = connection.createStatement()) {
      long userId = insertUser(statement, "individuals-duplicate");
      long plantId = insertPlant(statement, "Monstera individuals duplicate");
      insertUserPlant(statement, userId, plantId);
      insertUserPlant(statement, userId, plantId);

      assertEquals(
          2, count(statement, "SELECT COUNT(*) FROM user_plants WHERE user_id = " + userId));
    }
  }

  /**
   * The composite (user_id, plant_id) index supports the individual listing and is not unique, so
   * duplicates are allowed.
   */
  @Test
  void testUserPlantsSchema_CreatesNonUniqueCompositeIndex_OnMigratedSchema() throws Exception {
    String database = createDatabase("individuals_index");
    apply(database, MASTER_CHANGELOG);

    try (Connection connection = connect(database)) {
      assertEquals("btree", indexType(connection, "user_plants", "idx_user_plants_user_plant"));
      assertFalse(
          indexIsUnique(connection, "user_plants", "idx_user_plants_user_plant"),
          "the individual listing index must allow duplicate pairs");
    }
  }

  /** Both watering dates become nullable, so "I don't remember" can be stored. */
  @Test
  void testUserPlantsSchema_MakesWateringDatesNullable_OnMigratedSchema() throws Exception {
    String database = createDatabase("individuals_nullable_dates");
    apply(database, MASTER_CHANGELOG);

    try (Connection connection = connect(database);
        Statement statement = connection.createStatement()) {
      assertTrue(columnIsNullable(connection, "user_plants", "last_watered_at"));
      assertTrue(columnIsNullable(connection, "user_plants", "next_water_due_at"));

      long userId = insertUser(statement, "individuals-nullable");
      long plantId = insertPlant(statement, "Monstera individuals nullable");
      long userPlantId =
          insertAndReturnId(
              statement,
              "INSERT INTO user_plants (user_id, plant_id, nickname, last_watered_at,"
                  + " next_water_due_at) VALUES ("
                  + userId
                  + ", "
                  + plantId
                  + ", '', NULL, NULL)");

      assertTrue(userPlantId > 0, "a plant with unknown watering dates must be storable");
    }
  }

  /**
   * The room link is an optional one-to-many target owned by the individual: deleting the room
   * clears the link instead of deleting the individual.
   */
  @Test
  void testUserPlantsSchema_ClearsRoom_WhenRoomDeleted() throws Exception {
    String database = createDatabase("individuals_room_fk");
    apply(database, MASTER_CHANGELOG);

    try (Connection connection = connect(database);
        Statement statement = connection.createStatement()) {
      long userId = insertUser(statement, "individuals-room-fk");
      long plantId = insertPlant(statement, "Monstera individuals room fk");
      long houseId = insertHouse(statement, userId, "Home");
      long roomId = insertRoom(statement, houseId, "Kitchen");
      long userPlantId =
          insertAndReturnId(
              statement,
              "INSERT INTO user_plants (user_id, plant_id, nickname, room_id) VALUES ("
                  + userId
                  + ", "
                  + plantId
                  + ", '', "
                  + roomId
                  + ")");

      statement.executeUpdate("DELETE FROM rooms WHERE id = " + roomId);

      assertEquals(
          null,
          queryLong(statement, "SELECT room_id FROM user_plants WHERE id = " + userPlantId),
          "deleting the room must clear the individual's location");
    }
  }

  /**
   * When several plant_locations rows map to one individual, the oldest assignment wins. The
   * pre-#37 fixture has no unique user-plant choice, so the migration must pick deterministically.
   */
  @Test
  void testUserPlantMigration_KeepsOldestAssignment_WhenSeveralMapToOneIndividual()
      throws Exception {
    String database = createDatabase("individuals_oldest");
    applyPreceding(database);

    long userId;
    long plantId;
    long userPlantId;
    long oldestRoomId;
    try (Connection connection = connect(database);
        Statement statement = connection.createStatement()) {
      userId = insertUser(statement, "individuals-oldest");
      plantId = insertPlant(statement, "Monstera individuals oldest");
      userPlantId = insertUserPlant(statement, userId, plantId);
      long houseId = insertHouse(statement, userId, "Home");
      oldestRoomId = insertRoom(statement, houseId, "Oldest room");
      long newestRoomId = insertRoom(statement, houseId, "Newest room");
      insertPlantLocation(statement, plantId, newestRoomId, "2026-01-02 10:00:00");
      insertPlantLocation(statement, plantId, oldestRoomId, "2026-01-01 10:00:00");
    }

    apply(database, MASTER_CHANGELOG);

    try (Connection connection = connect(database);
        Statement statement = connection.createStatement()) {
      assertEquals(
          oldestRoomId,
          queryLong(statement, "SELECT room_id FROM user_plants WHERE id = " + userPlantId),
          "the oldest assignment must win when several map to one individual");
    }
  }

  /**
   * Assignments are only migrated when the room's house owner already tracks that catalog plant. A
   * plant the user never added and an assignment in another user's house are both dropped.
   */
  @Test
  void testUserPlantMigration_DropsAssignments_WhenHouseOwnerHasNoIndividual() throws Exception {
    String database = createDatabase("individuals_unmapped");
    applyPreceding(database);

    long trackedUserPlantId;
    long untrackedPlantId;
    try (Connection connection = connect(database);
        Statement statement = connection.createStatement()) {
      long trackedUser = insertUser(statement, "individuals-tracked");
      long otherUser = insertUser(statement, "individuals-other");
      long trackedPlantId = insertPlant(statement, "Monstera individuals tracked");
      untrackedPlantId = insertPlant(statement, "Ficus individuals untracked");
      trackedUserPlantId = insertUserPlant(statement, trackedUser, trackedPlantId);
      long trackedHouseId = insertHouse(statement, trackedUser, "Tracked home");
      long trackedRoomId = insertRoom(statement, trackedHouseId, "Tracked room");
      long otherHouseId = insertHouse(statement, otherUser, "Other home");
      long otherRoomId = insertRoom(statement, otherHouseId, "Other room");

      // The user never added this catalog plant, so there is no individual to update.
      insertPlantLocation(statement, untrackedPlantId, trackedRoomId, "2026-01-01 10:00:00");
      // The assignment lives in another user's house, which does not own the caller's individual.
      insertPlantLocation(statement, trackedPlantId, otherRoomId, "2026-01-01 10:00:00");
    }

    apply(database, MASTER_CHANGELOG);

    try (Connection connection = connect(database);
        Statement statement = connection.createStatement()) {
      assertNull(
          queryLong(statement, "SELECT room_id FROM user_plants WHERE id = " + trackedUserPlantId),
          "an assignment under another user's house must not be migrated");
      assertEquals(
          0,
          count(statement, "SELECT COUNT(*) FROM user_plants WHERE plant_id = " + untrackedPlantId),
          "an assignment for a never-added catalog plant must be dropped");
    }
  }

  /** The retired plant_locations table is gone after the migration. */
  @Test
  void testUserPlantMigration_DropsPlantLocationsTable_WhenMigrated() throws Exception {
    String database = createDatabase("individuals_table_gone");
    apply(database, MASTER_CHANGELOG);

    try (Connection connection = connect(database);
        Statement statement = connection.createStatement()) {
      assertEquals(
          0,
          count(
              statement,
              "SELECT COUNT(*) FROM information_schema.tables"
                  + " WHERE table_schema = 'public' AND table_name = 'plant_locations'"),
          "plant_locations must be dropped");
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

  private long insertUser(Statement statement, String username) throws SQLException {
    return insertAndReturnId(
        statement,
        "INSERT INTO users (username, email, password_hash, first_name, last_name) VALUES ('"
            + username
            + "', '"
            + username
            + "@example.com', 'hash', 'Test', 'User')");
  }

  private long insertPlant(Statement statement, String scientificName) throws SQLException {
    return insertAndReturnId(
        statement,
        "INSERT INTO plants (name, scientific_name, description, watering_frequency)"
            + " VALUES ('Monstera', '"
            + scientificName
            + "', 'seeded for migration tests', 7)");
  }

  private long insertUserPlant(Statement statement, long userId, long plantId) throws SQLException {
    return insertAndReturnId(
        statement,
        "INSERT INTO user_plants (user_id, plant_id, nickname, last_watered_at, next_water_due_at)"
            + " VALUES ("
            + userId
            + ", "
            + plantId
            + ", '', now(), now() + interval '7 days')");
  }

  private long insertHouse(Statement statement, long userId, String name) throws SQLException {
    return insertAndReturnId(
        statement, "INSERT INTO houses (user_id, name) VALUES (" + userId + ", '" + name + "')");
  }

  private long insertRoom(Statement statement, long houseId, String name) throws SQLException {
    return insertAndReturnId(
        statement, "INSERT INTO rooms (house_id, name) VALUES (" + houseId + ", '" + name + "')");
  }

  private void insertPlantLocation(Statement statement, long plantId, long roomId, String createdAt)
      throws SQLException {
    statement.executeUpdate(
        "INSERT INTO plant_locations (plant_id, room_id, created_at) VALUES ("
            + plantId
            + ", "
            + roomId
            + ", TIMESTAMP '"
            + createdAt
            + "')");
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

  private Long queryLong(Statement statement, String sql) throws SQLException {
    try (ResultSet resultSet = statement.executeQuery(sql)) {
      resultSet.next();
      long value = resultSet.getLong(1);
      return resultSet.wasNull() ? null : value;
    }
  }

  private boolean columnIsNullable(Connection connection, String table, String column)
      throws SQLException {
    try (Statement statement = connection.createStatement();
        ResultSet resultSet =
            statement.executeQuery(
                "SELECT is_nullable FROM information_schema.columns"
                    + " WHERE table_schema = 'public' AND table_name = '"
                    + table
                    + "' AND column_name = '"
                    + column
                    + "'")) {
      if (!resultSet.next()) {
        fail("expected column " + column + " on " + table);
      }
      return resultSet.getString(1).equals("YES");
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

  private boolean indexIsUnique(Connection connection, String table, String index)
      throws SQLException {
    try (Statement statement = connection.createStatement();
        ResultSet resultSet =
            statement.executeQuery(
                "SELECT idx.indisunique FROM pg_index idx"
                    + " JOIN pg_class index_class ON index_class.oid = idx.indexrelid"
                    + " JOIN pg_class table_class ON table_class.oid = idx.indrelid"
                    + " WHERE table_class.relname = '"
                    + table
                    + "'"
                    + " AND index_class.relname = '"
                    + index
                    + "'")) {
      if (!resultSet.next()) {
        fail("expected index " + index + " on " + table);
      }
      return resultSet.getBoolean("indisunique");
    }
  }
}
