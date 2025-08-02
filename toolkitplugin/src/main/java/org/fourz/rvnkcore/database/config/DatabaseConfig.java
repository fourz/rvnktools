package org.fourz.rvnkcore.database.config;

/**
 * Database configuration model for RVNKCore database connections.
 * 
 * Provides comprehensive configuration options for both SQLite and MySQL
 * database connections, including connection pooling settings and SSL/TLS support.
 * 
 * @since 1.0.0
 */
public class DatabaseConfig {
    
    // Basic Database Settings
    private String type = "sqlite";
    private String host = "localhost";
    private int port = 3306;
    private String database;
    private String username;
    private String password;
    private boolean useSSL = true;
    private String connectionParameters;
    
    // Connection Pool Settings
    private int maxConnections = 10;
    private int minIdleConnections = 2;
    private long connectionTimeoutMs = 30000;
    private long idleTimeoutMs = 600000;
    private long maxLifetimeMs = 1800000;
    private long leakDetectionMs = 60000;
    
    // Performance Settings
    private boolean cachePrepStmts = true;
    private int prepStmtCacheSize = 250;
    private int prepStmtCacheSqlLimit = 2048;
    
    /**
     * Private constructor for builder pattern.
     */
    private DatabaseConfig() {}
    
    /**
     * Creates a new builder for DatabaseConfig.
     * 
     * @return A new DatabaseConfig builder
     */
    public static Builder builder() {
        return new Builder();
    }
    
    /**
     * Creates a default SQLite configuration.
     * 
     * @param databaseFileName The SQLite database file name
     * @return A DatabaseConfig configured for SQLite
     */
    public static DatabaseConfig sqlite(String databaseFileName) {
        return builder()
                .type("sqlite")
                .database(databaseFileName)
                .build();
    }
    
    /**
     * Creates a default MySQL configuration.
     * 
     * @param host The MySQL host
     * @param port The MySQL port
     * @param database The database name
     * @param username The username
     * @param password The password
     * @return A DatabaseConfig configured for MySQL
     */
    public static DatabaseConfig mysql(String host, int port, String database, String username, String password) {
        return builder()
                .type("mysql")
                .host(host)
                .port(port)
                .database(database)
                .username(username)
                .password(password)
                .build();
    }
    
    // Getters
    public String getType() { return type; }
    public String getHost() { return host; }
    public int getPort() { return port; }
    public String getDatabase() { return database; }
    public String getUsername() { return username; }
    public String getPassword() { return password; }
    public boolean isUseSSL() { return useSSL; }
    public String getConnectionParameters() { return connectionParameters; }
    
    public int getMaxConnections() { return maxConnections; }
    public int getMinIdleConnections() { return minIdleConnections; }
    public long getConnectionTimeoutMs() { return connectionTimeoutMs; }
    public long getIdleTimeoutMs() { return idleTimeoutMs; }
    public long getMaxLifetimeMs() { return maxLifetimeMs; }
    public long getLeakDetectionMs() { return leakDetectionMs; }
    
    public boolean isCachePrepStmts() { return cachePrepStmts; }
    public int getPrepStmtCacheSize() { return prepStmtCacheSize; }
    public int getPrepStmtCacheSqlLimit() { return prepStmtCacheSqlLimit; }
    
    /**
     * Validates the configuration and throws exceptions for invalid settings.
     * 
     * @throws IllegalArgumentException If configuration is invalid
     */
    public void validate() {
        if (type == null || type.trim().isEmpty()) {
            throw new IllegalArgumentException("Database type cannot be null or empty");
        }
        
        if (database == null || database.trim().isEmpty()) {
            throw new IllegalArgumentException("Database name cannot be null or empty");
        }
        
        if ("mysql".equalsIgnoreCase(type)) {
            if (host == null || host.trim().isEmpty()) {
                throw new IllegalArgumentException("MySQL host cannot be null or empty");
            }
            
            if (username == null || username.trim().isEmpty()) {
                throw new IllegalArgumentException("MySQL username cannot be null or empty");
            }
            
            if (port <= 0 || port > 65535) {
                throw new IllegalArgumentException("MySQL port must be between 1 and 65535");
            }
        }
        
        if (maxConnections <= 0) {
            throw new IllegalArgumentException("Max connections must be greater than 0");
        }
        
        if (minIdleConnections < 0 || minIdleConnections > maxConnections) {
            throw new IllegalArgumentException("Min idle connections must be between 0 and max connections");
        }
        
        if (connectionTimeoutMs <= 0) {
            throw new IllegalArgumentException("Connection timeout must be greater than 0");
        }
    }
    
    /**
     * Builder pattern for creating DatabaseConfig instances.
     */
    public static class Builder {
        private final DatabaseConfig config = new DatabaseConfig();
        
        public Builder type(String type) {
            config.type = type;
            return this;
        }
        
        public Builder host(String host) {
            config.host = host;
            return this;
        }
        
        public Builder port(int port) {
            config.port = port;
            return this;
        }
        
        public Builder database(String database) {
            config.database = database;
            return this;
        }
        
        public Builder username(String username) {
            config.username = username;
            return this;
        }
        
        public Builder password(String password) {
            config.password = password;
            return this;
        }
        
        public Builder useSSL(boolean useSSL) {
            config.useSSL = useSSL;
            return this;
        }
        
        public Builder connectionParameters(String parameters) {
            config.connectionParameters = parameters;
            return this;
        }
        
        public Builder maxConnections(int maxConnections) {
            config.maxConnections = maxConnections;
            return this;
        }
        
        public Builder minIdleConnections(int minIdleConnections) {
            config.minIdleConnections = minIdleConnections;
            return this;
        }
        
        public Builder connectionTimeoutMs(long timeoutMs) {
            config.connectionTimeoutMs = timeoutMs;
            return this;
        }
        
        public Builder idleTimeoutMs(long timeoutMs) {
            config.idleTimeoutMs = timeoutMs;
            return this;
        }
        
        public Builder maxLifetimeMs(long lifetimeMs) {
            config.maxLifetimeMs = lifetimeMs;
            return this;
        }
        
        public Builder leakDetectionMs(long detectionMs) {
            config.leakDetectionMs = detectionMs;
            return this;
        }
        
        public Builder cachePrepStmts(boolean cache) {
            config.cachePrepStmts = cache;
            return this;
        }
        
        public Builder prepStmtCacheSize(int size) {
            config.prepStmtCacheSize = size;
            return this;
        }
        
        public Builder prepStmtCacheSqlLimit(int limit) {
            config.prepStmtCacheSqlLimit = limit;
            return this;
        }
        
        /**
         * Builds and validates the DatabaseConfig.
         * 
         * @return A validated DatabaseConfig instance
         * @throws IllegalArgumentException If configuration is invalid
         */
        public DatabaseConfig build() {
            config.validate();
            return config;
        }
    }
    
    @Override
    public String toString() {
        return "DatabaseConfig{" +
                "type='" + type + '\'' +
                ", host='" + host + '\'' +
                ", port=" + port +
                ", database='" + database + '\'' +
                ", username='" + username + '\'' +
                ", useSSL=" + useSSL +
                ", maxConnections=" + maxConnections +
                ", minIdleConnections=" + minIdleConnections +
                '}';
    }
}
