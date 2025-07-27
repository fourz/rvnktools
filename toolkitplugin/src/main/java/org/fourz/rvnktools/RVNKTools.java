package org.fourz.rvnktools;

import org.bukkit.Bukkit;
import org.bukkit.event.Listener;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.fourz.rvnktools.command.cycle.CycleCommands;
import org.fourz.rvnktools.command.manager.CommandManager;
import org.fourz.rvnktools.core.RVNKCoreBootstrap;
import org.fourz.rvnktools.dhlogfilter.DHLogFilterManager;
import org.fourz.rvnktools.listener.PlayerTrackingListener;
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
    private LuckPermsIntegrationListener luckPermsListener;
    private LogManager logger;
    private DHLogFilterManager dhLogFilterManager;
    
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
        initializeDHLogFilter();
        logger.info("RVNK Toolkit has been enabled.");
    }

    @Override
    public void onDisable() {
        shutdownDHLogFilter();
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
    }

    private void initializeDHLogFilter() {
        logger.info("Initializing DH log filter system...");
        try {
            dhLogFilterManager = new DHLogFilterManager(this);
            dhLogFilterManager.initialize();
        } catch (Exception e) {
            logger.error("Failed to initialize DH log filter system", e);
            logger.warning("DH log filtering will be unavailable");
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

    private void shutdownDHLogFilter() {
        if (dhLogFilterManager != null) {
            logger.info("Shutting down DH log filter system...");
            try {
                dhLogFilterManager.shutdown();
            } catch (Exception e) {
                logger.error("Error shutting down DH log filter system", e);
            }
            dhLogFilterManager = null;
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
     * Gets the DH log filter manager.
     * 
     * @return The DH log filter manager, or null if not available
     */
    public DHLogFilterManager getDHLogFilterManager() {
        return dhLogFilterManager;
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