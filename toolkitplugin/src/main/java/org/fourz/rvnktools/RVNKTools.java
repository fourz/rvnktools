package org.fourz.rvnktools;

import org.bukkit.Bukkit;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.fourz.rvnktools.command.CycleCommands;
import org.fourz.rvnktools.command.DiscordCommand;
import org.fourz.rvnktools.command.EventsCommand;
import org.fourz.rvnktools.command.PingCommand;
import org.fourz.rvnktools.command.TPSCommand;
import org.fourz.rvnktools.listener.JoinListener;
import org.fourz.rvnktools.listener.MickyHatPlaceListener;
import org.fourz.rvnktools.announceManager.AnnounceREST;

import net.milkbowl.vault.economy.Economy;

import org.fourz.rvnktools.Permission.LuckPermsManager;
import org.fourz.rvnktools.Permission.PermissionService;
import org.fourz.rvnktools.announceManager.AnnounceManager;
import org.fourz.rvnktools.command.BroadcastCommand;
import org.fourz.rvnktools.linkMaker.LinkMaker;
import org.fourz.rvnktools.hatManager.PutHatCommand;

public class RVNKTools extends JavaPlugin implements Listener {

    private AnnounceManager announceManager;
    private AnnounceREST announceREST;
    private Economy economy;    
    private CycleCommands cycleCommands;
    public LinkMaker linkMaker;
    public PermissionService permissionService;
    private int gcTaskId = -1;

    @Override
    public void onEnable() {

        // Initialize LuckPermsManager and PermissionService
        LuckPermsManager.init();
        permissionService = new PermissionService();
        
        // Initialize Economy
        this.economy = getServer().getServicesManager().getRegistration(Economy.class).getProvider();
        
        // Initialize AnnounceManager and REST API
        announceManager = new AnnounceManager(this);
        announceREST = new AnnounceREST(this, announceManager);
        announceREST.start();
        getLogger().info("REST API server started on port 8080");

        // Register PlaceholderAPI integration or flag as unavailable
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null) {
            getLogger().warning("PlaceholderAPI not found, PlaceholderAPI integration will be unavailable.");
        } else {
            getLogger().info("PlaceholderAPI found, PlaceholderAPI integration enabled.");
        } 

        // Initialize LinkMaker
        linkMaker = new LinkMaker(this);

        // Register Events
        getServer().getPluginManager().registerEvents(new JoinListener(this), this);
        getServer().getPluginManager().registerEvents(new MickyHatPlaceListener(), this);

        // Register Commands
        this.getCommand("ping").setExecutor(new PingCommand());
        this.getCommand("tps").setExecutor(new TPSCommand());
        this.getCommand("events").setExecutor(new EventsCommand());
        this.getCommand("discord").setExecutor(new DiscordCommand(this));        
        this.getCommand("broadcast").setExecutor(new BroadcastCommand(this));
        this.getCommand("puthat").setExecutor(new PutHatCommand(this));

        // Initialize and register CycleCommands
        cycleCommands = new CycleCommands(this);

        getLogger().info("RVNK Toolkit has been enabled.");                
    }

    @Override
    public void onDisable() {
        if (announceREST != null) {
            announceREST.stop();
        }
        
        if (announceManager != null) {
            announceManager.shutdown(); 
        }
        announceManager = null;
        cycleCommands = null;
        linkMaker = null;
        
        getLogger().info("RVNK Toolkit has been disabled.");
    }

    public Economy getEconomy() {
        return economy;
    }
}