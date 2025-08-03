package org.fourz.rvnktools.core;

import org.fourz.rvnkcore.service.registry.ServiceRegistry;
import org.fourz.rvnkcore.service.registry.DefaultServiceRegistry;
import org.fourz.rvnkcore.api.exception.ServiceException;
import org.fourz.rvnkcore.api.service.PlayerService;
import org.fourz.rvnkcore.api.service.PlayerWorldService;
import org.fourz.rvnkcore.api.config.ApiConfig;
import org.fourz.rvnkcore.api.server.jetty.CoreServer;
import org.fourz.rvnkcore.database.connection.ConnectionProvider;
import org.fourz.rvnkcore.database.connection.ConnectionProviderFactory;
import org.fourz.rvnkcore.database.query.BasicSQLQueryBuilder;
import org.fourz.rvnkcore.database.repository.PlayerRepository;
import org.fourz.rvnkcore.database.repository.PlayerWorldDataRepository;
import org.fourz.rvnkcore.database.schema.DatabaseSetup;
import org.fourz.rvnkcore.service.player.DefaultPlayerService;
import org.fourz.rvnkcore.service.player.DefaultPlayerWorldService;
import org.fourz.rvnktools.RVNKTools;
import org.fourz.rvnktools.util.log.LogManager;

/**
 * RVNKCore Integration Bootstrap
 * 
 * This class serves as a temporary bridge between RVNKTools and the RVNKCore framework
 * during the architectural transition period (Q3 2025 - Q1 2026).
 * 
 * MIGRATION STATUS:
 * - Current Phase: Foundation Implementation (RVNKCore embedded in RVNKTools)
 * - Target Phase: Separated Plugin Architecture (Q1 2026)
 * 
 * RESPONSIBILITIES:
 * 1. Initialize RVNKCore services within RVNKTools context
 * 2. Provide service discovery and dependency injection
 * 3. Manage database connections and schema initialization
 * 4. Bridge legacy RVNKTools features with new RVNKCore APIs
 * 5. Handle graceful shutdown and resource cleanup
 * 
 * FUTURE MIGRATION:
 * When RVNKCore becomes a separate plugin, this bootstrap class will be replaced by:
 * - Direct RVNKCore plugin dependency in plugin.yml
 * - Service discovery through Bukkit's ServiceManager
 * - Event-driven integration instead of direct method calls
 * 
 * @deprecated This class will be removed when RVNKCore becomes a separate plugin (Q1 2026)
 * @see org.fourz.rvnkcore.service.registry.ServiceRegistry
 * @see org.fourz.rvnkcore.api.server.jetty.CoreServer
 */
public class RVNKCoreBootstrap {
    private final RVNKTools plugin;
    private final LogManager logger;
    private ServiceRegistry serviceRegistry;
    private CoreServer apiServer;
    private static RVNKCoreBootstrap instance;
    
    private RVNKCoreBootstrap(RVNKTools plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, getClass());
    }
    
    public static RVNKCoreBootstrap getInstance(RVNKTools plugin) {
        if (instance == null) {
            instance = new RVNKCoreBootstrap(plugin);
        }
        return instance;
    }
    
    /**
     * Initializes RVNKCore framework and registers essential services.
     * 
     * INITIALIZATION ORDER:
     * 1. Service Registry - Central dependency injection container
     * 2. Database Setup - Schema creation and connection management
     * 3. Core Services - PlayerService, AnnouncementService (future)
     * 4. Bridge Services - Legacy compatibility adapters
     * 5. REST API Server - External integration endpoints
     */
    public void initialize() {
        logger.info("RVNKCore bootstrap initializing...");
        
        try {
            // Phase 0: Configure logging for clean output
            configureSystemLogging();
            
            // Phase 1: Core infrastructure
            initServiceRegistry();
            setupDatabase();
            
            // Phase 2: Business services
            registerBridgeServices();
            
            // Phase 3: External APIs
            startApiServer();
            
            logger.info("RVNKCore bootstrap completed successfully");
        } catch (Exception e) {
            logger.error("RVNKCore bootstrap initialization failed", e);
            // Re-throw as runtime exception since this is a critical initialization failure
            throw new RuntimeException("RVNKCore bootstrap failed", e);
        }
    }
    
    /**
     * Configures system-wide logging settings for cleaner console output.
     * This must be called early in the initialization process.
     */
    private void configureSystemLogging() {
        // Configure Jetty logging to be quiet (before any Jetty classes are loaded)
        System.setProperty("org.eclipse.jetty.util.log.class", "org.eclipse.jetty.util.log.StdErrLog");
        System.setProperty("org.eclipse.jetty.LEVEL", "WARN");
        System.setProperty("org.eclipse.jetty.server.LEVEL", "WARN");
        System.setProperty("org.eclipse.jetty.server.handler.LEVEL", "WARN");
        System.setProperty("org.eclipse.jetty.util.ssl.LEVEL", "WARN");
        System.setProperty("org.eclipse.jetty.server.AbstractConnector.LEVEL", "WARN");
        System.setProperty("org.eclipse.jetty.server.handler.ContextHandler.LEVEL", "WARN");
        System.setProperty("org.eclipse.jetty.server.Server.LEVEL", "WARN");
        System.setProperty("org.eclipse.jetty.util.ssl.SslContextFactory.LEVEL", "WARN");
    }
    
    private void initServiceRegistry() {
        serviceRegistry = new DefaultServiceRegistry(plugin);
        logger.info("ServiceRegistry initialized");
    }
    
    private void setupDatabase() {
        try {
            // Create connection provider based on configuration
            ConnectionProviderFactory factory = new ConnectionProviderFactory(plugin);
            ConnectionProvider connectionProvider = factory.createConnectionProvider();
            
            DatabaseSetup databaseSetup = new DatabaseSetup(connectionProvider, plugin);
            
            // Perform one-time database initialization
            databaseSetup.initializeDatabase();
            
            logger.info("Database setup completed successfully using " + 
                       connectionProvider.getClass().getSimpleName());
        } catch (Exception e) {
            logger.error("Failed to setup database", e);
            throw new RuntimeException("Database setup failed", e);
        }
    }
    
    /**
     * Registers bridge services to provide compatibility between RVNKTools and RVNKCore.
     * 
     * MIGRATION NOTE: These bridge services will be replaced by direct service access
     * when RVNKCore becomes a separate plugin. The bridge pattern allows gradual migration
     * without breaking existing RVNKTools functionality.
     */
    private void registerBridgeServices() {
        try {
            // Core data services
            registerPlayerService();
            registerPlayerWorldService();
            
            // Legacy compatibility bridges (future implementation)
            registerAnnouncementBridge();
            registerLinkMakerBridge();
            registerPermissionBridge();
            
            logger.info("Bridge services registered successfully");
        } catch (ServiceException e) {
            logger.error("Failed to register bridge services", e);
        }
    }
    
    /**
     * Registers the PlayerService as the primary data access service.
     * This replaces direct database access in legacy code.
     */
    private void registerPlayerService() throws ServiceException {
        try {
            // Create dependencies
            ConnectionProviderFactory factory = new ConnectionProviderFactory(plugin);
            ConnectionProvider connectionProvider = factory.createConnectionProvider();
            
            BasicSQLQueryBuilder queryBuilder = new BasicSQLQueryBuilder();
            
            PlayerRepository playerRepository = 
                new PlayerRepository(connectionProvider, queryBuilder, plugin);
            
            DefaultPlayerService playerService = 
                new DefaultPlayerService(playerRepository, plugin);
            
            // Register the service
            serviceRegistry.registerService(PlayerService.class, playerService);
            
            logger.info("PlayerService registered successfully using " + 
                       connectionProvider.getClass().getSimpleName());
        } catch (Exception e) {
            logger.error("Failed to register PlayerService", e);
            throw new ServiceException("PlayerService registration failed", e);
        }
    }
    
    /**
     * Registers the PlayerWorldService for per-world player data tracking.
     * This enables worldswap teleport functionality and comprehensive location tracking.
     */
    private void registerPlayerWorldService() throws ServiceException {
        try {
            // Retrieve already created dependencies from PlayerService registration
            ConnectionProviderFactory factory = new ConnectionProviderFactory(plugin);
            ConnectionProvider connectionProvider = factory.createConnectionProvider();
            
            BasicSQLQueryBuilder queryBuilder = new BasicSQLQueryBuilder();
            
            // Create repositories
            PlayerRepository playerRepository = 
                new PlayerRepository(connectionProvider, queryBuilder, plugin);
            PlayerWorldDataRepository worldDataRepository = 
                new PlayerWorldDataRepository(connectionProvider, queryBuilder, plugin);
            
            DefaultPlayerWorldService playerWorldService = 
                new DefaultPlayerWorldService(playerRepository, worldDataRepository, plugin);
            
            // Register the service
            serviceRegistry.registerService(PlayerWorldService.class, playerWorldService);
            
            logger.info("PlayerWorldService registered successfully for per-world tracking");
        } catch (Exception e) {
            logger.error("Failed to register PlayerWorldService", e);
            throw new ServiceException("PlayerWorldService registration failed", e);
        }
    }
    
    private void registerAnnouncementBridge() throws ServiceException {
        // TODO: Implement and register the announcement bridge service
    }
    
    private void registerLinkMakerBridge() throws ServiceException {
        // TODO: Implement and register the link maker bridge service
    }
    
    private void registerPermissionBridge() throws ServiceException {
        // TODO: Implement and register the permission bridge service
    }

    /**
     * Initializes and starts the REST API server for external integrations.
     * 
     * MIGRATION NOTE: In the separated plugin architecture, this will become
     * part of the RVNKCore plugin's main initialization, not a bridge service.
     */
    private void startApiServer() {
        try {
            ApiConfig apiConfig = new ApiConfig(plugin);
            if (apiConfig.isEnabled()) {
                // Configure all loggers with the global log level
                configureGlobalLogging(apiConfig);
                
                PlayerService playerService = getService(PlayerService.class);
                apiServer = new CoreServer(apiConfig, playerService, plugin);
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
     * Configures global logging levels for all RVNKCore components.
     * 
     * @param apiConfig The API configuration containing log level settings
     */
    private void configureGlobalLogging(ApiConfig apiConfig) {
        java.util.logging.Level globalLevel = apiConfig.getGlobalLogLevel();
        java.util.logging.Level apiLevel = apiConfig.getApiLogLevel();
        
        boolean isGlobalDebugEnabled = (globalLevel == java.util.logging.Level.FINE);
        boolean isApiDebugEnabled = (apiLevel == java.util.logging.Level.FINE);
        
        // Configure debug logging for core components using global level
        LogManager.getInstance(plugin, org.fourz.rvnkcore.database.repository.BaseRepository.class)
                .setDebugEnabled(isGlobalDebugEnabled);
        LogManager.getInstance(plugin, org.fourz.rvnkcore.database.repository.PlayerRepository.class)
                .setDebugEnabled(isGlobalDebugEnabled);
        LogManager.getInstance(plugin, org.fourz.rvnktools.listener.PlayerTrackingListener.class)
                .setDebugEnabled(isGlobalDebugEnabled);
        
        // Configure debug logging for API components using API-specific level
        LogManager.getInstance(plugin, org.fourz.rvnkcore.api.security.AuthFilter.class)
                .setDebugEnabled(isApiDebugEnabled);
        LogManager.getInstance(plugin, org.fourz.rvnkcore.api.controller.PlayerController.class)
                .setDebugEnabled(isApiDebugEnabled);
        
        String apiLogInfo = apiLevel.equals(globalLevel) ? "inherits global" : apiLevel.toString();
        logger.info("Logging configured - Global Level: " + globalLevel + 
                   ", API Level: " + apiLogInfo + 
                   ", Global Debug: " + isGlobalDebugEnabled + 
                   ", API Debug: " + isApiDebugEnabled);
    }
    
    /**
     * Gracefully shuts down RVNKCore components and cleans up resources.
     * 
     * SHUTDOWN ORDER:
     * 1. REST API Server - Stop accepting new requests
     * 2. Service Registry - Cleanup registered services  
     * 3. Database Connections - Close all connections
     */
    public void shutdown() {
        logger.info("Shutting down RVNKCore bootstrap...");
        
        if (apiServer != null && apiServer.isRunning()) {
            apiServer.stop();
            apiServer = null;
        }
        
        if (serviceRegistry != null) {
            serviceRegistry.shutdown();
            serviceRegistry = null;
        }
        
        logger.info("RVNKCore bootstrap shutdown complete");
        instance = null;
    }
    
    /**
     * Gets the service registry.
     * 
     * @return The service registry instance
     */
    public ServiceRegistry getServiceRegistry() {
        return serviceRegistry;
    }
    
    /**
     * Gets a service from the registry.
     * 
     * @param <T> The service type
     * @param serviceClass The service interface class
     * @return The service instance
     * @throws ServiceException If the service is not found
     */
    public <T> T getService(Class<T> serviceClass) throws ServiceException {
        if (serviceRegistry == null) {
            throw new ServiceException("ServiceRegistry not initialized");
        }
        return serviceRegistry.getService(serviceClass);
    }
    
    /**
     * Checks if a service is available.
     * 
     * @param serviceClass The service interface class
     * @return true if the service is available
     */
    public boolean hasService(Class<?> serviceClass) {
        return serviceRegistry != null && serviceRegistry.hasService(serviceClass);
    }
    
    /**
     * Checks if RVNKCore bootstrap is initialized.
     * 
     * @return true if initialized and ready
     */
    public boolean isInitialized() {
        return serviceRegistry != null;
    }
}