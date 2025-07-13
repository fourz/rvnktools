package org.fourz.rvnktools.command.cycle;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import me.clip.placeholderapi.PlaceholderAPI;
import net.md_5.bungee.api.chat.TextComponent;
import org.fourz.rvnktools.util.ChatFormat;
import org.fourz.rvnktools.RVNKTools;
import org.fourz.rvnktools.util.logging.LogManager;

public class CycleCommands {
    private final RVNKTools plugin;
    private final LogManager logger;
    private FileConfiguration config;
    private CycleState state;
    private final Map<String, Map<UUID, Integer>> playerCommandPositions;

    public CycleCommands(RVNKTools plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, getClass());
        this.playerCommandPositions = new HashMap<>();
        loadConfig();
        registerCommands();
        logger.info("CycleCommands initialized successfully");
    }

    public RVNKTools getPlugin() {
        return plugin;
    }

    public void loadConfig() {
        // Load main config
        File cycleCommandsFile = new File(plugin.getDataFolder(), "cyclecommands.yml");
        if (!cycleCommandsFile.exists()) {
            logger.debug("cyclecommands.yml not found, creating default configuration");
            plugin.saveResource("cyclecommands.yml", false);
        }
        this.config = YamlConfiguration.loadConfiguration(cycleCommandsFile);

        // Initialize and load state
        String stateFile = config.getString("data.file", "cyclestate.yml");
        this.state = new CycleState(plugin.getDataFolder(), stateFile);
        try {
            this.state.load();
            logger.debug("Successfully loaded cycle state from " + stateFile);
        } catch (Exception e) {
            logger.warning("Failed to load cycle state from " + stateFile + ": " + e.getMessage());
        }
        
        // Copy loaded state to working memory
        Map<String, Map<UUID, Integer>> loadedState = this.state.getPlayerCommandPositions();
        this.playerCommandPositions.clear();
        this.playerCommandPositions.putAll(loadedState);
        logger.debug("Loaded " + loadedState.size() + " command states");
    }

    public void saveState() {
        logger.debug("Saving cycle command states...");
        try {
            // Copy working memory to state manager
            for (Map.Entry<String, Map<UUID, Integer>> entry : playerCommandPositions.entrySet()) {
                for (Map.Entry<UUID, Integer> playerState : entry.getValue().entrySet()) {
                    state.setPlayerCommandPosition(entry.getKey(), playerState.getKey(), playerState.getValue());
                }
            }
            state.save();
            logger.debug("Successfully saved cycle command states");
        } catch (Exception e) {
            logger.error("Failed to save cycle command states", e);
        }
    }

    public void registerCommands() {
        ConfigurationSection commandsSection = config.getConfigurationSection("commands");

        if (commandsSection != null) {
            int registeredCount = 0;
            for (String commandKey : commandsSection.getKeys(false)) {
                ConfigurationSection commandConfig = commandsSection.getConfigurationSection(commandKey);
                if (commandConfig != null) {
                    try {
                        String permission = commandConfig.getString("permission");
                        logger.debug("Registering command: " + commandKey + " with permission: " + permission);
                        registeredCount++;
                    } catch (Exception e) {
                        logger.error("Failed to register command: " + commandKey, e);
                    }
                } else {
                    logger.warning("Invalid configuration for command: " + commandKey);
                }
            }
            logger.info("Successfully registered " + registeredCount + " cycle commands");
        } else {
            logger.warning("No commands section found in configuration");
        }
    }

    public String getNextInstructionKey(String commandKey, UUID playerId) {
        Map<UUID, Integer> commandPositions = playerCommandPositions.computeIfAbsent(commandKey, k -> new HashMap<>());
        int position = commandPositions.getOrDefault(playerId, 0);

        ConfigurationSection instructionsSection = config.getConfigurationSection("commands." + commandKey + ".instructions");
        if (instructionsSection == null) {
            logger.error("No instructions found for command: " + commandKey);
            return null;
        }

        int totalInstructions = instructionsSection.getKeys(false).size();
        if (totalInstructions == 0) {
            logger.warning("Command " + commandKey + " has no instructions defined");
            return null;
        }

        String nextInstructionKey = (String) instructionsSection.getKeys(false).toArray()[position];
        logger.debug("Next instruction for " + commandKey + " (player: " + playerId + "): " + nextInstructionKey);

        // Update position and save state
        position = (position + 1) % totalInstructions;
        commandPositions.put(playerId, position);
        saveState();

        return nextInstructionKey;
    }

    public long parseTime(String timeStr) {
        try {
            if (timeStr.endsWith("m")) {
                return Long.parseLong(timeStr.substring(0, timeStr.length() - 1)) * 1200L;
            } else if (timeStr.endsWith("s")) {
                return Long.parseLong(timeStr.substring(0, timeStr.length() - 1)) * 20L;
            } else {
                return Long.parseLong(timeStr) * 20L;
            }
        } catch (NumberFormatException e) {
            logger.error("Invalid time format: " + timeStr, e);
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