package org.fourz.rvnkcore;

import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.fourz.rvnkcore.api.service.AnnouncementService;
import org.fourz.rvnkcore.api.service.PlayerService;
import org.fourz.rvnkcore.api.service.PlayerWorldService;
import org.fourz.rvnkcore.api.service.WorldService;
import org.fourz.rvnkcore.config.ConfigLoader;
import org.fourz.rvnkcore.database.connection.ConnectionProvider;
import org.fourz.rvnkcore.database.connection.ConnectionProviderFactory;
import org.fourz.rvnkcore.database.schema.DatabaseSetup;
import org.fourz.rvnkcore.init.ApiServerInitializer;
import org.fourz.rvnkcore.init.RVNKToolsInitializer;
import org.fourz.rvnkcore.init.CoreServiceFactory;
import org.fourz.rvnkcore.service.registry.DefaultServiceRegistry;
import org.fourz.rvnkcore.service.registry.ServiceRegistry;
import org.fourz.rvnkcore.util.log.LogManager;

// Bundled component imports for accessor compatibility
import org.fourz.rvnktools.announceManager.AnnounceManager;
import org.fourz.rvnktools.command.manager.CommandManager;
import org.fourz.rvnktools.linkMaker.LinkMaker;
import org.fourz.rvnktools.listener.LuckPermsIntegrationListener;
import org.fourz.rvnktools.logfilter.LogFilter;
import org.fourz.rvnktools.permission.PermissionService;

import net.milkbowl.vault.economy.Economy;

/**
 * RVNKCore - Core unified library for Ravenkraft Minecraft plugins.
 *
 * <p>This is the main plugin entry point that coordinates:</p>
 * <ul>
 *   <li>ServiceRegistry for cross-plugin service access</li>
 *   <li>Database abstraction (MySQL/SQLite)</li>
 *   <li>REST API server</li>
 *   <li>Bundled RVNKTools functionality</li>
 * </ul>
 *
 * <p>Other plugins depend on RVNKCore and access services via:</p>
 * <pre>
 * RVNKCore core = RVNKCore.getInstance();
 * PlayerService playerService = core.getService(PlayerService.class);
 * </pre>
 *
 * <p>This class follows SOLID principles by delegating initialization
 * to specialized initializer classes:</p>
 * <ul>
 *   <li>{@link CoreServiceFactory} - Core service construction</li>
 *   <li>{@link RVNKToolsInitializer} - RVNKTools component lifecycle</li>
 *   <li>{@link ApiServerInitializer} - REST API server lifecycle</li>
 * </ul>
 *
 * @since 1.3.0-alpha (restructured from RVNKTools)
 * @since 1.4.0 (SOLID refactor with initializers)
 */
public class RVNKCore extends JavaPlugin implements Listener {

    private static RVNKCore instance;

    // Core components
    private LogManager logger;
    private ServiceRegistry serviceRegistry;
    private ConfigLoader coreConfigLoader;
    private ConnectionProvider connectionProvider;
    private boolean coreInitialized = false;

    // Initializers (SOLID: delegated responsibilities)
    private CoreServiceFactory coreServiceFactory;
    private RVNKToolsInitializer toolsInitializer;
    private ApiServerInitializer apiInitializer;

    @Override
    public void onEnable() {
        instance = this;
        this.logger = LogManager.getInstance(this, getClass());

        // TEMP: Total startup timer for performance debugging
        long totalStartTime = System.currentTimeMillis();
        logger.info("=== RVNKCore Starting ===");

        // Phase 1: Initialize Core Framework
        logger.info("Phase 1: Initializing Core Framework...");
        long phase1Start = System.currentTimeMillis();
        initializeCoreFramework();
        logger.info("Phase 1 complete (" + (System.currentTimeMillis() - phase1Start) + "ms)");

        // Phase 2: Initialize Bundled RVNKTools Components
        logger.info("Phase 2: Initializing Bundled Components...");
        long phase2Start = System.currentTimeMillis();
        initializeBundledComponents();
        logger.info("Phase 2 complete (" + (System.currentTimeMillis() - phase2Start) + "ms)");

        logger.info("=== RVNKCore Enabled === (Total startup: " + (System.currentTimeMillis() - totalStartTime) + "ms)");
    }

    @Override
    public void onDisable() {
        logger.info("=== RVNKCore Shutting Down ===");

        // Shutdown bundled components first
        shutdownBundledComponents();

        // Shutdown core framework last
        shutdownCoreFramework();

        instance = null;
        logger.info("=== RVNKCore Disabled ===");
    }

    // ============================================================
    // CORE FRAMEWORK INITIALIZATION
    // ============================================================

    private void initializeCoreFramework() {
        logger.info("Initializing core framework...");

        try {
            // Initialize service registry
            this.serviceRegistry = new DefaultServiceRegistry(this);

            // Initialize configuration
            this.coreConfigLoader = ConfigLoader.getInstance(this);
            coreConfigLoader.ensureConfigExists();

            // Setup database
            setupDatabase();

            // Register core services via factory
            coreServiceFactory = new CoreServiceFactory(connectionProvider, this);
            coreServiceFactory.registerAllServices(serviceRegistry);

            // Start API server via initializer
            apiInitializer = new ApiServerInitializer(serviceRegistry, coreConfigLoader, this);
            apiInitializer.start();

            coreInitialized = true;
            logger.info("Core framework initialization complete");

        } catch (Exception e) {
            logger.error("Failed to initialize core framework", e);
            throw new RuntimeException("RVNKCore initialization failed", e);
        }
    }

    private void setupDatabase() {
        try {
            ConnectionProviderFactory factory = new ConnectionProviderFactory(this);
            connectionProvider = factory.createConnectionProvider();

            DatabaseSetup databaseSetup = new DatabaseSetup(connectionProvider, this);
            databaseSetup.initializeDatabase();

            logger.info("Database setup completed using " + connectionProvider.getClass().getSimpleName());
        } catch (Exception e) {
            logger.error("Failed to setup database", e);
            throw new RuntimeException("Database setup failed", e);
        }
    }

    private void shutdownCoreFramework() {
        if (!coreInitialized) {
            return;
        }

        logger.info("Shutting down core framework...");

        try {
            // Stop API server
            if (apiInitializer != null) {
                apiInitializer.stop();
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

            coreInitialized = false;
            logger.info("Core framework shutdown complete");
        } catch (Exception e) {
            logger.error("Error during core framework shutdown", e);
        }
    }

    // ============================================================
    // BUNDLED RVNKTOOLS COMPONENTS
    // ============================================================

    private void initializeBundledComponents() {
        toolsInitializer = new RVNKToolsInitializer(this, serviceRegistry);
        toolsInitializer.initializeAll();
    }

    private void shutdownBundledComponents() {
        if (toolsInitializer != null) {
            toolsInitializer.shutdownAll();
        }
    }

    // ============================================================
    // PUBLIC API - Cross-Plugin Access
    // ============================================================

    /**
     * Gets the singleton instance of RVNKCore.
     *
     * @return The RVNKCore instance, or null if not loaded
     */
    public static RVNKCore getInstance() {
        return instance;
    }

    /**
     * Gets a service by its interface class.
     *
     * @param serviceClass The service interface class
     * @return The service instance
     * @throws IllegalArgumentException if service not found
     * @throws IllegalStateException if core not initialized
     */
    public <T> T getService(Class<T> serviceClass) {
        if (!coreInitialized) {
            throw new IllegalStateException("RVNKCore not initialized");
        }
        try {
            return serviceRegistry.getService(serviceClass);
        } catch (Exception e) {
            throw new IllegalArgumentException("Service not found: " + serviceClass.getSimpleName(), e);
        }
    }

    /**
     * Gets the ServiceRegistry for direct service access.
     *
     * @return The ServiceRegistry instance
     */
    public ServiceRegistry getServiceRegistry() {
        return serviceRegistry;
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
     * Gets the WorldService for world tracking and management.
     *
     * @return WorldService instance
     */
    public WorldService getWorldService() {
        return getService(WorldService.class);
    }

    /**
     * Gets the AnnouncementService for announcement management.
     *
     * @return AnnouncementService instance
     */
    public AnnouncementService getAnnouncementService() {
        return getService(AnnouncementService.class);
    }

    /**
     * Checks if a service is available.
     *
     * @param serviceClass The service interface class
     * @return true if service is available
     */
    public <T> boolean hasService(Class<T> serviceClass) {
        return coreInitialized && serviceRegistry.hasService(serviceClass);
    }

    /**
     * Checks if RVNKCore is initialized and ready for use.
     *
     * @return true if initialized, false otherwise
     */
    public boolean isInitialized() {
        return coreInitialized;
    }

    // ============================================================
    // BUNDLED COMPONENT ACCESSORS (delegate to ServiceRegistry)
    // ============================================================

    /**
     * Gets the Economy service if Vault is available.
     *
     * @return Economy instance, or null if not available
     */
    public Economy getEconomy() {
        try {
            return getService(Economy.class);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Gets the AnnounceManager for announcement operations.
     *
     * @return AnnounceManager instance
     */
    public AnnounceManager getAnnounceManager() {
        return getService(AnnounceManager.class);
    }

    /**
     * Gets the LinkMaker for link creation operations.
     *
     * @return LinkMaker instance
     */
    public LinkMaker getLinkMaker() {
        return getService(LinkMaker.class);
    }

    /**
     * Gets the PermissionService for permission operations.
     *
     * @return PermissionService instance
     */
    public PermissionService getPermissionService() {
        return getService(PermissionService.class);
    }

    /**
     * Gets the LuckPerms integration listener.
     *
     * @return LuckPermsIntegrationListener instance, or null if not available
     */
    public LuckPermsIntegrationListener getLuckPermsListener() {
        try {
            return getService(LuckPermsIntegrationListener.class);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Gets the LogFilter for log filtering operations.
     *
     * @return LogFilter instance
     */
    public LogFilter getLogFilter() {
        return getService(LogFilter.class);
    }

}
