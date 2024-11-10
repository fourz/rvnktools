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
import org.fourz.rvnktools.Permission.LuckPermsManager;
import org.fourz.rvnktools.Permission.PermissionService;
import org.fourz.rvnktools.announceManager.AnnounceManager;
import org.fourz.rvnktools.command.BroadcastCommand;
import org.fourz.rvnktools.linkMaker.LinkMaker;

public class RVNKTools extends JavaPlugin implements Listener {

    private AnnounceManager announcementManager;
    private CycleCommands cycleCommands;
    public LinkMaker linkMaker;
    public PermissionService permissionService;
    private int gcTaskId = -1;

    @Override
    public void onEnable() {

        // Save default config if not present
        // saveDefaultConfig();

        LuckPermsManager.init();
        permissionService = new PermissionService();
        
        // Initialize AnnouncementManager
        announcementManager = new AnnounceManager(this);

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

        // Initialize and register CycleCommands
        cycleCommands = new CycleCommands(this);

        // Schedule periodic garbage collection
        scheduleGarbageCollection();

        getLogger().info("RVNK Toolkit has been enabled.");
                
    }

    @Override
    public void onDisable() {

        // Cancel GC task if running
        if (gcTaskId != -1) {
            Bukkit.getScheduler().cancelTask(gcTaskId);
            gcTaskId = -1;
        }

        announcementManager.savePlayerDisabledTypes();
        announcementManager.shutdown();

        //garbage collection        
        announcementManager = null;
        cycleCommands = null;
        linkMaker = null;
        
        
        // Code that runs when the plugin is disabled
        getLogger().info("RVNK Toolkit has been disabled.");
    }

    private void scheduleGarbageCollection() {
        gcTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(this, () -> {
            // Run cleanup on components
            if (announcementManager != null) {
                announcementManager.cleanup();
            }
            
            // Request garbage collection
            System.gc();
            
            getLogger().fine("Performed scheduled garbage collection");
        }, 6000L, 6000L); // Run every 5 minutes (6000 ticks)
    }
}