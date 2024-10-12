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

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

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
    }

    public void loadConfig() {
        plugin.saveResource("cyclecommands.yml", false);
        this.config = plugin.getConfig();
    }

    public void registerCommands() {
        ConfigurationSection commandsSection = config.getConfigurationSection("commands");
        if (commandsSection != null) {
            for (String commandKey : commandsSection.getKeys(false)) {
                ConfigurationSection commandConfig = commandsSection.getConfigurationSection(commandKey);
                if (commandConfig != null) {
                    PluginCommand pluginCommand = plugin.getCommand(commandKey);
                    if (pluginCommand != null) {
                        pluginCommand.setExecutor(new CycleCommandExecutor(commandKey, commandConfig));
                        plugin.getLogger().info("Registered command: " + commandKey);
                    }
                }
            }
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
                for (String key : instructions.getKeys(false)) {
                    switch (key) {
                        case "run_command_as_player":
                            String playerCommand = instructions.getString(key);
                            if (playerCommand != null) {
                                player.performCommand(playerCommand);
                            }
                            break;
                        case "run_command_as_server":
                            String serverCommand = instructions.getString(key).replace("$player", player.getName());
                            if (serverCommand != null) {
                                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), serverCommand);
                            }
                            break;
                        case "send_message_to_player":
                            String message = instructions.getString(key);
                            if (message != null) {
                                player.sendMessage(message);
                            }
                            break;
                        case "send_message_to_all_players":
                            String broadcastMessage = instructions.getString(key);
                            if (broadcastMessage != null) {
                                Bukkit.broadcastMessage(broadcastMessage);
                            }
                            break;
                        case "wait":
                            // Handle wait logic if required, might need async scheduling
                            break;
                    }
                }
            } else {
                player.sendMessage("No instruction set found for key: " + instructionKey);
            }

            return true;
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
    }
}