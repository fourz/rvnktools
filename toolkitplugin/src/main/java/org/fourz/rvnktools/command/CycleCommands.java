package org.fourz.rvnktools.command;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Iterator;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import me.clip.placeholderapi.PlaceholderAPI;

import org.fourz.rvnktools.util.ChatFormat;
import org.fourz.rvnktools.RVNKTools;
import net.md_5.bungee.api.chat.TextComponent;

public class CycleCommands {
    private final RVNKTools plugin;
    private FileConfiguration config;
    private final Map<String, Map<UUID, Integer>> playerCommandPositions;

    public CycleCommands(RVNKTools plugin) {
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

        if (commandsSection != null) {
            for (String commandKey : commandsSection.getKeys(false)) {

                ConfigurationSection commandConfig = commandsSection.getConfigurationSection(commandKey);
                if (commandConfig != null) {

                    PluginCommand pluginCommand = plugin.getCommand(commandKey);
                    if (pluginCommand != null) {

                        // register command
                        pluginCommand.setExecutor(new CycleCommandExecutor(commandKey, commandConfig));
                        plugin.getLogger().info("CycleCommands registered command: " + commandKey);

                        // register aliases
                        List<String> aliases = commandConfig.getStringList("aliases");
                        for (String alias : aliases) {
                            pluginCommand.getAliases().add(alias);
                        }

                        // set command description
                        String description = commandConfig.getString("description");
                        if (description != null) {
                            pluginCommand.setDescription(description);
                        }
                    } else {

                        plugin.getLogger().info("CycleCommands: Plugin command for " + commandKey + " is null");
                    }
                } else {
                    plugin.getLogger().info("CycleCommands: Command config for " + commandKey + " is null");
                }
            }
        } else {
            plugin.getLogger().info("CycleCommands: Commands section is null");
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
            ConfigurationSection instructions = commandConfig.getConfigurationSection("instructions");

            if (commandConfig.contains("permission")) {                
                // retrieve the permission node for the command
                String permissionsString = commandConfig.getString("permission").toLowerCase();     

                // check permissions for the command
                if (!player.hasPermission(permissionsString)) {
                    player.sendMessage("You do not have permission to use this command.");
                    return true;
                }
            }


            if (instructions != null) {
                List<Map<?, ?>> instructionList = instructions.getMapList(instructionKey);

                //plugin.getLogger().info("instructionList: " + instructionList);
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
            String value = instruction.get(key).toString();

            switch (key) {
                case "run_command_as_player":
                    player.performCommand(value);
                    executeInstructions(player, iterator);
                    break;
                case "run_command_as_server":
                    String serverCommand = value.replace("$player", player.getName());
                    //Bukkit.dispatchCommand(Bukkit.getConsoleSender(), serverCommand);
                    executeCommandAsync(serverCommand);
                    executeInstructions(player, iterator);
                    break;
                case "send_message_to_player":
                    messagePlayer(player, value);
                    executeInstructions(player, iterator);
                    break;
                case "send_message_to_all_players":
                    Bukkit.broadcastMessage(value);
                    executeInstructions(player, iterator);
                    break;
                case "set_permission":
                    plugin.permissionService.addPermission(player.getUniqueId(), value);
                    executeInstructions(player, iterator);
                    break;
                case "unset_permission":
                    plugin.permissionService.removePermission(player.getUniqueId(), value);
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

    private void messagePlayer (Player player, String message) {

        //if message is null, return a vertical space to avoid using unnecessary parsing methods
        if (message == null) {
            player.sendMessage("");
            return;
        }   

        //if placeholderAPI is enabled
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {

            //set any placeholders in the message
            message = PlaceholderAPI.setPlaceholders(player, message);
        } 
        
        //use ChatFormat to colorize the message and replace linkMaker placeholders
        TextComponent constructedMessage = ChatFormat.parse(message, plugin.linkMaker);

        //send the message to the player
        player.spigot().sendMessage(constructedMessage);        
    }

    // This method demonstrates how to run a command asynchronously but safely
    public void executeCommandAsync(String command) {
        // Start an asynchronous task
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            // Perform any async operations here (e.g., data retrieval or heavy computation)
            // Then queue the command execution on the main thread
            new BukkitRunnable() {
                @Override
                public void run() {
                    // Execute the command safely on the main thread
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
                }
            }.runTask(plugin); // Switch back to the main thread here
        });
    }

}