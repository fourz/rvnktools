package org.fourz.rvnktools;

import org.bukkit.plugin.java.JavaPlugin;
import org.fourz.rvnktools.DiscordCommand;
import org.fourz.rvnktools.listener.MickyHatPlaceListener;
import org.fourz.rvnktools.AnnouncementManager;

public class RVNKTools extends JavaPlugin {

    private AnnouncementManager announcementManager;    

    @Override
    public void onEnable() {
		
        // Initialize AnnouncementManager
        announcementManager = new AnnouncementManager(this);

        // Code that runs when the plugin is enabled
		getServer().getPluginManager().registerEvents(new JoinListener(), this);
        getServer().getPluginManager().registerEvents(new MickyHatPlaceListener(), this);
        getLogger().info("yoo");
        
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


