package org.fourz.rvnkcore.database.connection;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.fourz.rvnkcore.api.exception.DatabaseException;
import org.fourz.rvnkcore.database.config.DatabaseConfig;
import org.fourz.rvnkcore.util.log.LogManager;

import org.bukkit.plugin.Plugin;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

/**
 * MySQL implementation of ConnectionProvider using HikariCP for connection pooling.
 * 
 * Provides production-ready database connections to MySQL servers with
 * optimized connection pooling, SSL/TLS support, and comprehensive health monitoring.
 * 
 * This implementation uses HikariCP for optimal performance and connection management
 * in multi-threaded environments typical of Minecraft server plugins.
 * 
 * @since 1.0.0
 */
public class MySQLConnectionProvider implements ConnectionProvider {
    
    private HikariDataSource dataSource;
    private final DatabaseConfig config;
    private final LogManager logger;
    private final ReentrantLock initializationLock = new ReentrantLock();
    private volatile boolean initialized = false;
    
    /**
     * Constructor for MySQLConnectionProvider.
     * 
     * @param config The database configuration
     * @param plugin The plugin instance for logging
     * @throws DatabaseException If configuration is invalid
     */
    public MySQLConnectionProvider(DatabaseConfig config, Plugin plugin) {
        this.config = validateConfig(config);
        this.logger = LogManager.getInstance(plugin);
        initializeDataSource();
    }
    
    @Override
    public Connection getConnection() throws SQLException {
        ensureInitialized();
        
        try {
            Connection conn = dataSource.getConnection();
            if (!conn.isValid(5)) {
                throw new SQLException("Connection validation failed");
            }
            return conn;
        } catch (SQLException e) {
            logger.error("Failed to obtain MySQL connection", e);
            throw new DatabaseException("MySQL connection failed", e);
        }
    }
    
    @Override
    public boolean isValid() {
        if (!initialized || dataSource == null || dataSource.isClosed()) {
            return false;
        }
        
        try (Connection testConn = dataSource.getConnection()) {
            return testConn.isValid(5);
        } catch (SQLException e) {
            logger.warning("MySQL connection validation failed: " + e.getMessage());
            return false;
        }
    }
    
    @Override
    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            logger.info("Shutting down MySQL connection pool");
            dataSource.close();
            initialized = false;
        }
    }
    
    @Override
    public String getDatabaseType() {
        return "MySQL";
    }
    
    /**
     * Gets the current active connections from the pool.
     * 
     * @return Number of active connections
     */
    public int getActiveConnections() {
        return dataSource != null ? dataSource.getHikariPoolMXBean().getActiveConnections() : 0;
    }
    
    /**
     * Gets the current idle connections in the pool.
     * 
     * @return Number of idle connections
     */
    public int getIdleConnections() {
        return dataSource != null ? dataSource.getHikariPoolMXBean().getIdleConnections() : 0;
    }
    
    /**
     * Gets the total connections in the pool.
     * 
     * @return Total number of connections
     */
    public int getTotalConnections() {
        return dataSource != null ? dataSource.getHikariPoolMXBean().getTotalConnections() : 0;
    }
    
    /**
     * Initializes the HikariCP data source with optimized settings.
     */
    private void initializeDataSource() {
        initializationLock.lock();
        try {
            if (initialized) {
                return;
            }
            
            logger.debug("Initializing MySQL connection provider - Host: " + config.getHost() + ":" + config.getPort() + ", Database: " + config.getDatabase());
            logger.debug("Initializing MySQL connection pool");
            
            HikariConfig hikariConfig = new HikariConfig();
            
            // Connection URL building with SSL support
            hikariConfig.setJdbcUrl(buildConnectionUrl());
            hikariConfig.setUsername(config.getUsername());
            hikariConfig.setPassword(config.getPassword());
            
            // Connection Pool Configuration
            hikariConfig.setMaximumPoolSize(config.getMaxConnections());
            hikariConfig.setMinimumIdle(config.getMinIdleConnections());
            hikariConfig.setConnectionTimeout(config.getConnectionTimeoutMs());
            hikariConfig.setIdleTimeout(config.getIdleTimeoutMs());
            hikariConfig.setMaxLifetime(config.getMaxLifetimeMs());
            hikariConfig.setKeepaliveTime(config.getKeepaliveTimeMs());

            // Health and Monitoring
            hikariConfig.setLeakDetectionThreshold(config.getLeakDetectionMs());
            hikariConfig.setConnectionTestQuery("SELECT 1");
            
            // Performance Optimizations
            hikariConfig.addDataSourceProperty("cachePrepStmts", String.valueOf(config.isCachePrepStmts()));
            hikariConfig.addDataSourceProperty("prepStmtCacheSize", String.valueOf(config.getPrepStmtCacheSize()));
            hikariConfig.addDataSourceProperty("prepStmtCacheSqlLimit", String.valueOf(config.getPrepStmtCacheSqlLimit()));
            hikariConfig.addDataSourceProperty("useServerPrepStmts", "true");
            
            // MySQL-specific optimizations
            hikariConfig.addDataSourceProperty("useLocalSessionState", "true");
            hikariConfig.addDataSourceProperty("rewriteBatchedStatements", "true");
            hikariConfig.addDataSourceProperty("cacheResultSetMetadata", "true");
            hikariConfig.addDataSourceProperty("cacheServerConfiguration", "true");
            hikariConfig.addDataSourceProperty("elideSetAutoCommits", "true");
            hikariConfig.addDataSourceProperty("maintainTimeStats", "false");
            
            // Pool naming for monitoring
            hikariConfig.setPoolName("RVNKCore-MySQL-Pool");
            
            this.dataSource = new HikariDataSource(hikariConfig);
            this.initialized = true;
            
            logger.debug("MySQL connection pool initialized successfully - Max connections: " + 
                       config.getMaxConnections() + ", Min idle: " + config.getMinIdleConnections());
            
        } catch (Exception e) {
            // Parse the exception to provide helpful, concise error messages
            String rootCause = getRootCauseMessage(e);
            
            if (rootCause.contains("Access denied")) {
                // Extract username from error message
                String user = config.getUsername();
                logger.error("MySQL Database Connection - Access denied for user '" + user + 
                    "' - Invalid credentials or insufficient permissions. " +
                    "Verify username/password in config.yml and ensure user has database access");
            } else if (rootCause.contains("Unknown database")) {
                logger.error("MySQL Database Connection - Database '" + config.getDatabase() + 
                    "' does not exist on server " + config.getHost() + ". " +
                    "Create the database or verify the database name in config.yml");
            } else if (rootCause.contains("Communications link failure") || rootCause.contains("Connection refused")) {
                logger.error("MySQL Database Connection - Cannot reach MySQL server at " + 
                    config.getHost() + ":" + config.getPort() + ". " +
                    "Verify server is running, check host/port in config.yml, and ensure firewall allows connection");
            } else if (rootCause.contains("Unknown host")) {
                logger.error("MySQL Database Connection - Cannot resolve hostname '" + config.getHost() + "'. " +
                    "Verify the MySQL host address in config.yml");
            } else if (rootCause.contains("SSL connection error") || rootCause.contains("SSL")) {
                logger.error("MySQL Database Connection - SSL/TLS connection failed - " + rootCause + ". " +
                    "Check SSL configuration or set 'useSSL: false' in config.yml for local testing");
            } else {
                // Generic database connection error
                logger.error("MySQL Database Connection - Connection failed - " + rootCause + ". " +
                    "Check MySQL server status and configuration settings");
            }
            
            // Log debug info if enabled
            if (logger.isDebugEnabled()) {
                logger.debug("Full MySQL connection error details", e);
            }
            
            throw new DatabaseException("MySQL connection pool initialization failed", e);
        } finally {
            initializationLock.unlock();
        }
    }
    
    /**
     * Builds the JDBC connection URL with all parameters.
     * 
     * @return Complete JDBC URL
     */
    private String buildConnectionUrl() {
        StringBuilder url = new StringBuilder();
        url.append("jdbc:mysql://")
           .append(config.getHost())
           .append(":").append(config.getPort())
           .append("/").append(config.getDatabase());
        
        // Build parameter list
        List<String> params = new ArrayList<>();
        
        // SSL Configuration
        params.add("useSSL=" + config.isUseSSL());
        if (config.isUseSSL()) {
            params.add("requireSSL=true");
            params.add("verifyServerCertificate=false");
        }
        
        // Connection parameters
        params.add("serverTimezone=UTC");
        params.add("characterEncoding=UTF-8");
        params.add("autoReconnect=true");
        params.add("failOverReadOnly=false");
        params.add("maxReconnects=3");
        params.add("initialTimeout=1");
        
        // Performance parameters
        params.add("useUnicode=true");
        params.add("allowMultiQueries=true");
        params.add("allowPublicKeyRetrieval=true");
        
        // Add custom connection parameters if provided
        if (config.getConnectionParameters() != null && !config.getConnectionParameters().trim().isEmpty()) {
            params.add(config.getConnectionParameters());
        }
        
        url.append("?").append(String.join("&", params));
        
        // Log connection URL (without password) for debugging
        if (logger.isDebugEnabled()) {
            String debugUrl = url.toString().replaceAll("password=[^&]*", "password=***");
            logger.info("MySQL connection URL: " + debugUrl);
        }
        
        return url.toString();
    }
    
    /**
     * Validates the database configuration for MySQL.
     * 
     * @param config The configuration to validate
     * @return The validated configuration
     * @throws IllegalArgumentException If configuration is invalid
     */
    private DatabaseConfig validateConfig(DatabaseConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("Database configuration cannot be null");
        }
        
        if (!"mysql".equalsIgnoreCase(config.getType())) {
            throw new IllegalArgumentException("MySQLConnectionProvider requires type 'mysql'");
        }
        
        if (config.getHost() == null || config.getHost().trim().isEmpty()) {
            throw new IllegalArgumentException("MySQL host cannot be null or empty");
        }
        
        if (config.getDatabase() == null || config.getDatabase().trim().isEmpty()) {
            throw new IllegalArgumentException("MySQL database name cannot be null or empty");
        }
        
        if (config.getUsername() == null || config.getUsername().trim().isEmpty()) {
            throw new IllegalArgumentException("MySQL username cannot be null or empty");
        }
        
        if (config.getPort() <= 0 || config.getPort() > 65535) {
            throw new IllegalArgumentException("MySQL port must be between 1 and 65535");
        }
        
        return config;
    }
    
    /**
     * Ensures the connection pool is initialized.
     * 
     * @throws SQLException If initialization fails
     */
    private void ensureInitialized() throws SQLException {
        if (!initialized) {
            initializationLock.lock();
            try {
                if (!initialized) {
                    initializeDataSource();
                }
            } finally {
                initializationLock.unlock();
            }
        }
        
        if (dataSource == null || dataSource.isClosed()) {
            throw new SQLException("MySQL connection pool is not available");
        }
    }
    
    /**
     * Gets detailed connection pool statistics for monitoring.
     * 
     * @return Connection pool statistics as a formatted string
     */
    public String getPoolStatistics() {
        if (dataSource == null) {
            return "MySQL pool not initialized";
        }
        
        var poolMXBean = dataSource.getHikariPoolMXBean();
        return String.format("MySQL Pool Stats - Active: %d, Idle: %d, Total: %d, Waiting: %d",
                poolMXBean.getActiveConnections(),
                poolMXBean.getIdleConnections(),
                poolMXBean.getTotalConnections(),
                poolMXBean.getThreadsAwaitingConnection());
    }
    
    /**
     * Helper method to get root cause message from exception chain.
     * 
     * @param e The exception to get root cause from
     * @return Root cause message
     */
    private String getRootCauseMessage(Exception e) {
        Throwable rootCause = e;
        while (rootCause.getCause() != null && rootCause.getCause() != rootCause) {
            rootCause = rootCause.getCause();
        }
        return rootCause.getMessage() != null ? rootCause.getMessage() : rootCause.getClass().getSimpleName();
    }
}
