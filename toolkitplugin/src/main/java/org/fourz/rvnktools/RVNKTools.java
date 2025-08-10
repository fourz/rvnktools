package org.fourz.rvnktools;

import org.bukkit.Bukkit;
import org.bukkit.event.Listener;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.fourz.rvnktools.command.cycle.CycleCommands;
import org.fourz.rvnktools.command.manager.CommandManager;
import org.fourz.rvnktools.config.ConfigLoader;
import org.fourz.rvnktools.config.Config;
import org.fourz.rvnktools.core.RVNKCoreBootstrap;
import org.fourz.rvnktools.listener.PlayerTrackingListener;
import org.fourz.rvnktools.logfilter.LogFilter;
import org.fourz.rvnktools.listener.JoinListener;
import org.fourz.rvnktools.listener.MickyHatPlaceListener;
import org.fourz.rvnktools.listener.LuckPermsIntegrationListener;
import org.fourz.rvnktools.permission.LuckPermsManager;
import org.fourz.rvnktools.permission.PermissionService;
import org.fourz.rvnktools.api.RVNKToolsAPI;
import org.fourz.rvnktools.util.log.LogManager;

import net.milkbowl.vault.economy.Economy;

import org.fourz.rvnktools.announceManager.AnnounceManager;
import org.fourz.rvnktools.linkMaker.LinkMaker;

import org.fourz.rvnktools.announceManager.AnnounceManager;
import org.fourz.rvnktools.linkMaker.LinkMaker;

public class RVNKTools extends JavaPlugin implements Listener {

    private AnnounceManager announceManager;
    private Economy economy;    
    private CycleCommands commandCycler;
    public LinkMaker linkMaker;
    public PermissionService permissionService;
    private RVNKToolsAPI api;
    private CommandManager commandManager;
    // Add RVNKCore bootstrap component
    private RVNKCoreBootstrap coreBootstrap;
    private LogFilter logFilter;
    private LuckPermsIntegrationListener luckPermsListener;
    private LogManager logger;
    
    @Override
    public void onEnable() {
        this.logger = LogManager.getInstance(this, getClass());
        
        // Initialize configuration first
        initializeConfiguration();
        
        // Initialize RVNKCore next
        initializeRVNKCore();
        
        // Continue with normal initialization
        initializePermissions();
        initializeEconomy();
        initializeLinkMaker();
        initializeAnnounceManager();
        initializeAPI();
        checkPlaceholderAPI();
        registerEventListeners();
        initializeCommandFramework();
        initializeLogFilter();
        logger.info("RVNK Toolkit has been enabled.");
    }

    @Override
    public void onDisable() {
        shutdownAPI();
        shutdownAnnounceManager();
        shutdownLogFilter();
        cleanupResources();
        
        // Shutdown RVNKCore last
        shutdownRVNKCore();
        
        logger.info("RVNK Toolkit has been disabled.");
    }

    /**
     * Initializes configuration using the new unified architecture.
     * Ensures both config.yml (RVNKTools) and config-core.yml (RVNKCore) exist and are properly loaded.
     */
    private void initializeConfiguration() {
        logger.info("Initializing configuration...");
        try {
            // Initialize RVNKTools configuration
            ConfigLoader toolsConfigLoader = new ConfigLoader(this);
            toolsConfigLoader.ensureConfigExists();
            Config toolsConfig = toolsConfigLoader.getConfig();
            
            logger.info("RVNKTools configuration loaded successfully");
            logger.info("RVNKTools log level: " + toolsConfig.getLogLevel().getName());
            
            // Log configuration summary for debugging
            logger.info("Configuration validation complete");
            
        } catch (Exception e) {
            logger.error("Failed to initialize configuration", e);
            throw new RuntimeException("Configuration initialization failed", e);
        }
    }

    /**
     * Initializes the RVNKCore framework with unified configuration.
     */
    private void initializeRVNKCore() {
        logger.info("Initializing RVNKCore components...");
        try {
            // Initialize RVNKCore configuration using singleton pattern
            org.fourz.rvnkcore.config.ConfigLoader coreConfigLoader = org.fourz.rvnkcore.config.ConfigLoader.getInstance(this);
            coreConfigLoader.ensureConfigExists();
            
            logger.info("RVNKCore configuration loaded successfully");
            logger.info("RVNKCore log level: " + coreConfigLoader.getCoreLogLevel().getName());
            
            // Initialize core bootstrap with new configuration architecture
            coreBootstrap = RVNKCoreBootstrap.getInstance(this);
            coreBootstrap.initialize();
            
        } catch (Exception e) {
            logger.error("Failed to initialize RVNKCore components", e);
            logger.warning("Continuing with legacy initialization...");
        }
    }

    /**
     * Shuts down the RVNKCore framework.
     */
    private void shutdownRVNKCore() {
        if (coreBootstrap != null) {
            logger.info("Shutting down RVNKCore components...");
            try {
                coreBootstrap.shutdown();
            } catch (Exception e) {
                logger.error("Error shutting down RVNKCore components", e);
            }
            coreBootstrap = null;
        }
    }
    
    private void initializePermissions() {
        LuckPermsManager.init();
        permissionService = new PermissionService();
    }

    private void initializeEconomy() {
        if (setupEconomy()) {
            getLogger().info("Economy integration enabled.");
        } else {
            getLogger().info("Economy not found. Economy features will be disabled.");
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
        
        //commented out as api is being moved to RVNKCore
        //api.start();
    }

    private void checkPlaceholderAPI() {
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null) {
            getLogger().warning("PlaceholderAPI not found, PlaceholderAPI integration will be unavailable.");
        } else {
            getLogger().info("PlaceholderAPI found, PlaceholderAPI integration enabled.");
        }
    }

    private void registerEventListeners() {
        getServer().getPluginManager().registerEvents(new JoinListener(this), this);
        getServer().getPluginManager().registerEvents(new MickyHatPlaceListener(), this);
        
        // Register RVNKCore listeners if bootstrap is available
        if (coreBootstrap != null && coreBootstrap.isInitialized()) {
            try {
                PlayerTrackingListener playerTracker = new PlayerTrackingListener(this, coreBootstrap);
                getServer().getPluginManager().registerEvents(playerTracker, this);
                
                // Register LuckPerms integration listener
                try {
                    luckPermsListener = new LuckPermsIntegrationListener(coreBootstrap, this);
                    logger.info("LuckPerms integration enabled - permission group changes will be synchronized");
                } catch (IllegalStateException e) {
                    logger.warning("LuckPerms integration disabled: " + e.getMessage());
                }
            } catch (Exception e) {
                logger.error("Failed to register RVNKCore listeners", e);
            }
        }
    }

    private void initializeCommandFramework() {
        commandManager = CommandManager.getInstance(this);
        commandManager.initializeCommands();
        logger.info("Command framework initialization complete");
    }
    
    private void initializeLogFilter() {
        try {
            logFilter = new LogFilter(this);
            logger.info("Log Filter initialized");
        } catch (Exception e) {
            logger.error("Failed to initialize Log Filter", e);
        }
    }

    private void shutdownAPI() {
        if (api != null) api.stop();
        api = null;
    }

    private void shutdownAnnounceManager() {
        if (announceManager != null) {
            announceManager.shutdown();
        }
        announceManager = null;
    }
    
    private void shutdownLogFilter() {
        if (logFilter != null) {
            logFilter.shutdown();
            logFilter = null;
        }
    }

    private void cleanupResources() {
        if (luckPermsListener != null) {
            luckPermsListener.shutdown();
            luckPermsListener = null;
        }
        commandCycler = null;
        linkMaker = null;
    }
    
    /**
     * Gets the RVNKCore bootstrap instance.
     * 
     * @return The RVNKCore bootstrap instance
     */
    public RVNKCoreBootstrap getCoreBootstrap() {
        return coreBootstrap;
    }
    
    /**
     * Gets the LuckPerms integration listener.
     * 
     * @return The LuckPerms integration listener, or null if not available
     */
    public LuckPermsIntegrationListener getLuckPermsListener() {
        return luckPermsListener;
    }
    
    /**
     * Gets the Log Filter instance.
     * 
     * @return The Log Filter instance, or null if not initialized
     */
    public LogFilter getLogFilter() {
        return logFilter;
    }

    public Economy getEconomy() {
        return economy;
    }

    public CycleCommands getCycleCommands() {
        return commandCycler;
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
}