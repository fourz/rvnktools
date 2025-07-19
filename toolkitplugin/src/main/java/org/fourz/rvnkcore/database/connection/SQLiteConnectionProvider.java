package org.fourz.rvnkcore.database.connection;

import org.fourz.rvnkcore.api.exception.DatabaseException;
import org.fourz.rvnktools.util.log.LogManager;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * SQLite implementation of ConnectionProvider.
 * 
 * Provides database connections to a local SQLite database file,
 * with automatic database file creation and connection validation.
 * 
 * @since 1.0.0
 */
public class SQLiteConnectionProvider implements ConnectionProvider {
    
    private final String databasePath;
    private final LogManager logger;
    private Connection connection;
    
    /**
     * Constructor for SQLiteConnectionProvider.
     * 
     * @param plugin The plugin instance for logging and data folder access
     * @param databaseFileName The name of the database file
     */
    public SQLiteConnectionProvider(Plugin plugin, String databaseFileName) {
        this.databasePath = new File(plugin.getDataFolder(), databaseFileName).getAbsolutePath();
        this.logger = LogManager.getInstance(plugin);
        
        // Ensure the plugin data folder exists
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }
        
        logger.info("SQLite database path: " + this.databasePath);
    }
    
    @Override
    public Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed() || !isValid(connection)) {
            try {
                // Load SQLite JDBC driver
                Class.forName("org.sqlite.JDBC");
                
                // Create connection URL for SQLite
                String url = "jdbc:sqlite:" + databasePath;
                
                // Create connection with SQLite-specific settings
                connection = DriverManager.getConnection(url);
                connection.setAutoCommit(true);
                
                // Enable foreign key constraints
                try (var stmt = connection.createStatement()) {
                    stmt.execute("PRAGMA foreign_keys = ON");
                    stmt.execute("PRAGMA journal_mode = WAL");
                    stmt.execute("PRAGMA synchronous = NORMAL");
                }
                
                logger.info("Successfully connected to SQLite database");
                
                // Create tables if they don't exist
                createTablesIfNotExists();
                
            } catch (ClassNotFoundException e) {
                logger.error("SQLite JDBC driver not found", e);
                throw new DatabaseException("SQLite driver not available", e);
            } catch (SQLException e) {
                logger.error("Failed to connect to SQLite database", e);
                throw new DatabaseException("Database connection failed", e);
            }
        }
        
        return connection;
    }
    
    @Override
    public boolean isValid() {
        if (connection == null) {
            return false;
        }
        
        try {
            return !connection.isClosed() && connection.isValid(5);
        } catch (SQLException e) {
            logger.warning("Connection validation failed: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Validates a specific connection instance.
     * 
     * @param conn The connection to validate
     * @return true if the connection is valid
     */
    public boolean isValid(Connection conn) {
        if (conn == null) {
            return false;
        }
        
        try {
            return !conn.isClosed() && conn.isValid(5);
        } catch (SQLException e) {
            logger.warning("Connection validation failed: " + e.getMessage());
            return false;
        }
    }
    
    @Override
    public void close() {
        if (connection != null) {
            try {
                if (!connection.isClosed()) {
                    connection.close();
                    logger.info("SQLite database connection closed");
                }
            } catch (SQLException e) {
                logger.error("Error closing SQLite connection", e);
            } finally {
                connection = null;
            }
        }
    }
    
    @Override
    public String getDatabaseType() {
        return "SQLite";
    }
    
    /**
     * Creates the required database tables if they don't exist.
     * 
     * @throws SQLException if table creation fails
     */
    private void createTablesIfNotExists() throws SQLException {
        String createPlayersTable = """
            CREATE TABLE IF NOT EXISTS rvnk_players (
                id TEXT PRIMARY KEY,
                current_name TEXT NOT NULL,
                name_history TEXT DEFAULT '',
                first_join TIMESTAMP NOT NULL,
                last_seen TIMESTAMP NOT NULL,
                last_world TEXT,
                last_x REAL DEFAULT 0,
                last_y REAL DEFAULT 0,
                last_z REAL DEFAULT 0,
                primary_group TEXT DEFAULT 'default',
                groups TEXT DEFAULT '',
                banned BOOLEAN DEFAULT FALSE,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
            """;
        
        String createNameHistoryIndex = """
            CREATE INDEX IF NOT EXISTS idx_players_name_history 
            ON rvnk_players(current_name, name_history)
            """;
        
        String createLastSeenIndex = """
            CREATE INDEX IF NOT EXISTS idx_players_last_seen 
            ON rvnk_players(last_seen)
            """;
        
        String createGroupIndex = """
            CREATE INDEX IF NOT EXISTS idx_players_primary_group 
            ON rvnk_players(primary_group)
            """;
        
        try (var stmt = connection.createStatement()) {
            stmt.execute(createPlayersTable);
            stmt.execute(createNameHistoryIndex);
            stmt.execute(createLastSeenIndex);
            stmt.execute(createGroupIndex);
            
            logger.info("Database schema created/verified successfully");
        } catch (SQLException e) {
            logger.error("Failed to create database schema", e);
            throw e;
        }
    }
    
    /**
     * Gets the absolute path to the database file.
     * 
     * @return The database file path
     */
    public String getDatabasePath() {
        return databasePath;
    }
    
    /**
     * Checks if the database file exists.
     * 
     * @return true if the database file exists
     */
    public boolean databaseFileExists() {
        return new File(databasePath).exists();
    }
}
