package org.fourz.rvnkcore.database.connection;

import org.fourz.rvnkcore.api.exception.DatabaseException;
import org.fourz.rvnkcore.util.log.LogManager;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.concurrent.locks.ReentrantLock;

/**
 * SQLite implementation of ConnectionProvider.
 * 
 * Provides database connections to a local SQLite database file,
 * with automatic database file creation and connection validation.
 * 
 * This implementation creates fresh connections for each request to avoid
 * connection sharing issues with SQLite in multi-threaded environments.
 * 
 * @since 1.0.0
 */
public class SQLiteConnectionProvider implements ConnectionProvider {
    
    private final String databasePath;
    private final LogManager logger;
    private final ReentrantLock connectionLock = new ReentrantLock();
    private volatile boolean driverLoaded = false;
    
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
        
        // Only log database path during initial setup in debug mode
        if (logger.isDebugEnabled()) {
            logger.info("SQLite database path: " + this.databasePath);
        }
    }
    
    @Override
    public Connection getConnection() throws SQLException {
        // Ensure driver is loaded (thread-safe one-time initialization)
        if (!driverLoaded) {
            connectionLock.lock();
            try {
                if (!driverLoaded) {
                    Class.forName("org.sqlite.JDBC");
                    driverLoaded = true;
                    if (logger.isDebugEnabled()) {
                        logger.info("SQLite JDBC driver loaded");
                    }
                }
            } catch (ClassNotFoundException e) {
                logger.error("SQLite JDBC driver not found", e);
                throw new DatabaseException("SQLite driver not available", e);
            } finally {
                connectionLock.unlock();
            }
        }
        
        try {
            // Create a fresh connection for each request
            String url = "jdbc:sqlite:" + databasePath;
            Connection conn = DriverManager.getConnection(url);
            conn.setAutoCommit(true);
            
            // Configure SQLite-specific settings
            try (var stmt = conn.createStatement()) {
                stmt.execute("PRAGMA foreign_keys = ON");
                stmt.execute("PRAGMA journal_mode = WAL");
                stmt.execute("PRAGMA synchronous = NORMAL");
                stmt.execute("PRAGMA busy_timeout = 30000"); // 30 second timeout
            }
            
            return conn;
            
        } catch (SQLException e) {
            logger.error("Failed to create SQLite database connection", e);
            throw new DatabaseException("Database connection failed", e);
        }
    }
    
    @Override
    public boolean isValid() {
        // For fresh connection approach, always attempt to create a test connection
        try (Connection testConn = getConnection()) {
            return testConn.isValid(5);
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
        // With fresh connection approach, no persistent connection to close
        // Individual connections are closed by the calling code using try-with-resources
        if (logger.isDebugEnabled()) {
            logger.info("SQLiteConnectionProvider shutdown completed");
        }
    }
    
    @Override
    public String getDatabaseType() {
        return "SQLite";
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
