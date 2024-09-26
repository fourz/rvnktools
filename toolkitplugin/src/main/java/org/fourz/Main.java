package org.fourz;

import java.util.logging.Level;
import org.bukkit.plugin.java.JavaPlugin;
// import org.bukkit.plugin.PluginManager;

public class Main extends JavaPlugin {

    @Override
    public void onEnable() {
		
        // Code that runs when the plugin is enabled
		getServer().getPluginManager().registerEvents(new JoinListener(), this);
        getLogger().info("RVNK Toolkit has been enabled.");
        getLogger().info("Brian is a neeeeeeeerd")
		// Register commands
		this.getCommand("ping").setExecutor(new PingCommand());
    }

    @Override
    public void onDisable() {
        // Code that runs when the plugin is disabled
        getLogger().info("RVNK Toolkit has been disabled.");
    }
}


