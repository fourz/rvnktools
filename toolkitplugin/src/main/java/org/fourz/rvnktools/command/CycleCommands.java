package org.fourz.rvnktools.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class CycleCommands {
    private final JavaPlugin plugin;
    private FileConfiguration cycleCommandsConfig;
    private final Map<String, CommandDefinition> commandDefinitions = new HashMap<>();
    private final Map<String, AtomicInteger> playerCycleIndices = new HashMap<>();

    public CycleCommands(JavaPlugin plugin) {
        this.plugin = plugin;
        loadConfig();
        registerCommands();
    }

    // Loads the configuration file from the plugin's data folder
    private void loadConfig() {
        File configFile = new File(plugin.getDataFolder(), "cyclecommands.yml");
        if (!configFile.exists()) {
            // Save the default configuration file if it does not exist
            plugin.saveResource("cyclecommands.yml", false);
        }
        cycleCommandsConfig = YamlConfiguration.loadConfiguration(configFile);
        parseCommands();
    }

    // Parses the commands from the configuration file and stores them in memory
    private void parseCommands() {
        if (cycleCommandsConfig == null) return;

        // Iterate through each command defined in the configuration
        for (String commandName : cycleCommandsConfig.getConfigurationSection("commands").getKeys(false)) {
            String description = cycleCommandsConfig.getString("commands." + commandName + ".description");
            String usage = cycleCommandsConfig.getString("commands." + commandName + ".usage");
            String alias = cycleCommandsConfig.getString("commands." + commandName + ".alias");
            CommandDefinition commandDefinition = new CommandDefinition(commandName, description, usage, alias);

            // Iterate through each set for the command and store its actions
            for (String setName : cycleCommandsConfig.getConfigurationSection("commands." + commandName + ".sets").getKeys(false)) {
                List<Map<?, ?>> actions = cycleCommandsConfig.getMapList("commands." + commandName + ".sets." + setName);
                commandDefinition.getSets().put(setName, actions);
            }
            commandDefinitions.put(commandName, commandDefinition);
        }
    }

    // Registers all the parsed commands with the server
    private void registerCommands() {
        for (String commandName : commandDefinitions.keySet()) {
            plugin.getCommand(commandName).setExecutor(new CommandCycleExecutor(commandDefinitions.get(commandName)));
            plugin.getLogger().info("Registered command: " + commandName);
        }
    }

    // Inner class to handle the execution of cycle commands
    public class CommandCycleExecutor implements CommandExecutor {
        private final CommandDefinition commandDefinition;

        public CommandCycleExecutor(CommandDefinition commandDefinition) {
            this.commandDefinition = commandDefinition;
        }

        @Override
        public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            // Ensure the command sender is a player
            if (!(sender instanceof Player)) {
                sender.sendMessage("This command can only be run by a player.");
                return true;
            }
            Player player = (Player) sender;

            // Create a unique key for the player and command to track their cycle progress
            String playerKey = player.getUniqueId().toString() + "-" + commandDefinition.name;
            AtomicInteger cycleIndex = playerCycleIndices.computeIfAbsent(playerKey, k -> new AtomicInteger(0));

            // Get the list of sets for the command
            List<String> setNames = commandDefinition.getSets().keySet().stream().toList();
            if (setNames.isEmpty()) {
                player.sendMessage("No sets available for this command.");
                return true;
            }

            // Get the current set based on the cycle index and increment the index
            int currentIndex = cycleIndex.getAndUpdate(i -> (i + 1) % setNames.size());
            String setName = setNames.get(currentIndex);
            List<Map<?, ?>> actions = commandDefinition.getSets().get(setName);

            // Execute each action defined in the set
            for (Map<?, ?> actionMap : actions) {
                String action = (String) actionMap.get("action");
                String detail = (String) actionMap.get("detail");
                executeAction(player, action, detail);
            }
            return true;
        }

        // Executes a specific action for the player
        private void executeAction(Player player, String action, String detail) {
            switch (action) {
                case "run_command_as_player":
                    // Player executes a command
                    player.performCommand(detail);
                    break;
                case "run_command_as_server":
                    // Server executes a command, replacing $player with the player's name
                    plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(), detail.replace("$player", player.getName()));
                    break;
                case "send_message_to_player":
                    // Send a message to the player
                    player.sendMessage(detail);
                    break;
                default:
                    // Handle unknown actions
                    player.sendMessage("Unknown action: " + action);
            }
        }
    }

    // Class to define the structure of a command
    private static class CommandDefinition {
        private final String name;
        private final String description;
        private final String usage;
        private final String alias;
        private final Map<String, List<Map<?, ?>>> sets = new HashMap<>();

        public CommandDefinition(String name, String description, String usage, String alias) {
            this.name = name;
            this.description = description;
            this.usage = usage;
            this.alias = alias;
        }

        public String getUsage() {
            return usage;
        }

        public Map<String, List<Map<?, ?>>> getSets() {
            return sets;
        }
    }
}