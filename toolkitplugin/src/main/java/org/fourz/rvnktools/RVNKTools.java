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
import org.fourz.rvnktools.announceManager.AnnounceManager;
import org.fourz.rvnktools.command.BroadcastCommand;

public class RVNKTools extends JavaPlugin implements Listener {

    private AnnounceManager announcementManager;
    private CycleCommands cycleCommands;

    @Override
    public void onEnable() {

        // Save default config if not present
        // saveDefaultConfig();
        
        // Initialize AnnouncementManager
        announcementManager = new AnnounceManager(this);

        // Register PlaceholderAPI integration or flag as unavailable
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null) {
            getLogger().warning("PlaceholderAPI not found, PlaceholderAPI integration will be unavailable.");
        } else {
            getLogger().info("PlaceholderAPI found, PlaceholderAPI integration enabled.");
        } 

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

        getLogger().info("RVNK Toolkit has been enabled.");
                
    }

    @Override
    public void onDisable() {

        announcementManager.savePlayerDisabledTypes();

        //garbage collection        
        announcementManager = null;
        cycleCommands = null;

        
        // Code that runs when the plugin is disabled
        getLogger().info("RVNK Toolkit has been disabled.");
    }
}


