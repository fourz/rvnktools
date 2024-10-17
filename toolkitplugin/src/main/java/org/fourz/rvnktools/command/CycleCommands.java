package org.fourz.rvnktools.command;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import org.bukkit.configuration.file.YamlConfiguration;
//import 

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Iterator;

public class CycleCommands {
    private final JavaPlugin plugin;
    private FileConfiguration config;
    private final Map<String, Map<UUID, Integer>> playerCommandPositions;

    public CycleCommands(JavaPlugin plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfig();
        this.playerCommandPositions = new HashMap<>();
        loadConfig();
        registerCommands();
        plugin.getLogger().info("CycleCommands initialized.");
    }

    public void loadConfig() {
        File cycleCommandsFile = new File(plugin.getDataFolder(), "cyclecommands.yml");
        if (!cycleCommandsFile.exists()) {
            plugin.saveResource("cyclecommands.yml", false);
        }
        this.config = YamlConfiguration.loadConfiguration(cycleCommandsFile);
    }

    public void registerCommands() {
        ConfigurationSection commandsSection = config.getConfigurationSection("commands");
        // output the commands section to the console
        plugin.getLogger().info("Commands section: " + commandsSection);

        if (commandsSection != null) {
            for (String commandKey : commandsSection.getKeys(false)) {
                plugin.getLogger().info("Processing command key: " + commandKey);

                ConfigurationSection commandConfig = commandsSection.getConfigurationSection(commandKey);
                if (commandConfig != null) {
                    plugin.getLogger().info("Command config for " + commandKey + ": " + commandConfig);

                    PluginCommand pluginCommand = plugin.getCommand(commandKey);
                    if (pluginCommand != null) {
                        plugin.getLogger().info("Plugin command for " + commandKey + ": " + pluginCommand);

                        pluginCommand.setExecutor(new CycleCommandExecutor(commandKey, commandConfig));
                        plugin.getLogger().info("Registered command: " + commandKey);

                    } else {

                        plugin.getLogger().info("Plugin command for " + commandKey + " is null");
                    }
                } else {

                    plugin.getLogger().info("Command config for " + commandKey + " is null");
                }
            }
        } else {

            plugin.getLogger().info("Commands section is null");
        }
    }

    private class CycleCommandExecutor implements CommandExecutor {
        private final String commandKey;
        private final ConfigurationSection commandConfig;

        public CycleCommandExecutor(String commandKey, ConfigurationSection commandConfig) {
            this.commandKey = commandKey;
            this.commandConfig = commandConfig;
        }

        @Override
        public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("This command can only be used by players.");
                return true;
            }

            Player player = (Player) sender;
            UUID playerId = player.getUniqueId();
            String instructionKey = getNextInstructionKey(playerId);
            ConfigurationSection instructions = commandConfig.getConfigurationSection("instructions." + instructionKey);

            if (instructions != null) {
                List<Map<?, ?>> instructionList = instructions.getMapList("steps"); // Adjusted to handle lists
                executeInstructions(player, instructionList.iterator());
            } else {
                player.sendMessage("No instruction set found for key: " + instructionKey);
            }

            return true;
        }

        private void executeInstructions(Player player, Iterator<Map<?, ?>> iterator) {
            if (!iterator.hasNext()) return;

            Map<?, ?> instruction = iterator.next();
            
            String key = instruction.keySet().iterator().next().toString();
            plugin.getLogger().info("Instruction key: " + key);

            String value = instruction.get(key).toString();
            plugin.getLogger().info("Instruction value: " + value);

            switch (key) {
                case "run_command_as_player":
                    player.performCommand(value);
                    executeInstructions(player, iterator);
                    break;
                case "run_command_as_server":
                    String serverCommand = value.replace("$player", player.getName());
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), serverCommand);
                    executeInstructions(player, iterator);
                    break;
                case "send_message_to_player":
                    player.sendMessage(value);
                    executeInstructions(player, iterator);
                    break;
                case "send_message_to_all_players":
                    Bukkit.broadcastMessage(value);
                    executeInstructions(player, iterator);
                    break;
                case "wait":
                    long delay = parseTime(value);
                    Bukkit.getScheduler().runTaskLater(plugin, () -> executeInstructions(player, iterator), delay);
                    break;
                default:
                    plugin.getLogger().warning("Unknown instruction: " + key);
                    executeInstructions(player, iterator);
                    break;
            }
        }
        private String getNextInstructionKey(UUID playerId) {
            Map<UUID, Integer> commandPositions = playerCommandPositions.computeIfAbsent(commandKey, k -> new HashMap<>());
            int position = commandPositions.getOrDefault(playerId, 0);

            ConfigurationSection instructionsSection = commandConfig.getConfigurationSection("instructions");
            if (instructionsSection == null) {
                return null;
            }

            int totalInstructions = instructionsSection.getKeys(false).size();
            String nextInstructionKey = (String) instructionsSection.getKeys(false).toArray()[position];

            // Update position for next time
            position = (position + 1) % totalInstructions;
            commandPositions.put(playerId, position);

            return nextInstructionKey;
        }
        private long parseTime(String timeStr) {
            // Implement parsing of time strings like "1m", "30s"
            // For simplicity, let's assume 'm' for minutes and 's' for seconds
            try {
                if (timeStr.endsWith("m")) {
                    return Long.parseLong(timeStr.replace("m", "")) * 20 * 60;
                } else if (timeStr.endsWith("s")) {
                    return Long.parseLong(timeStr.replace("s", "")) * 20;
                } else {
                    return Long.parseLong(timeStr) * 20; // Assuming ticks
                }
            } catch (NumberFormatException e) {
                plugin.getLogger().warning("Invalid time format: " + timeStr);
                return 0;
            }
        }
    }
}