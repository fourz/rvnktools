package org.fourz.rvnktools.announceManager;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.fourz.rvnktools.announceManager.data.DataStore;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class AnnouncePreferences {
    private final JavaPlugin plugin;
    private final DataStore dataStore;
    private Map<UUID, Set<String>> playerDisabledTypes;

    public AnnouncePreferences(JavaPlugin plugin, DataStore dataStore) {
        this.plugin = plugin;
        this.dataStore = dataStore;
        this.playerDisabledTypes = new HashMap<>();
        loadPreferences();
    }

    public void loadPreferences() {
        if (dataStore != null) {
            dataStore.connect();
            playerDisabledTypes = dataStore.getAllPlayerDisabledTypes();
            dataStore.disconnect();
            
            // Sync to YML backup
            saveToYml();
            return;
        }

        // Fallback to YML if no datastore
        loadFromYml();
    }

    private void loadFromYml() {
        File configFile = new File(plugin.getDataFolder(), "announceDisabledTypes.yml");
        if (!configFile.exists()) {
            return;
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(configFile);
        for (String key : config.getKeys(false)) {
            UUID playerId = UUID.fromString(key);
            List<String> disabledTypesList = config.getStringList(key);
            Set<String> disabledTypesSet = new HashSet<>(disabledTypesList);
            playerDisabledTypes.put(playerId, disabledTypesSet);
        }
    }

    public void savePreferences() {
        // Always maintain YML backup
        saveToYml();

        // Save to database if available
        if (dataStore != null) {
            dataStore.connect();
            // Get fresh data first
            playerDisabledTypes = dataStore.getAllPlayerDisabledTypes();
            
            // Save all preferences
            for (Map.Entry<UUID, Set<String>> entry : playerDisabledTypes.entrySet()) {
                for (String type : entry.getValue()) {
                    dataStore.savePlayerDisabledType(entry.getKey(), type);
                }
            }
            dataStore.disconnect();
        }
    }

    private void saveToYml() {
        File dataFolder = plugin.getDataFolder();
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }

        File configFile = new File(dataFolder, "announceDisabledTypes.yml");
        YamlConfiguration config = new YamlConfiguration();

        for (Map.Entry<UUID, Set<String>> entry : playerDisabledTypes.entrySet()) {
            String key = entry.getKey().toString();
            List<String> disabledTypesList = new ArrayList<>(entry.getValue());
            config.set(key, disabledTypesList);
        }

        try {
            config.save(configFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to sync announceDisabledTypes to YML: " + e.getMessage());
        }
    }

    public void addDisabledType(UUID playerId, String type) {
        if (dataStore != null) {
            dataStore.connect();
            dataStore.savePlayerDisabledType(playerId, type);
            dataStore.disconnect();
        }
        playerDisabledTypes.computeIfAbsent(playerId, k -> new HashSet<>()).add(type);
    }

    public void removeDisabledType(UUID playerId, String type) {
        if (dataStore != null) {
            dataStore.connect();
            dataStore.removePlayerDisabledType(playerId, type);
            dataStore.disconnect();
        }
        Set<String> types = playerDisabledTypes.get(playerId);
        if (types != null) {
            types.remove(type);
        }
    }

    public Set<String> getDisabledTypes(UUID playerId) {
        return playerDisabledTypes.getOrDefault(playerId, new HashSet<>());
    }

    public Map<UUID, Set<String>> getAllDisabledTypes() {
        return playerDisabledTypes;
    }
}