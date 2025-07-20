package org.fourz.rvnktools;

import org.bukkit.Bukkit;
import org.bukkit.event.Listener;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.fourz.rvnktools.command.cycle.CycleCommands;
import org.fourz.rvnktools.command.manager.CommandManager;
import org.fourz.rvnktools.core.RVNKCoreBootstrap;
import org.fourz.rvnktools.listener.PlayerTrackingListener;
import org.fourz.rvnktools.listener.JoinListener;
import org.fourz.rvnktools.listener.MickyHatPlaceListener;
import org.fourz.rvnktools.permission.LuckPermsManager;
import org.fourz.rvnktools.permission.PermissionService;
import org.fourz.rvnktools.api.RVNKToolsAPI;
import org.fourz.rvnktools.util.log.LogManager;

import net.milkbowl.vault.economy.Economy;

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
    private LogManager logger;
    
    @Override
    public void onEnable() {
        this.logger = LogManager.getInstance(this, getClass());
        
        // Initialize RVNKCore first
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
        logger.info("RVNK Toolkit has been enabled.");
    }

    @Override
    public void onDisable() {
        shutdownAPI();
        shutdownAnnounceManager();
        cleanupResources();
        
        // Shutdown RVNKCore last
        shutdownRVNKCore();
        
        logger.info("RVNK Toolkit has been disabled.");
    }

    /**
     * Initializes the RVNKCore framework.
     * 
     * TODO: This will eventually become the primary initialization method,
     * with other services migrated to use RVNKCore.
     */
    private void initializeRVNKCore() {
        logger.info("Initializing RVNKCore components...");
        try {
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
        api.start();
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
            } catch (Exception e) {
                logger.error("Failed to register PlayerTrackingListener", e);
            }
        }
    }

    private void initializeCommandFramework() {
        commandManager = CommandManager.getInstance(this);
        commandManager.initializeCommands();
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

    private void cleanupResources() {
        commandCycler = null;
        linkMaker = null;
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