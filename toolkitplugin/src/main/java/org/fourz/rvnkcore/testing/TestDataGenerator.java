package org.fourz.rvnkcore.testing;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

/**
 * Abstract base class for test data generation across RVNK plugins.
 * Provides deterministic UUID generation, consistent test player names,
 * and dialect-aware SQL execution for both MySQL and SQLite.
 *
 * <p>Plugin-specific generators should extend this class and implement
 * the abstract seed, cleanup, and cleanupByPlayer methods.</p>
 *
 * <p>Usage example:
 * <pre>
 * public class QuestsTestDataGenerator extends TestDataGenerator {
 *     public QuestsTestDataGenerator(DatabaseManager db) {
 *         super(db.getLogger(), db::isMySQL, db::getConnection);
 *     }
 *
 *     public CompletableFuture&lt;Integer&gt; seed(DataCategory category) {
 *         // Generate test data
 *     }
 * }
 * </pre>
 * </p>
 */
public abstract class TestDataGenerator {

    /**
     * Data generation categories with associated record counts.
     */
    public enum DataCategory {
        /** Quick validation - 10 base records */
        MINIMAL(10),
        /** Normal testing - 100 base records */
        STANDARD(100),
        /** Performance testing - 1000 base records */
        STRESS(1000);

        private final int baseCount;

        DataCategory(int baseCount) {
            this.baseCount = baseCount;
        }

        /**
         * Get the base record count for this category.
         * @return base number of records to generate
         */
        public int getBaseCount() {
            return baseCount;
        }
    }

    /** Seed date for deterministic timestamp generation */
    protected static final Instant SEED_DATE = Instant.parse("2024-01-01T00:00:00Z");

    /** Random instance for non-deterministic elements */
    protected final Random random = new Random(42); // Seeded for reproducibility

    /** Logger for the generator */
    protected final Logger logger;

    /** Function to check if using MySQL (true) or SQLite (false) */
    protected final java.util.function.Supplier<Boolean> isMySQLSupplier;

    /** Function to get database connection */
    protected final java.util.function.Supplier<Connection> connectionSupplier;

    /**
     * Create a new TestDataGenerator.
     *
     * @param logger logger instance for output
     * @param isMySQLSupplier supplier that returns true for MySQL, false for SQLite
     * @param connectionSupplier supplier that provides database connections
     */
    protected TestDataGenerator(
            Logger logger,
            java.util.function.Supplier<Boolean> isMySQLSupplier,
            java.util.function.Supplier<Connection> connectionSupplier) {
        this.logger = logger;
        this.isMySQLSupplier = isMySQLSupplier;
        this.connectionSupplier = connectionSupplier;
    }

    // ========== Deterministic Data Generation ==========

    /**
     * Generate a deterministic UUID from a seed value.
     * The same seed will always produce the same UUID.
     *
     * @param seed integer seed value
     * @return deterministic UUID based on the seed
     */
    protected UUID testUUID(int seed) {
        return UUID.nameUUIDFromBytes(("test-player-" + seed).getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Generate a deterministic player name from a seed value.
     *
     * @param seed integer seed value
     * @return player name in format "TestPlayer###"
     */
    protected String testPlayerName(int seed) {
        return "TestPlayer" + String.format("%03d", seed);
    }

    /**
     * Generate a deterministic timestamp relative to a seed date.
     *
     * @param daysAgo number of days before the seed date
     * @return timestamp for that date
     */
    protected Timestamp testTimestamp(int daysAgo) {
        return Timestamp.from(SEED_DATE.minus(daysAgo, ChronoUnit.DAYS));
    }

    /**
     * Generate a test world name.
     *
     * @param seed integer seed value
     * @return world name in format "test_world_###"
     */
    protected String testWorldName(int seed) {
        return "test_world_" + seed;
    }

    /**
     * Generate a random double within a range (uses seeded random for reproducibility).
     *
     * @param min minimum value (inclusive)
     * @param max maximum value (exclusive)
     * @return random double in range
     */
    protected double randomDouble(double min, double max) {
        return min + (random.nextDouble() * (max - min));
    }

    /**
     * Generate a random int within a range (uses seeded random for reproducibility).
     *
     * @param min minimum value (inclusive)
     * @param max maximum value (exclusive)
     * @return random int in range
     */
    protected int randomInt(int min, int max) {
        return min + random.nextInt(max - min);
    }

    /**
     * Select a random element from an array.
     *
     * @param <T> element type
     * @param array array to select from
     * @return random element
     */
    protected <T> T randomElement(T[] array) {
        return array[random.nextInt(array.length)];
    }

    // ========== Dialect-Aware SQL Helpers ==========

    /**
     * Check if the current database is MySQL.
     *
     * @return true for MySQL, false for SQLite
     */
    protected boolean isMySQL() {
        return isMySQLSupplier.get();
    }

    /**
     * Get a database connection.
     *
     * @return database connection
     */
    protected Connection getConnection() {
        return connectionSupplier.get();
    }

    /**
     * Get the SQL statement to disable foreign key checks.
     *
     * @return dialect-appropriate SQL statement
     */
    protected String disableForeignKeyChecks() {
        return isMySQL() ? "SET FOREIGN_KEY_CHECKS=0" : "PRAGMA foreign_keys=OFF";
    }

    /**
     * Get the SQL statement to enable foreign key checks.
     *
     * @return dialect-appropriate SQL statement
     */
    protected String enableForeignKeyChecks() {
        return isMySQL() ? "SET FOREIGN_KEY_CHECKS=1" : "PRAGMA foreign_keys=ON";
    }

    /**
     * Get the SQL statement to truncate/clear a table.
     *
     * @param tableName name of the table to truncate
     * @return dialect-appropriate SQL statement
     */
    protected String truncateTable(String tableName) {
        return isMySQL() ? "TRUNCATE TABLE " + tableName : "DELETE FROM " + tableName;
    }

    /**
     * Get the SQL statement to reset auto-increment on a table.
     *
     * @param tableName name of the table
     * @return dialect-appropriate SQL statement, or null if not needed
     */
    protected String resetAutoIncrement(String tableName) {
        if (isMySQL()) {
            return "ALTER TABLE " + tableName + " AUTO_INCREMENT=1";
        } else {
            return "DELETE FROM sqlite_sequence WHERE name='" + tableName + "'";
        }
    }

    /**
     * Get the current timestamp expression for SQL.
     *
     * @return dialect-appropriate timestamp expression
     */
    protected String currentTimestamp() {
        return isMySQL() ? "NOW()" : "datetime('now')";
    }

    /**
     * Execute a SQL statement without returning results.
     *
     * @param sql SQL statement to execute
     * @throws SQLException if execution fails
     */
    protected void executeSQL(String sql) throws SQLException {
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.execute();
        }
    }

    /**
     * Execute a batch of SQL statements, typically for cleanup.
     *
     * @param statements array of SQL statements
     * @throws SQLException if any execution fails
     */
    protected void executeBatch(String... statements) throws SQLException {
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try {
                for (String sql : statements) {
                    try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                        stmt.execute();
                    }
                }
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    // ========== Abstract Methods ==========

    /**
     * Seed test data into the database.
     *
     * @param category data category determining volume
     * @return CompletableFuture with the total number of records created
     */
    public abstract CompletableFuture<Integer> seed(DataCategory category);

    /**
     * Remove all test data from the database.
     *
     * @return CompletableFuture with success status
     */
    public abstract CompletableFuture<Boolean> cleanup();

    /**
     * Remove test data for a specific player.
     *
     * @param playerUuid player UUID to clean up
     * @return CompletableFuture with the number of records deleted
     */
    public abstract CompletableFuture<Integer> cleanupByPlayer(UUID playerUuid);

    /**
     * Get the name of this generator for logging.
     *
     * @return generator name (e.g., "QuestsTestDataGenerator")
     */
    public abstract String getGeneratorName();

    // ========== Logging Helpers ==========

    /**
     * Log an info message with the generator name prefix.
     *
     * @param message message to log
     */
    protected void logInfo(String message) {
        logger.info("[" + getGeneratorName() + "] " + message);
    }

    /**
     * Log a warning message with the generator name prefix.
     *
     * @param message message to log
     */
    protected void logWarning(String message) {
        logger.warning("[" + getGeneratorName() + "] " + message);
    }

    /**
     * Log a severe message with the generator name prefix.
     *
     * @param message message to log
     */
    protected void logSevere(String message) {
        logger.severe("[" + getGeneratorName() + "] " + message);
    }

    /**
     * Log a seeding progress message.
     *
     * @param tableName table being seeded
     * @param count number of records created
     */
    protected void logSeeded(String tableName, int count) {
        logInfo("Generated " + count + " " + tableName + " records");
    }
}
