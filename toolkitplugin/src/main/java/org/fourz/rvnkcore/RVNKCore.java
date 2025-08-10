package org.fourz.rvnkcore;

import org.bukkit.plugin.java.JavaPlugin;
import org.fourz.rvnkcore.api.config.ApiConfig;
import org.fourz.rvnkcore.api.server.jetty.CoreServer;
import org.fourz.rvnkcore.api.service.PlayerService;
import org.fourz.rvnkcore.api.service.PlayerWorldService;
import org.fourz.rvnkcore.config.ConfigLoader;
import org.fourz.rvnkcore.database.connection.ConnectionProvider;
import org.fourz.rvnkcore.database.connection.ConnectionProviderFactory;
import org.fourz.rvnkcore.database.query.BasicSQLQueryBuilder;
import org.fourz.rvnkcore.database.repository.PlayerRepository;
import org.fourz.rvnkcore.database.repository.PlayerWorldDataRepository;
import org.fourz.rvnkcore.database.schema.DatabaseSetup;
import org.fourz.rvnkcore.service.player.DefaultPlayerService;
import org.fourz.rvnkcore.service.player.DefaultPlayerWorldService;
import org.fourz.rvnkcore.service.registry.DefaultServiceRegistry;
import org.fourz.rvnkcore.service.registry.ServiceRegistry;
import org.fourz.rvnktools.util.log.LogManager;

/**
 * RVNKCore - Centralized Data and Service Layer
 * 
 * Acts as a separate plugin object within RVNKTools, providing clean separation
 * and public API methods that will be used when plugins are truly separate.
 * 
 * This class manages its own lifecycle, services, and provides public methods
 * for cross-plugin communication without requiring bootstrap patterns.
 */
public class RVNKCore {
    private static RVNKCore instance;
    private final JavaPlugin parentPlugin;
    private final LogManager logger;
    private final ServiceRegistry serviceRegistry;
    private final ConfigLoader configLoader;
    private ConnectionProvider connectionProvider;
    private CoreServer apiServer;
    private boolean initialized = false;
    
    /**
     * Creates a new RVNKCore instance.
     * 
     * @param plugin The plugin instance that owns this RVNKCore
     */
    public RVNKCore(JavaPlugin plugin) {
        this.parentPlugin = plugin;
        this.serviceRegistry = new DefaultServiceRegistry(plugin);
        this.configLoader = ConfigLoader.getInstance(plugin);
        this.logger = LogManager.getInstance(plugin, getClass());
        instance = this;
    }
    
    /**
     * Gets the singleton instance of RVNKCore.
     * 
     * @return The RVNKCore instance, or null if not initialized
     */
    public static RVNKCore getInstance() {
        return instance;
    }
    
    /**
     * Initializes RVNKCore and all its services.
     * This should be called during the plugin's onEnable phase.
     */
    public void initialize() {
        if (initialized) {
            logger.warning("RVNKCore already initialized, skipping");
            return;
        }
        
        logger.info("Initializing RVNKCore...");
        
        try {
            // Phase 1: Configuration and Database Setup
            configLoader.ensureConfigExists();
            setupDatabase();
            
            // Phase 2: Register Core Services
            registerPlayerService();
            registerPlayerWorldService();
            
            // Phase 3: Start API Server
            startApiServer();
            
            initialized = true;
            logger.info("RVNKCore initialization complete");
        } catch (Exception e) {
            logger.error("Failed to initialize RVNKCore", e);
            throw new RuntimeException("RVNKCore initialization failed", e);
        }
    }
    
    /**
     * Sets up the database connection and schema.
     */
    private void setupDatabase() {
        try {
            ConnectionProviderFactory factory = new ConnectionProviderFactory(parentPlugin);
            connectionProvider = factory.createConnectionProvider();
            
            DatabaseSetup databaseSetup = new DatabaseSetup(connectionProvider, parentPlugin);
            databaseSetup.initializeDatabase();
            
            logger.info("Database setup completed using " + connectionProvider.getClass().getSimpleName());
        } catch (Exception e) {
            logger.error("Failed to setup database", e);
            throw new RuntimeException("Database setup failed", e);
        }
    }
    
    /**
     * Registers the PlayerService with the service registry.
     */
    private void registerPlayerService() {
        try {
            BasicSQLQueryBuilder queryBuilder = new BasicSQLQueryBuilder();
            PlayerRepository playerRepository = new PlayerRepository(connectionProvider, queryBuilder, parentPlugin);
            DefaultPlayerService playerService = new DefaultPlayerService(playerRepository, parentPlugin);
            
            serviceRegistry.registerService(PlayerService.class, playerService);
            logger.info("PlayerService registered successfully");
        } catch (Exception e) {
            logger.error("Failed to register PlayerService", e);
            throw new RuntimeException("PlayerService registration failed", e);
        }
    }
    
    /**
     * Registers the PlayerWorldService with the service registry.
     */
    private void registerPlayerWorldService() {
        try {
            BasicSQLQueryBuilder queryBuilder = new BasicSQLQueryBuilder();
            PlayerRepository playerRepository = new PlayerRepository(connectionProvider, queryBuilder, parentPlugin);
            PlayerWorldDataRepository worldDataRepository = new PlayerWorldDataRepository(connectionProvider, queryBuilder, parentPlugin);
            DefaultPlayerWorldService playerWorldService = new DefaultPlayerWorldService(playerRepository, worldDataRepository, parentPlugin);
            
            serviceRegistry.registerService(PlayerWorldService.class, playerWorldService);
            logger.info("PlayerWorldService registered successfully");
        } catch (Exception e) {
            logger.error("Failed to register PlayerWorldService", e);
            throw new RuntimeException("PlayerWorldService registration failed", e);
        }
    }
    
    /**
     * Starts the API server if enabled in configuration.
     */
    private void startApiServer() {
        try {
            ApiConfig apiConfig = configLoader.getApiConfig();
            
            if (apiConfig.isEnabled()) {
                PlayerService playerService = serviceRegistry.getService(PlayerService.class);
                apiServer = new CoreServer(apiConfig, playerService, parentPlugin);
                apiServer.start();
                logger.info("RVNKCore REST API server started");
            } else {
                logger.info("RVNKCore REST API is disabled");
            }
        } catch (Exception e) {
            logger.error("Failed to start REST API server", e);
        }
    }
    
    /**
     * Shuts down RVNKCore and cleans up all resources.
     * This should be called during the plugin's onDisable phase.
     */
    public void shutdown() {
        if (!initialized) {
            return;
        }
        
        logger.info("Shutting down RVNKCore...");
        
        try {
            // Stop API server
            if (apiServer != null) {
                apiServer.stop();
                logger.info("REST API server stopped");
            }
            
            // Close database connections
            if (connectionProvider != null) {
                connectionProvider.close();
                logger.info("Database connections closed");
            }
            
            // Shutdown service registry
            if (serviceRegistry instanceof AutoCloseable) {
                ((AutoCloseable) serviceRegistry).close();
                logger.info("Service registry shutdown");
            }
            
            initialized = false;
            logger.info("RVNKCore shutdown complete");
        } catch (Exception e) {
            logger.error("Error during RVNKCore shutdown", e);
        }
    }
    
    // === PUBLIC API METHODS ===
    
    /**
     * Gets a service by its interface class.
     * Public API method for cross-plugin communication.
     * 
     * @param serviceClass The service interface class
     * @return The service instance
     * @throws IllegalArgumentException if service not found
     */
    public <T> T getService(Class<T> serviceClass) {
        if (!initialized) {
            throw new IllegalStateException("RVNKCore not initialized");
        }
        try {
            return serviceRegistry.getService(serviceClass);
        } catch (Exception e) {
            throw new IllegalArgumentException("Service not found: " + serviceClass.getSimpleName(), e);
        }
    }
    
    /**
     * Gets the PlayerService for player data operations.
     * 
     * @return PlayerService instance
     */
    public PlayerService getPlayerService() {
        return getService(PlayerService.class);
    }
    
    /**
     * Gets the PlayerWorldService for per-world player tracking.
     * 
     * @return PlayerWorldService instance
     */
    public PlayerWorldService getPlayerWorldService() {
        return getService(PlayerWorldService.class);
    }
    
    /**
     * Checks if a service is available.
     * 
     * @param serviceClass The service interface class
     * @return true if service is available
     */
    public <T> boolean hasService(Class<T> serviceClass) {
        return initialized && serviceRegistry.hasService(serviceClass);
    }
    
    /**
     * Gets the plugin instance that owns this RVNKCore.
     * 
     * @return The plugin instance
     */
    public JavaPlugin getPlugin() {
        return parentPlugin;
    }
    
    /**
     * Checks if RVNKCore is initialized and ready for use.
     * 
     * @return true if initialized, false otherwise
     */
    public boolean isInitialized() {
        return initialized;
    }
}
