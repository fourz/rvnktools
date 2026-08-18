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

    // Cross-host safe ceiling for pool timeouts (#1817/#1822 family). Applied to every RVNKCore MySQL
    // pool regardless of config so an existing server's stale config.yml cannot hold idle connections
    // past the network's silent drop window. Config may request lower, never higher.
    private static final long MAX_SAFE_IDLE_TIMEOUT_MS = 120_000L;
    private static final long MAX_SAFE_MAX_LIFETIME_MS = 180_000L;

    // Outage logging (#2017). A database outage is an EXPECTED condition on a cross-host MySQL —
    // the provider already signals it to callers by throwing, so logging a full stack trace on
    // every retry adds no diagnostic value and is an availability risk in its own right: a caller
    // that retries on a timer (RVNKWorlds' world sync runs every 60s) turns a multi-hour outage
    // into tens of thousands of log lines. That is the same shape as the DEBUG-plus-load incident
    // that queued ~150k lines and blocked shutdown for ~11 minutes (#1548).
    // Policy: full trace ONCE when an outage begins, a single-line summary at most every
    // SUMMARY_INTERVAL_MS while it continues, and one line when it recovers.
    private static final long OUTAGE_SUMMARY_INTERVAL_MS = 300_000L;

    private final ConnectionOutageLog outageLog = new ConnectionOutageLog(OUTAGE_SUMMARY_INTERVAL_MS);

    private HikariDataSource dataSource;
    private final DatabaseConfig config;
    private final LogManager logger;
    private final String poolOwner;
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
        this.poolOwner = (plugin != null) ? plugin.getName() : "unknown";
        initializeDataSource();
    }
    
    @Override
    public Connection getConnection() throws SQLException {
        ensureInitialized();
        
        try {
            Connection conn = dataSource.getConnection();
            if (!conn.isValid(5)) {
                // #1766: return the dead connection to the pool (HikariCP evicts it) before failing —
                // throwing while it is still checked out leaks it, and repeated validation failures on
                // a cross-host drop exhaust the pool (the "Apparent connection leak" warnings).
                try {
                    conn.close();
                } catch (SQLException closeEx) {
                    logger.debug("Failed to close invalid connection: " + closeEx.getMessage());
                }
                throw new SQLException("Connection validation failed");
            }
            recordConnectionSuccess();
            return conn;
        } catch (SQLException e) {
            recordConnectionFailure(e);
            throw new DatabaseException("MySQL connection failed", e);
        }
    }

    /**
     * Notes a successful borrow, and reports recovery if an outage was in progress.
     */
    private void recordConnectionSuccess() {
        // Duration must be read before recordSuccess(), which resets the outage clock.
        long durationMs = outageLog.outageDurationMs(System.currentTimeMillis());
        int failures = outageLog.recordSuccess();
        if (failures > 0) {
            logger.warning("MySQL connection recovered after " + failures
                    + " failed attempt(s) over " + ConnectionOutageLog.describeDuration(durationMs));
        }
    }

    /**
     * Logs a connection failure without flooding the console on a sustained outage.
     *
     * <p>The first failure of an outage carries the full stack trace, because that is the one
     * that explains the cause. Everything after it is the same cause repeating, so it is
     * collapsed into a periodic one-line summary. Callers still receive the exception either
     * way — this governs logging only, never control flow.</p>
     */
    private void recordConnectionFailure(SQLException e) {
        long now = System.currentTimeMillis();
        ConnectionOutageLog.Action action = outageLog.recordFailure(now);

        switch (action) {
            case FULL_TRACE:
                logger.error("Failed to obtain MySQL connection", e);
                break;
            case SUMMARY:
                logger.warning("MySQL still unavailable - " + outageLog.failureCount()
                        + " failed attempt(s) over "
                        + ConnectionOutageLog.describeDuration(outageLog.outageDurationMs(now))
                        + "; last error: " + e.getMessage()
                        + " (repeat stack traces suppressed)");
                break;
            case SUPPRESS:
            default:
                logger.debug("MySQL connection failure " + outageLog.failureCount()
                        + ": " + e.getMessage());
                break;
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
            // Debug, not warning: isValid() is polled by health checks and adapters, so during an
            // outage this fires on every poll. recordConnectionFailure() owns the operator-facing
            // narrative for an outage; this line would only duplicate it once per poll (#2017).
            logger.debug("MySQL connection validation failed: " + e.getMessage());
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

            // Cross-host safe ceiling (following #1817/#1822): the RVNK MySQL host is on a different
            // machine from the game servers, and network gear silently drops idle TCP with no FIN, so
            // a pooled connection held past this window comes back dead ("Communications link failure /
            // Socket is closed"). Cap idle/lifetime here regardless of config so a stale config.yml —
            // the old 600000/1800000 defaults that saveResource() never overwrites on an existing
            // server (#1563/#1592) — cannot reintroduce the churn. Config may request LOWER, never higher.
            final long idleTimeout = Math.min(config.getIdleTimeoutMs(), MAX_SAFE_IDLE_TIMEOUT_MS);
            final long maxLifetime = Math.min(config.getMaxLifetimeMs(), MAX_SAFE_MAX_LIFETIME_MS);
            if (idleTimeout != config.getIdleTimeoutMs() || maxLifetime != config.getMaxLifetimeMs()) {
                logger.info("Capping pool timeouts for cross-host safety: idleTimeout "
                        + config.getIdleTimeoutMs() + "->" + idleTimeout + "ms, maxLifetime "
                        + config.getMaxLifetimeMs() + "->" + maxLifetime + "ms (config exceeded the safe ceiling)");
            }
            hikariConfig.setIdleTimeout(idleTimeout);
            hikariConfig.setMaxLifetime(maxLifetime);
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
            
            // Pool naming for monitoring — include the owning plugin so logs are attributable
            // (#1629). Every plugin builds its own pool through this provider but they all used to
            // share the literal name "RVNKCore-MySQL-Pool", making it impossible to tell whose pool
            // a shutdown/leak line referred to — which is how #1629 was first misdiagnosed as one
            // shared pool being torn down under everyone.
            hikariConfig.setPoolName("RVNKCore-MySQL-Pool-" + poolOwner);
            
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
        
        // Always apply the required MySQL safety params (socketTimeout / connectTimeout /
        // tcpKeepAlive), merging with any operator-set connectionParameters (#1629 P4 / #1546).
        // Applied HERE at the provider level — not only in RVNKCore's ConfigLoader — so every pool
        // is protected, including plugins that build their own DatabaseConfig (RVNKWorlds, RVNKLore)
        // and never called withRequiredMySqlParams. Without socketTimeout a dropped cross-host
        // connection blocks the driver forever, HikariCP cannot reclaim it, and the pool degrades
        // until exhaustion — the exact failure behind the #1629 leak. withRequiredMySqlParams is
        // idempotent (skips any param already present), so double-application is harmless.
        String requiredParams = DatabaseConfig.withRequiredMySqlParams(config.getConnectionParameters());
        if (!requiredParams.isEmpty()) {
            params.add(requiredParams);
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
