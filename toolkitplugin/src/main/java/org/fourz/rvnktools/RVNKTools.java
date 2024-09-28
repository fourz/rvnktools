package org.fourz.rvnktools;

import java.util.logging.Level;
import org.bukkit.plugin.java.JavaPlugin;
import org.checkerframework.checker.units.qual.A;
import org.fourz.rvnktools.commands.AnnouncementCommand;
import org.fourz.rvnktools.commands.DiscordCommand;
import org.fourz.rvnktools.commands.EventsCommand;
import org.fourz.rvnktools.AnnouncementManager;

public class RVNKTools extends JavaPlugin {

    private AnnouncementManager announcementManager;    

    @Override
    public void onEnable() {
		
        // Initialize AnnouncementManager
        announcementManager = new AnnouncementManager(this);

        // Code that runs when the plugin is enabled
		getServer().getPluginManager().registerEvents(new JoinListener(), this);
        
		// Register commands
		this.getCommand("ping").setExecutor(new PingCommand());
        this.getCommand("tps").setExecutor(this);
        this.getCommand("events").setExecutor(new EventsCommand());
        this.getCommand("discord").setExecutor(new DiscordCommand());

        getLogger().info("RVNK Toolkit has been enabled.");
    }

    @Override
    public void onDisable() {
        // Code that runs when the plugin is disabled
        getLogger().info("RVNK Toolkit has been disabled.");
    }
}


