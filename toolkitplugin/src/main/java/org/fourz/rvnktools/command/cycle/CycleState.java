package org.fourz.rvnktools.command.cycle;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.fourz.rvnktools.RVNKTools;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Manages the state of cycle commands, tracking the position of each player
 * within the cycle sequence for each command.
 */
public class CycleState {
    private final File stateFile;
    private final Map<String, Map<UUID, Integer>> playerCommandPositions;
    // Logger removed

    /**
     * Initializes a new cycle state manager.
     *
     * @param plugin The RVNKTools plugin instance
     * @param filename The name of the file to store state in
     */
    public CycleState(RVNKTools plugin, String filename) {
        this.stateFile = new File(plugin.getDataFolder(), filename);
        this.playerCommandPositions = new HashMap<>();
    }

    /**
     * Loads the state from disk.
     */
    public void load() {
        if (!stateFile.exists()) {
            // No cycle state file found, creating a new one
            return;
        }

        try {
            FileConfiguration state = YamlConfiguration.loadConfiguration(stateFile);
            for (String commandKey : state.getKeys(false)) {
                Map<UUID, Integer> positions = new HashMap<>();
                for (String playerUUID : state.getConfigurationSection(commandKey).getKeys(false)) {
                    positions.put(UUID.fromString(playerUUID), 
                                state.getInt(commandKey + "." + playerUUID));
                }
                playerCommandPositions.put(commandKey, positions);
            }
            // Cycle command state loaded successfully
        } catch (Exception e) {
            // Failed to load cycle command state
            e.printStackTrace();
        }
    }

    /**
     * Saves the state to disk.
     */
    public void save() {
        FileConfiguration state = new YamlConfiguration();
        
        for (Map.Entry<String, Map<UUID, Integer>> commandEntry : playerCommandPositions.entrySet()) {
            for (Map.Entry<UUID, Integer> playerEntry : commandEntry.getValue().entrySet()) {
                state.set(commandEntry.getKey() + "." + playerEntry.getKey().toString(), 
                         playerEntry.getValue());
            }
        }

        try {
            state.save(stateFile);
            // Cycle command state saved successfully
        } catch (Exception e) {
            // Failed to save cycle command state
            e.printStackTrace();
        }
    }

    /**
     * Gets all player command positions.
     *
     * @return A map of command keys to player positions
     */
    public Map<String, Map<UUID, Integer>> getPlayerCommandPositions() {
        return playerCommandPositions;
    }

    /**
     * Sets a player's position for a specific command.
     *
     * @param command The command key
     * @param player The player's UUID
     * @param position The position in the cycle
     */
    public void setPlayerCommandPosition(String command, UUID player, int position) {
        playerCommandPositions
            .computeIfAbsent(command, k -> new HashMap<>())
            .put(player, position);
    }
}