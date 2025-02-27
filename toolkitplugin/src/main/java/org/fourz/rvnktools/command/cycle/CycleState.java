package org.fourz.rvnktools.command.cycle;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class CycleState {
    private final File stateFile;
    private final Map<String, Map<UUID, Integer>> playerCommandPositions;

    public CycleState(File pluginFolder, String filename) {
        this.stateFile = new File(pluginFolder, filename);
        this.playerCommandPositions = new HashMap<>();
    }

    public void load() {
        if (!stateFile.exists()) {
            return;
        }

        FileConfiguration state = YamlConfiguration.loadConfiguration(stateFile);
        for (String commandKey : state.getKeys(false)) {
            Map<UUID, Integer> positions = new HashMap<>();
            for (String playerUUID : state.getConfigurationSection(commandKey).getKeys(false)) {
                positions.put(UUID.fromString(playerUUID), 
                            state.getInt(commandKey + "." + playerUUID));
            }
            playerCommandPositions.put(commandKey, positions);
        }
    }

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
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public Map<String, Map<UUID, Integer>> getPlayerCommandPositions() {
        return playerCommandPositions;
    }

    public void setPlayerCommandPosition(String command, UUID player, int position) {
        playerCommandPositions
            .computeIfAbsent(command, k -> new HashMap<>())
            .put(player, position);
    }
}
