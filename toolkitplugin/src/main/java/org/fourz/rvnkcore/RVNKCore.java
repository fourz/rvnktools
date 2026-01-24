package org.fourz.rvnkcore;

import org.bukkit.Bukkit;
import org.bukkit.event.Listener;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.fourz.rvnkcore.api.config.ApiConfig;
import org.fourz.rvnkcore.api.server.jetty.CoreServer;
import org.fourz.rvnkcore.api.service.AnnouncementService;
import org.fourz.rvnkcore.api.service.PlayerService;
import org.fourz.rvnkcore.api.service.PlayerWorldService;
import org.fourz.rvnkcore.api.service.WorldService;
import org.fourz.rvnkcore.api.event.PlayerTrackingListener;
import org.fourz.rvnkcore.api.event.WorldTrackingListener;
import org.fourz.rvnkcore.config.ConfigLoader;
import org.fourz.rvnkcore.database.connection.ConnectionProvider;
import org.fourz.rvnkcore.database.connection.ConnectionProviderFactory;
import org.fourz.rvnkcore.database.query.BasicSQLQueryBuilder;
import org.fourz.rvnkcore.database.repository.AnnouncementRepository;
import org.fourz.rvnkcore.database.repository.PlayerRepository;
import org.fourz.rvnkcore.database.repository.PlayerWorldDataRepository;
import org.fourz.rvnkcore.database.repository.DefaultWorldRepository;
import org.fourz.rvnkcore.database.schema.DatabaseSetup;
import org.fourz.rvnkcore.service.announcement.DefaultAnnouncementService;
import org.fourz.rvnkcore.service.player.DefaultPlayerService;
import org.fourz.rvnkcore.service.player.DefaultPlayerWorldService;
import org.fourz.rvnkcore.service.world.DefaultWorldService;
import org.fourz.rvnkcore.service.registry.DefaultServiceRegistry;
import org.fourz.rvnkcore.service.registry.ServiceRegistry;
import org.fourz.rvnkcore.util.log.LogManager;

// RVNKTools imports for bundled functionality
import org.fourz.rvnktools.command.cycle.CycleCommands;
import org.fourz.rvnktools.command.manager.CommandManager;
import org.fourz.rvnktools.logfilter.LogFilter;
import org.fourz.rvnktools.listener.JoinListener;
import org.fourz.rvnktools.listener.MickyHatPlaceListener;
import org.fourz.rvnktools.listener.LuckPermsIntegrationListener;
import org.fourz.rvnktools.permission.LuckPermsManager;
import org.fourz.rvnktools.permission.PermissionService;
import org.fourz.rvnktools.api.RVNKToolsAPI;
import org.fourz.rvnktools.announceManager.AnnounceManager;
import org.fourz.rvnktools.linkMaker.LinkMaker;

import net.milkbowl.vault.economy.Economy;

/**
 * RVNKCore - Core unified library for Ravenkraft Minecraft plugins.
 *
 * This is the main plugin entry point that provides:
 * - ServiceRegistry for cross-plugin service access
 * - Database abstraction (MySQL/SQLite)
 * - REST API server
 * - Bundled RVNKTools functionality
 *
 * Other plugins depend on RVNKCore and access services via:
 * <pre>
 * RVNKCore core = RVNKCore.getInstance();
 * PlayerService playerService = core.getService(PlayerService.class);
 * </pre>
 *
 * @since 1.3.0-alpha (restructured from RVNKTools)
 */
public class RVNKCore extends JavaPlugin implements Listener {

    private static RVNKCore instance;

    // Core components
    private LogManager logger;
    private ServiceRegistry serviceRegistry;
    private ConfigLoader coreConfigLoader;
    private ConnectionProvider connectionProvider;
    private CoreServer apiServer;
    private boolean coreInitialized = false;

    // Bundled RVNKTools components
    private AnnounceManager announceManager;
    private Economy economy;
    private CycleCommands commandCycler;
    private LinkMaker linkMaker;
    private PermissionService permissionService;
    private RVNKToolsAPI api;
    private CommandManager commandManager;
    private LogFilter logFilter;
    private LuckPermsIntegrationListener luckPermsListener;

    @Override
    public void onEnable() {
        instance = this;
        this.logger = LogManager.getInstance(this, getClass());

        logger.info("=== RVNKCore Starting ===");

        // Phase 1: Initialize Core Framework
        initializeCoreFramework();

        // Phase 2: Initialize Bundled RVNKTools Components
        initializeBundledComponents();

        logger.info("=== RVNKCore Enabled ===");
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
            this.coreConfigLoader = org.fourz.rvnkcore.config.ConfigLoader.getInstance(this);
            coreConfigLoader.ensureConfigExists();

            // Setup database
            setupDatabase();

            // Register core services
            registerCoreServices();

            // Start API server
            startApiServer();

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

    private void registerCoreServices() {
        registerPlayerService();
        registerPlayerWorldService();
        registerWorldService();
        registerAnnouncementService();
    }

    private void registerPlayerService() {
        try {
            BasicSQLQueryBuilder queryBuilder = new BasicSQLQueryBuilder();
            PlayerRepository playerRepository = new PlayerRepository(connectionProvider, queryBuilder, this);
            DefaultPlayerService playerService = new DefaultPlayerService(playerRepository, this);

            serviceRegistry.registerService(PlayerService.class, playerService);
            logger.info("PlayerService registered");
        } catch (Exception e) {
            logger.error("Failed to register PlayerService", e);
            throw new RuntimeException("PlayerService registration failed", e);
        }
    }

    private void registerPlayerWorldService() {
        try {
            BasicSQLQueryBuilder queryBuilder = new BasicSQLQueryBuilder();
            PlayerRepository playerRepository = new PlayerRepository(connectionProvider, queryBuilder, this);
            PlayerWorldDataRepository worldDataRepository = new PlayerWorldDataRepository(connectionProvider, queryBuilder, this);
            DefaultPlayerWorldService playerWorldService = new DefaultPlayerWorldService(playerRepository, worldDataRepository, this);

            serviceRegistry.registerService(PlayerWorldService.class, playerWorldService);
            logger.info("PlayerWorldService registered");
        } catch (Exception e) {
            logger.error("Failed to register PlayerWorldService", e);
            throw new RuntimeException("PlayerWorldService registration failed", e);
        }
    }

    private void registerWorldService() {
        try {
            DefaultWorldRepository worldRepository = new DefaultWorldRepository(connectionProvider, this);
            DefaultWorldService worldService = new DefaultWorldService(worldRepository, this);

            serviceRegistry.registerService(WorldService.class, worldService);
            logger.info("WorldService registered");
        } catch (Exception e) {
            logger.error("Failed to register WorldService", e);
            throw new RuntimeException("WorldService registration failed", e);
        }
    }

    private void registerAnnouncementService() {
        try {
            BasicSQLQueryBuilder queryBuilder = new BasicSQLQueryBuilder();
            AnnouncementRepository announcementRepository = new AnnouncementRepository(connectionProvider, queryBuilder, this);
            LogManager announcementLogger = LogManager.getInstance(this, DefaultAnnouncementService.class);
            DefaultAnnouncementService announcementService = new DefaultAnnouncementService(announcementRepository, announcementLogger);

            serviceRegistry.registerService(AnnouncementService.class, announcementService);
            logger.info("AnnouncementService registered");
        } catch (Exception e) {
            logger.error("Failed to register AnnouncementService", e);
            throw new RuntimeException("AnnouncementService registration failed", e);
        }
    }

    private void startApiServer() {
        try {
            ApiConfig apiConfig = coreConfigLoader.getApiConfig();

            if (apiConfig.isEnabled()) {
                PlayerService playerService = serviceRegistry.getService(PlayerService.class);
                PlayerWorldService playerWorldService = serviceRegistry.getService(PlayerWorldService.class);
                AnnouncementService announcementService = serviceRegistry.getService(AnnouncementService.class);
                WorldService worldService = serviceRegistry.getService(WorldService.class);
                apiServer = new CoreServer(apiConfig, playerService, playerWorldService, announcementService, worldService, this);
                apiServer.start();
                logger.info("REST API server started");
            } else {
                logger.info("REST API is disabled in configuration");
            }
        } catch (Exception e) {
            logger.error("Failed to start REST API server", e);
        }
    }

    private void shutdownCoreFramework() {
        if (!coreInitialized) {
            return;
        }

        logger.info("Shutting down core framework...");

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
        logger.info("Initializing bundled RVNKTools components...");

        initializeToolsConfiguration();
        initializePermissions();
        initializeEconomy();
        initializeLinkMaker();
        initializeAnnounceManager();
        initializeAPI();
        checkPlaceholderAPI();
        registerEventListeners();
        initializeCommandFramework();
        initializeLogFilter();

        // Register bundled component commands after CommandManager initialization
        registerBundledComponentCommands();

        logger.info("Bundled components initialization complete");
    }

    private void initializeToolsConfiguration() {
        try {
            org.fourz.rvnktools.config.ConfigLoader toolsConfigLoader = new org.fourz.rvnktools.config.ConfigLoader(this);
            toolsConfigLoader.ensureConfigExists();
            org.fourz.rvnktools.config.Config toolsConfig = toolsConfigLoader.getConfig();
            logger.info("RVNKTools configuration loaded (log level: " + toolsConfig.getLogLevel().getName() + ")");
        } catch (Exception e) {
            logger.error("Failed to initialize tools configuration", e);
        }
    }

    private void initializePermissions() {
        LuckPermsManager.init();
        permissionService = new PermissionService();
    }

    private void initializeEconomy() {
        if (setupEconomy()) {
            logger.info("Economy integration enabled");
        } else {
            logger.info("Economy not found - economy features disabled");
        }
    }

    private void initializeLinkMaker() {
        linkMaker = new LinkMaker(this);
    }

    private void initializeAnnounceManager() {
        announceManager = new AnnounceManager(this);
    }

    private void initializeAPI() {
        api = new RVNKToolsAPI(this, announceManager);
        // API is handled by CoreServer now
    }

    private void checkPlaceholderAPI() {
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null) {
            logger.warning("PlaceholderAPI not found - integration unavailable");
        } else {
            logger.info("PlaceholderAPI integration enabled");
        }
    }

    private void registerEventListeners() {
        getServer().getPluginManager().registerEvents(new JoinListener(this), this);
        getServer().getPluginManager().registerEvents(new MickyHatPlaceListener(), this);

        // Register core tracking listeners
        if (coreInitialized) {
            try {
                PlayerTrackingListener playerTracker = new PlayerTrackingListener(this, this);
                getServer().getPluginManager().registerEvents(playerTracker, this);

                WorldTrackingListener worldTracker = new WorldTrackingListener(this, this);
                getServer().getPluginManager().registerEvents(worldTracker, this);
                worldTracker.syncAllLoadedWorlds();

                // Register LuckPerms integration
                try {
                    luckPermsListener = new LuckPermsIntegrationListener(this, this);
                    logger.info("LuckPerms integration enabled");
                } catch (IllegalStateException e) {
                    logger.warning("LuckPerms integration disabled: " + e.getMessage());
                }
            } catch (Exception e) {
                logger.error("Failed to register tracking listeners", e);
            }
        }
    }

    private void initializeCommandFramework() {
        commandManager = CommandManager.getInstance(this);
        commandManager.initializeCommands();
        logger.info("Command framework initialized");
    }

    private void initializeLogFilter() {
        try {
            logFilter = new LogFilter(this);
            logger.info("Log Filter initialized");
        } catch (Exception e) {
            logger.error("Failed to initialize Log Filter", e);
        }
    }

    private void registerBundledComponentCommands() {
        try {
            if (announceManager != null) {
                announceManager.registerCommands();
            }
        } catch (Exception e) {
            logger.error("Failed to register bundled component commands", e);
        }
    }

    private void shutdownBundledComponents() {
        logger.info("Shutting down bundled components...");

        if (api != null) {
            api.stop();
            api = null;
        }

        if (announceManager != null) {
            announceManager.shutdown();
            announceManager = null;
        }

        if (logFilter != null) {
            logFilter.shutdown();
            logFilter = null;
        }

        if (luckPermsListener != null) {
            luckPermsListener.shutdown();
            luckPermsListener = null;
        }

        commandCycler = null;
        linkMaker = null;

        logger.info("Bundled components shutdown complete");
    }

    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }
        economy = rsp.getProvider();
        return economy != null;
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
    // BUNDLED COMPONENT ACCESSORS
    // ============================================================

    public Economy getEconomy() {
        return economy;
    }

    public CycleCommands getCycleCommands() {
        return commandCycler;
    }

    public AnnounceManager getAnnounceManager() {
        return announceManager;
    }

    public LinkMaker getLinkMaker() {
        return linkMaker;
    }

    public PermissionService getPermissionService() {
        return permissionService;
    }

    public LuckPermsIntegrationListener getLuckPermsListener() {
        return luckPermsListener;
    }

    public LogFilter getLogFilter() {
        return logFilter;
    }

    /**
     * @deprecated Use RVNKCore.getInstance() directly. This method exists for backward compatibility.
     * @return this instance
     */
    @Deprecated
    public RVNKCore getRVNKCore() {
        return this;
    }
}
