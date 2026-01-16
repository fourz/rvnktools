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
import org.fourz.rvnktools.util.log.LogManager;
import org.fourz.rvnkcore.RVNKCore;

/**
 * Manages cycle commands, which cycle through different instruction sets
 * each time they are executed by a player. This allows for commands that
 * toggle between different behaviors.
 */
public class CycleCommands {
    private final RVNKCore plugin;
    private final LogManager logger;
    private FileConfiguration config;
    private CycleState state;
    private final Map<String, Map<UUID, Integer>> playerCommandPositions;

    /**
     * Initializes the cycle commands manager.
     *
     * @param plugin The RVNKCore plugin instance
     */
    public CycleCommands(RVNKCore plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, getClass());
        this.playerCommandPositions = new HashMap<>();
        loadConfig();
        registerCommands();
        logger.info("CycleCommands initialized successfully");
    }

    /**
     * Gets the plugin instance.
     *
     * @return The RVNKCore plugin instance
     */
    public RVNKCore getPlugin() {
        return plugin;
    }

    /**
     * Loads the cycle commands configuration and state.
     */
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
        this.state = new CycleState(plugin, stateFile);
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

    /**
     * Saves the current state of all cycle commands.
     */
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

    /**
     * Registers all cycle commands defined in the configuration.
     */
    public void registerCommands() {
        ConfigurationSection commandsSection = config.getConfigurationSection("commands");

        if (commandsSection != null) {
            int registeredCount = 0;
            for (String commandKey : commandsSection.getKeys(false)) {
                ConfigurationSection commandConfig = commandsSection.getConfigurationSection(commandKey);
                if (commandConfig != null) {
                    try {
                        // Register the command with a CycleCommandExecutor
                        CycleCommandExecutor executor = new CycleCommandExecutor(commandKey, commandConfig, this);
                        plugin.getCommand(commandKey).setExecutor(executor);
                        
                        // Get permission if specified in config
                        String permission = commandConfig.getString("permission");
                        if (permission != null) {
                            logger.debug("Registering command: " + commandKey + " with permission: " + permission);
                        } else {
                            logger.debug("Registering command: " + commandKey + " with no permission");
                        }
                        
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

    /**
     * Gets the next instruction key for a player executing a command.
     *
     * @param commandKey The command being executed
     * @param playerId The UUID of the player executing the command
     * @return The key of the next instruction set to execute
     */
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

    /**
     * Parses a time string into ticks.
     *
     * @param timeStr The time string (e.g., "5s" for 5 seconds, "2m" for 2 minutes)
     * @return The number of ticks
     */
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

    /**
     * Sends a message to all players on the server.
     *
     * @param message The message to send
     */
    void messageAllPlayers(String message) {
        TextComponent processedMessage = processMessage(message, null);
        Bukkit.spigot().broadcast(processedMessage);
    }

    /**
     * Sends a message to a specific player.
     *
     * @param player The player to send the message to
     * @param message The message to send
     */
    void messagePlayer(Player player, String message) {
        TextComponent processedMessage = processMessage(message, player);
        player.spigot().sendMessage(processedMessage);
    }

    /**
     * Executes a command asynchronously.
     *
     * @param command The command to execute
     */
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

    /**
     * Checks if a message contains link placeholders.
     *
     * @param message The message to check
     * @return True if the message contains link placeholders
     */
    private boolean containsLinkPlaceholder(String message) {
        return message != null && message.contains("{");
    }

    /**
     * Processes a message with placeholders and formatting.
     *
     * @param message The message to process
     * @param player The player context for placeholders
     * @return The processed message as a TextComponent
     */
    private TextComponent processMessage(String message, Player player) {
        if (message == null) {
            return new TextComponent("");
        }

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            message = PlaceholderAPI.setPlaceholders(player, message);
        }

        return ChatFormat.parse(message, containsLinkPlaceholder(message) ? plugin.getLinkMaker() : null);
    }
}