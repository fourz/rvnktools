package org.fourz.rvnktools;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.fourz.rvnktools.announcementManager.AnnouncementManager;
import org.fourz.rvnktools.command.CycleCommands;
import org.fourz.rvnktools.command.DiscordCommand;
import org.fourz.rvnktools.command.EventsCommand;
import org.fourz.rvnktools.command.PingCommand;
import org.fourz.rvnktools.listener.JoinListener;
import org.fourz.rvnktools.listener.MickyHatPlaceListener;
import org.fourz.rvnktools.command.BroadcastCommand;

public class RVNKTools extends JavaPlugin {

    private AnnouncementManager announcementManager;
    private CycleCommands cycleCommands;

    @Override
    public void onEnable() {

        // Save default config if not present
        // saveDefaultConfig();
        
        // Initialize AnnouncementManager
        // announcementManager = new AnnouncementManager(this);

        // Code that runs when the plugin is enabled
        getServer().getPluginManager().registerEvents(new JoinListener(this), this);
        getServer().getPluginManager().registerEvents(new MickyHatPlaceListener(), this);
        getLogger().info("yoo");

        // Register commands
        this.getCommand("ping").setExecutor(new PingCommand());
        this.getCommand("tps").setExecutor(this);
        this.getCommand("events").setExecutor(new EventsCommand());
        this.getCommand("discord").setExecutor(new DiscordCommand(this));        
        this.getCommand("broadcast").setExecutor(new BroadcastCommand(this));

        // Initialize and register CycleCommands
        // cycleCommands = new CycleCommands(this);

        getLogger().info("RVNK Toolkit has been enabled.");
    }

    @Override
    public void onDisable() {
        // Code that runs when the plugin is disabled
        getLogger().info("RVNK Toolkit has been disabled.");
    }

    // private void registerToggleCommands() {
    //     FileConfiguration config = getConfig();

    //     config.getConfigurationSection("togglecommands").getKeys(false).forEach(commandName -> {
    //         List<String> toggleOnCommands = config.getStringList("togglecommands." + commandName + ".toggleoncommands");
    //         List<String> toggleOffCommands = config.getStringList("togglecommands." + commandName + ".toggleoffcommands");
    //         String toggleOnMessage = config.getString("togglecommands." + commandName + ".toggleonmessage");
    //         String toggleOffMessage = config.getString("togglecommands." + commandName + ".toggleoffmessage");
    //         String permissionNode = config.getString("togglecommands." + commandName + ".permissionnode");

    //         getCommand(commandName).setExecutor(new ToggleCommand(this, commandName, toggleOnCommands, toggleOffCommands, toggleOnMessage, toggleOffMessage, permissionNode));
    //     });
    // }
}


