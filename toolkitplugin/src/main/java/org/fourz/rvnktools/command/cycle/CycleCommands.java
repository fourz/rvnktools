package org.fourz.rvnktools.command.cycle;

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
import net.md_5.bungee.api.chat.TextComponent;
import org.fourz.rvnktools.util.ChatFormat;
import org.fourz.rvnktools.RVNKTools;

public class CycleCommands {
    private final RVNKTools plugin;
    private FileConfiguration config;
    private CycleState state;
    private final Map<String, Map<UUID, Integer>> playerCommandPositions;

    public CycleCommands(RVNKTools plugin) {
        this.plugin = plugin;
        this.playerCommandPositions = new HashMap<>();
        loadConfig();
        registerCommands();
        plugin.getLogger().info("CycleCommands initialized.");
    }

    public RVNKTools getPlugin() {
        return plugin;
    }

    public void loadConfig() {
        // Load main config
        File cycleCommandsFile = new File(plugin.getDataFolder(), "cyclecommands.yml");
        if (!cycleCommandsFile.exists()) {
            plugin.saveResource("cyclecommands.yml", false);
        }
        this.config = YamlConfiguration.loadConfiguration(cycleCommandsFile);

        // Initialize and load state
        String stateFile = config.getString("data.file", "cyclestate.yml");
        this.state = new CycleState(plugin.getDataFolder(), stateFile);
        this.state.load();
        
        // Copy loaded state to working memory
        Map<String, Map<UUID, Integer>> loadedState = this.state.getPlayerCommandPositions();
        this.playerCommandPositions.clear();
        this.playerCommandPositions.putAll(loadedState);
    }

    public void saveState() {
        // Copy working memory to state manager
        for (Map.Entry<String, Map<UUID, Integer>> entry : playerCommandPositions.entrySet()) {
            for (Map.Entry<UUID, Integer> playerState : entry.getValue().entrySet()) {
                state.setPlayerCommandPosition(entry.getKey(), playerState.getKey(), playerState.getValue());
            }
        }
        state.save();
    }

    public void registerCommands() {
        ConfigurationSection commandsSection = config.getConfigurationSection("commands");

        if (commandsSection != null) {
            for (String commandKey : commandsSection.getKeys(false)) {
                ConfigurationSection commandConfig = commandsSection.getConfigurationSection(commandKey);
                if (commandConfig != null) {
                    PluginCommand pluginCommand = plugin.getCommand(commandKey);
                    if (pluginCommand != null) {
                        pluginCommand.setExecutor(new CycleCommandExecutor(commandKey, commandConfig, this));
                        plugin.getLogger().info("CycleCommands registered command: " + commandKey);

                        List<String> aliases = commandConfig.getStringList("aliases");
                        for (String alias : aliases) {
                            pluginCommand.getAliases().add(alias);
                        }

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

    public String getNextInstructionKey(String commandKey, UUID playerId) {
        Map<UUID, Integer> commandPositions = playerCommandPositions.computeIfAbsent(commandKey, k -> new HashMap<>());
        int position = commandPositions.getOrDefault(playerId, 0);

        ConfigurationSection instructionsSection = config.getConfigurationSection("commands." + commandKey + ".instructions");
        if (instructionsSection == null) {
            return null;
        }

        int totalInstructions = instructionsSection.getKeys(false).size();
        String nextInstructionKey = (String) instructionsSection.getKeys(false).toArray()[position];

        // Update position and save state
        position = (position + 1) % totalInstructions;
        commandPositions.put(playerId, position);
        saveState();

        return nextInstructionKey;
    }

    public long parseTime(String timeStr) {
        try {
            if (timeStr.endsWith("m")) {
                return Long.parseLong(timeStr.replace("m", "")) * 20 * 60;
            } else if (timeStr.endsWith("s")) {
                return Long.parseLong(timeStr.replace("s", "")) * 20;
            } else {
                return Long.parseLong(timeStr) * 20;
            }
        } catch (NumberFormatException e) {
            plugin.getLogger().warning("Invalid time format: " + timeStr);
            return 0;
        }
    }

    void messageAllPlayers(String message) {
        TextComponent processedMessage = processMessage(message, null);
        Bukkit.spigot().broadcast(processedMessage);
    }

    void messagePlayer(Player player, String message) {
        TextComponent processedMessage = processMessage(message, player);
        player.spigot().sendMessage(processedMessage);
    }

    void executeCommandAsync(String command) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            new BukkitRunnable() {
                @Override
                public void run() {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
                }
            }.runTask(plugin);
        });
    }

    private boolean containsLinkPlaceholder(String message) {
        return message != null && message.contains("{");
    }

    private TextComponent processMessage(String message, Player player) {
        if (message == null) {
            return new TextComponent("");
        }

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            message = PlaceholderAPI.setPlaceholders(player, message);
        }

        return ChatFormat.parse(message, containsLinkPlaceholder(message) ? plugin.linkMaker : null);
    }
}