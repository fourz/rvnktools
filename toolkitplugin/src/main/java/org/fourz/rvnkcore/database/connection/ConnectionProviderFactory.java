package org.fourz.rvnkcore.database.connection;

import org.fourz.rvnkcore.database.config.DatabaseConfig;
import org.fourz.rvnkcore.config.ConfigLoader;
import org.fourz.rvnkcore.util.log.LogManager;
import org.bukkit.plugin.Plugin;

/**
 * Factory for creating ConnectionProvider instances based on configuration.
 * 
 * This factory automatically selects the appropriate ConnectionProvider
 * implementation (SQLite or MySQL) based on the database configuration.
 * 
 * @since 1.0.0
 */
public class ConnectionProviderFactory {
    
    private final Plugin plugin;
    private final LogManager logger;
    
    /**
     * Constructor for ConnectionProviderFactory.
     * 
     * @param plugin The plugin instance
     */
    public ConnectionProviderFactory(Plugin plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin);
    }
    
    /**
     * Creates a ConnectionProvider based on the current configuration.
     * 
     * @return The appropriate ConnectionProvider implementation
     * @throws IllegalStateException If configuration is invalid
     */
    public ConnectionProvider createConnectionProvider() {
        ConfigLoader configLoader = ConfigLoader.getInstance(plugin);
        DatabaseConfig config = configLoader.getDatabaseConfig();
        
        return createConnectionProvider(config);
    }
    
    /**
     * Creates a ConnectionProvider for the specified configuration.
     * 
     * @param config The database configuration
     * @return The appropriate ConnectionProvider implementation
     * @throws IllegalArgumentException If database type is unsupported
     */
    public ConnectionProvider createConnectionProvider(DatabaseConfig config) {
        String databaseType = config.getType().toLowerCase();
        
        logger.debug("Creating ConnectionProvider for database type: " + databaseType);
        
        switch (databaseType) {
            case "sqlite":
                return createSQLiteProvider(config);
            case "mysql":
                return createMySQLProvider(config);
            default:
                throw new IllegalArgumentException("Unsupported database type: " + databaseType + 
                                                 ". Supported types: sqlite, mysql");
        }
    }
    
    /**
     * Creates an SQLite ConnectionProvider.
     * 
     * @param config The database configuration
     * @return SQLiteConnectionProvider instance
     */
    private ConnectionProvider createSQLiteProvider(DatabaseConfig config) {
        logger.info("Initializing SQLite connection provider with database: " + config.getDatabase());
        return new SQLiteConnectionProvider(plugin, config.getDatabase());
    }
    
    /**
     * Creates a MySQL ConnectionProvider.
     * 
     * @param config The database configuration
     * @return MySQLConnectionProvider instance
     */
    private ConnectionProvider createMySQLProvider(DatabaseConfig config) {
        return new MySQLConnectionProvider(config, plugin);
    }
    
    /**
     * Creates a ConnectionProvider with custom configuration for testing.
     * 
     * @param type The database type ("sqlite" or "mysql")
     * @param connectionString The connection configuration
     * @return The appropriate ConnectionProvider implementation
     */
    public ConnectionProvider createTestConnectionProvider(String type, String connectionString) {
        DatabaseConfig config;
        
        switch (type.toLowerCase()) {
            case "sqlite":
                config = DatabaseConfig.sqlite(connectionString);
                break;
            case "mysql":
                // Parse MySQL connection string: host:port/database?user=xxx&password=xxx
                String[] parts = connectionString.split("[:/\\?&=]");
                if (parts.length < 6) {
                    throw new IllegalArgumentException("Invalid MySQL connection string format");
                }
                
                String host = parts[0];
                int port = Integer.parseInt(parts[1]);
                String database = parts[2];
                String username = parts[4]; // After user=
                String password = parts[6]; // After password=
                
                config = DatabaseConfig.mysql(host, port, database, username, password);
                break;
            default:
                throw new IllegalArgumentException("Unsupported test database type: " + type);
        }
        
        return createConnectionProvider(config);
    }
}
