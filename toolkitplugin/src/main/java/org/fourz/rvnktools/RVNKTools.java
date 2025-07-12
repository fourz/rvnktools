package org.fourz.rvnktools;

import org.bukkit.Bukkit;
import org.bukkit.event.Listener;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.fourz.rvnktools.command.*;
import org.fourz.rvnktools.command.cycle.CycleCommands;
import org.fourz.rvnktools.command.framework.CommandManager;
import org.fourz.rvnktools.listener.JoinListener;
import org.fourz.rvnktools.listener.MickyHatPlaceListener;
import org.fourz.rvnktools.permission.LuckPermsManager;
import org.fourz.rvnktools.permission.PermissionService;
import org.fourz.rvnktools.api.RVNKToolsAPI;

import net.milkbowl.vault.economy.Economy;

import org.fourz.rvnktools.announceManager.AnnounceManager;
import org.fourz.rvnktools.command.BroadcastCommand;
import org.fourz.rvnktools.linkMaker.LinkMaker;
import org.fourz.rvnktools.hatManager.PutHatCommand;

public class RVNKTools extends JavaPlugin implements Listener {

    private AnnounceManager announceManager;
    private Economy economy;    
    private CycleCommands cycleCommands;
    public LinkMaker linkMaker;
    public PermissionService permissionService;
    private int gcTaskId = -1;
    private RVNKToolsAPI api;
    private CommandManager commandManager;

    @Override
    public void onEnable() {

        // Initialize LuckPermsManager and PermissionService used for permission integration
        LuckPermsManager.init();
        permissionService = new PermissionService();
        
        // Initialize Economy if available        
        if (setupEconomy()) {  
            getLogger().info("Economy integration enabled.");
        } else {
            getLogger().info("Economy not found. Economy features will be disabled.");   
        }

        // Initialize LinkMaker
        linkMaker = new LinkMaker(this);        
        
        // Initialize AnnounceManager
        announceManager = new AnnounceManager(this);

        // Initialize RVNKToolsAPI (AnnounceManager, ...)
        api = new RVNKToolsAPI(this, announceManager);
        api.start();

        // Register PlaceholderAPI integration or flag as unavailable
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null) {
            getLogger().warning("PlaceholderAPI not found, PlaceholderAPI integration will be unavailable.");
        } else {
            getLogger().info("PlaceholderAPI found, PlaceholderAPI integration enabled.");
        } 

        // Register Events
        getServer().getPluginManager().registerEvents(new JoinListener(this), this);
        getServer().getPluginManager().registerEvents(new MickyHatPlaceListener(), this);

        // Initialize command framework
        commandManager = CommandManager.getInstance(this);
        commandManager.initializeCommands();  // This handles all framework commands

        // Initialize and register CycleCommands (will be migrated to framework later)
        cycleCommands = new CycleCommands(this);

        // Legacy commands (to be migrated to framework)
        getCommand("discord").setExecutor(new DiscordCommand(this));
        getCommand("broadcast").setExecutor(new BroadcastCommand(this));
        getCommand("puthat").setExecutor(new PutHatCommand(this));
        getCommand("rvnktools").setExecutor(new RVNKToolsCommand(this));
        TeleportWorldSwapSubCommand teleportWorldSwap = new TeleportWorldSwapSubCommand(this);
        getCommand("event").setExecutor(new EventCommand(teleportWorldSwap));
        TrainsCommand trainsCommand = new TrainsCommand(this);
        getCommand("trains").setExecutor(trainsCommand);
        getCommand("trains").setTabCompleter(trainsCommand);

        getLogger().info("RVNK Toolkit has been enabled.");
    }

    @Override
    public void onDisable() {
        if (api != null) api.stop();
        if (announceManager != null) {
            announceManager.shutdown(); 
        }
        api = null;        
        announceManager = null;
        cycleCommands = null;
        linkMaker = null;        
        getLogger().info("RVNK Toolkit has been disabled.");
    }

    public Economy getEconomy() {
        return economy;
    }

    public CycleCommands getCycleCommands() {
        return cycleCommands;
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