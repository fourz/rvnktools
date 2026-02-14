package org.fourz.rvnkcore.init;

import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.fourz.rvnkcore.RVNKCore;
import org.fourz.rvnkcore.api.event.PlayerTrackingListener;
import org.fourz.rvnkcore.api.event.WorldTrackingListener;
import org.fourz.rvnkcore.command.PlayerPreferencesCommand;
import org.fourz.rvnkcore.service.registry.ServiceRegistry;
import org.fourz.rvnkcore.util.log.LogManager;

import org.fourz.rvnktools.announceManager.AnnounceManager;
import org.fourz.rvnktools.api.RVNKToolsAPI;
import org.fourz.rvnktools.command.manager.CommandManager;
import org.fourz.rvnktools.linkMaker.LinkMaker;
import org.fourz.rvnktools.listener.JoinListener;
import org.fourz.rvnktools.listener.LuckPermsIntegrationListener;
import org.fourz.rvnktools.listener.MickyHatPlaceListener;
import org.fourz.rvnktools.logfilter.LogFilter;
import org.fourz.rvnktools.permission.LuckPermsManager;
import org.fourz.rvnktools.permission.PermissionService;

import net.milkbowl.vault.economy.Economy;

/**
 * Initializer for RVNKTools components and integrations.
 *
 * <p>This class follows the Single Responsibility Principle (SRP) by handling
 * only the initialization, registration, and shutdown of RVNKTools
 * components and their integrations with the core framework.</p>
 *
 * <p>Components managed by this initializer:</p>
 * <ul>
 *   <li>{@link AnnounceManager} - Announcement system</li>
 *   <li>{@link LinkMaker} - Link creation utilities</li>
 *   <li>{@link PermissionService} - Permission management</li>
 *   <li>{@link CommandManager} - Command framework</li>
 *   <li>{@link LogFilter} - Log filtering</li>
 *   <li>{@link Economy} - Vault economy integration (optional)</li>
 * </ul>
 *
 * <p>All components are registered in the ServiceRegistry for dependency injection.</p>
 *
 * @since 1.4.0
 * @see ServiceRegistry
 */
public class RVNKToolsInitializer {

    private final RVNKCore plugin;
    private final ServiceRegistry registry;
    private final LogManager logger;

    // Component references for shutdown
    private RVNKToolsAPI api;
    private LuckPermsIntegrationListener luckPermsListener;

    /**
     * Creates a new RVNKToolsInitializer.
     *
     * @param plugin The RVNKCore plugin instance
     * @param registry The ServiceRegistry for component registration
     */
    public RVNKToolsInitializer(RVNKCore plugin, ServiceRegistry registry) {
        this.plugin = plugin;
        this.registry = registry;
        this.logger = LogManager.getInstance(plugin, getClass());
    }

    /**
     * Initializes all RVNKTools components.
     *
     * <p>Components are initialized in dependency order and registered
     * in the ServiceRegistry for access via {@code getService()}.</p>
     */
    public void initializeAll() {
        long startTime = System.currentTimeMillis();
        logger.debug("Initializing RVNKTools components...");

        initializeToolsConfiguration();
        logger.debug("  + ToolsConfiguration initialized (" + (System.currentTimeMillis() - startTime) + "ms)");

        initializePermissions();
        logger.debug("  + Permissions initialized (" + (System.currentTimeMillis() - startTime) + "ms)");

        initializeEconomy();
        logger.debug("  + Economy initialized (" + (System.currentTimeMillis() - startTime) + "ms)");

        initializeLinkMaker();
        logger.debug("  + LinkMaker initialized (" + (System.currentTimeMillis() - startTime) + "ms)");

        initializeAnnounceManager();
        logger.debug("  + AnnounceManager initialized (" + (System.currentTimeMillis() - startTime) + "ms)");

        initializeAPI();
        logger.debug("  + API initialized (" + (System.currentTimeMillis() - startTime) + "ms)");

        checkPlaceholderAPI();
        logger.debug("  + PlaceholderAPI checked (" + (System.currentTimeMillis() - startTime) + "ms)");

        registerEventListeners();
        logger.debug("  + Event listeners registered (" + (System.currentTimeMillis() - startTime) + "ms)");

        // Initialize LogFilter BEFORE CommandManager to ensure service is available
        initializeLogFilter();
        logger.debug("  + LogFilter initialized (" + (System.currentTimeMillis() - startTime) + "ms)");

        initializeCommandFramework();
        logger.debug("  + CommandManager initialized (" + (System.currentTimeMillis() - startTime) + "ms)");

        registerBundledComponentCommands();
        logger.debug("  + Bundled commands registered (" + (System.currentTimeMillis() - startTime) + "ms)");

        long totalTime = System.currentTimeMillis() - startTime;
        logger.info("RVNKTools components initialized: ToolsConfig, Permissions, Economy, LinkMaker, AnnounceManager, API, Events, LogFilter, CommandManager (" + totalTime + "ms)");
    }

    /**
     * Shuts down all RVNKTools components in reverse initialization order.
     */
    public void shutdownAll() {
        logger.info("Shutting down RVNKTools components...");

        // Shutdown API
        if (api != null) {
            api.stop();
            api = null;
        }

        // Shutdown AnnounceManager
        try {
            AnnounceManager announceManager = registry.getService(AnnounceManager.class);
            if (announceManager != null) {
                announceManager.shutdown();
            }
        } catch (Exception e) {
            // Service may not be registered
        }

        // Shutdown LogFilter
        try {
            LogFilter logFilter = registry.getService(LogFilter.class);
            if (logFilter != null) {
                logFilter.shutdown();
            }
        } catch (Exception e) {
            // Service may not be registered
        }

        // Shutdown LuckPerms listener
        if (luckPermsListener != null) {
            luckPermsListener.shutdown();
            luckPermsListener = null;
        }

        logger.info("RVNKTools components shutdown complete");
    }

    private void initializeToolsConfiguration() {
        try {
            org.fourz.rvnktools.config.ConfigLoader toolsConfigLoader = new org.fourz.rvnktools.config.ConfigLoader(plugin);
            toolsConfigLoader.ensureConfigExists();
            org.fourz.rvnktools.config.Config toolsConfig = toolsConfigLoader.getConfig();
            logger.info("RVNKTools configuration loaded (log level: " + toolsConfig.getLogLevel().getName() + ")");
        } catch (Exception e) {
            logger.error("Failed to initialize tools configuration", e);
        }
    }

    private void initializePermissions() {
        try {
            LuckPermsManager.init();
            PermissionService permissionService = new PermissionService();
            registry.registerService(PermissionService.class, permissionService);
            logger.info("PermissionService registered");
        } catch (Exception e) {
            logger.error("Failed to initialize permissions", e);
        }
    }

    private void initializeEconomy() {
        if (plugin.getServer().getPluginManager().getPlugin("Vault") == null) {
            logger.info("Economy not found - economy features disabled");
            return;
        }

        RegisteredServiceProvider<Economy> rsp = plugin.getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            logger.info("Economy not found - economy features disabled");
            return;
        }

        Economy economy = rsp.getProvider();
        if (economy != null) {
            registry.registerService(Economy.class, economy);
            logger.info("Economy integration enabled");
        } else {
            logger.info("Economy not found - economy features disabled");
        }
    }

    private void initializeLinkMaker() {
        try {
            LinkMaker linkMaker = new LinkMaker(plugin);
            registry.registerService(LinkMaker.class, linkMaker);
            logger.info("LinkMaker registered");
        } catch (Exception e) {
            logger.error("Failed to initialize LinkMaker", e);
        }
    }

    private void initializeAnnounceManager() {
        try {
            AnnounceManager announceManager = new AnnounceManager(plugin);
            registry.registerService(AnnounceManager.class, announceManager);
            logger.info("AnnounceManager registered");
        } catch (Exception e) {
            logger.error("Failed to initialize AnnounceManager", e);
        }
    }

    private void initializeAPI() {
        try {
            AnnounceManager announceManager = registry.getService(AnnounceManager.class);
            api = new RVNKToolsAPI(plugin, announceManager);
            registry.registerService(RVNKToolsAPI.class, api);
            logger.info("RVNKToolsAPI registered");
        } catch (Exception e) {
            logger.error("Failed to initialize API", e);
        }
    }

    private void checkPlaceholderAPI() {
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null) {
            logger.warning("PlaceholderAPI not found - integration unavailable");
        } else {
            logger.info("PlaceholderAPI integration enabled");
        }
    }

    private void registerEventListeners() {
        plugin.getServer().getPluginManager().registerEvents(new JoinListener(plugin), plugin);
        plugin.getServer().getPluginManager().registerEvents(new MickyHatPlaceListener(), plugin);

        // Register core tracking listeners
        if (plugin.isInitialized()) {
            try {
                PlayerTrackingListener playerTracker = new PlayerTrackingListener(plugin, plugin);
                plugin.getServer().getPluginManager().registerEvents(playerTracker, plugin);

                WorldTrackingListener worldTracker = new WorldTrackingListener(plugin, plugin);
                plugin.getServer().getPluginManager().registerEvents(worldTracker, plugin);
                worldTracker.syncAllLoadedWorlds();

                // Register LuckPerms integration
                try {
                    luckPermsListener = new LuckPermsIntegrationListener(plugin, plugin);
                    registry.registerService(LuckPermsIntegrationListener.class, luckPermsListener);
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
        try {
            CommandManager commandManager = CommandManager.getInstance(plugin);
            commandManager.initializeCommands();
            registry.registerService(CommandManager.class, commandManager);
            logger.info("CommandManager registered");
        } catch (Exception e) {
            logger.error("Failed to initialize command framework", e);
        }
    }

    private void initializeLogFilter() {
        try {
            LogFilter logFilter = new LogFilter(plugin);
            registry.registerService(LogFilter.class, logFilter);
            logger.info("LogFilter registered");
        } catch (Exception e) {
            logger.error("Failed to initialize Log Filter", e);
        }
    }

    private void registerBundledComponentCommands() {
        try {
            // Register PlayerPreferencesCommand
            CommandManager commandManager = registry.getService(CommandManager.class);
            if (commandManager != null) {
                PlayerPreferencesCommand prefCommand = new PlayerPreferencesCommand(plugin);
                commandManager.registerCommand(prefCommand);
                logger.info("PlayerPreferencesCommand registered");
            }

            // Register AnnounceManager commands
            AnnounceManager announceManager = registry.getService(AnnounceManager.class);
            if (announceManager != null) {
                announceManager.registerCommands();
            }
        } catch (Exception e) {
            logger.error("Failed to register bundled component commands", e);
        }
    }
}
