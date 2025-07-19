package org.fourz.rvnktools.core;

import org.fourz.rvnkcore.service.registry.ServiceRegistry;
import org.fourz.rvnkcore.service.registry.ServiceRegistryImpl;
import org.fourz.rvnkcore.api.exception.ServiceException;
import org.fourz.rvnkcore.api.service.IPlayerService;
import org.fourz.rvnkcore.database.connection.SQLiteConnectionProvider;
import org.fourz.rvnkcore.database.query.BasicSQLQueryBuilder;
import org.fourz.rvnkcore.database.repository.PlayerRepository;
import org.fourz.rvnkcore.service.player.PlayerService;
import org.fourz.rvnktools.RVNKTools;
import org.fourz.rvnktools.util.log.LogManager;

/**
 * Bootstrap class for RVNKCore integration.
 * This acts as a bridge between RVNKTools and RVNKCore during the transition period.
 * 
 * TODO: This class will be removed once full migration to RVNKCore is complete.
 */
public class RVNKCoreBootstrap {
    private final RVNKTools plugin;
    private final LogManager logger;
    private ServiceRegistry serviceRegistry;
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
     * Initializes RVNKCore and registers bridge services.
     */
    public void initialize() {
        logger.info("Initializing RVNKCore bootstrap...");
        
        try {
            // Initialize RVNKCore components
            initServiceRegistry();
            registerBridgeServices();
            logger.info("RVNKCore bootstrap completed successfully");
        } catch (Exception e) {
            logger.error("Failed to initialize RVNKCore", e);
        }
    }
    
    private void initServiceRegistry() {
        try {
            serviceRegistry = new ServiceRegistryImpl(plugin);
            logger.info("ServiceRegistry initialized");
        } catch (Exception e) {
            logger.error("Failed to initialize ServiceRegistry", e);
            throw e;
        }
    }
    
    private void registerBridgeServices() {
        try {
            // Register core services
            registerPlayerService();
            registerAnnouncementBridge();
            registerLinkMakerBridge();
            registerPermissionBridge();
            
            logger.info("Bridge services registered successfully");
        } catch (ServiceException e) {
            logger.error("Failed to register bridge services", e);
        }
    }
    
    private void registerPlayerService() throws ServiceException {
        try {
            // Create dependencies
            SQLiteConnectionProvider connectionProvider = 
                new SQLiteConnectionProvider(plugin, "rvnkcore.db");
            
            BasicSQLQueryBuilder queryBuilder = new BasicSQLQueryBuilder();
            
            PlayerRepository playerRepository = 
                new PlayerRepository(connectionProvider, queryBuilder, plugin);
            
            PlayerService playerService = 
                new PlayerService(playerRepository, plugin);
            
            // Register the service
            serviceRegistry.registerService(IPlayerService.class, playerService);
            
            logger.info("PlayerService registered successfully");
        } catch (Exception e) {
            logger.error("Failed to register PlayerService", e);
            throw new ServiceException("PlayerService registration failed", e);
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
     * Shuts down RVNKCore components.
     */
    public void shutdown() {
        logger.info("Shutting down RVNKCore bootstrap...");
        
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