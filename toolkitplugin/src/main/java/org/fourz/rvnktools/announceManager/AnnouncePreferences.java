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
    private Map<UUID, String> playerPreferences;

    public AnnouncePreferences(JavaPlugin plugin, DataStore dataStore) {
        this.plugin = plugin;
        this.dataStore = dataStore;
        this.playerDisabledTypes = new HashMap<>();
        this.playerPreferences = new HashMap<>();
        loadPreferences();
    }

    public void loadPreferences() {
        if (dataStore != null) {
            dataStore.connect();
            // Load disabled types
            playerDisabledTypes = dataStore.getAllPlayerDisabledTypes();
            
            // Load preferences for each player
            for (UUID playerId : playerDisabledTypes.keySet()) {
                String prefs = dataStore.getPlayerPreferences(playerId);
                if (prefs != null) {
                    playerPreferences.put(playerId, prefs);
                }
            }
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
        
        // Load preferences
        configFile = new File(plugin.getDataFolder(), "announcePreferences.yml");
        if (configFile.exists()) {
            config = YamlConfiguration.loadConfiguration(configFile);
            for (String key : config.getKeys(false)) {
                UUID playerId = UUID.fromString(key);
                if (config.contains(key + ".preferences")) {
                    playerPreferences.put(playerId, config.getString(key + ".preferences"));
                }
            }
        }
    }

    public void savePreferences() {
        // Always maintain YML backup
        saveToYml();

        // Save to database if available
        if (dataStore != null) {
            dataStore.connect();
            
            // Save preferences
            for (Map.Entry<UUID, String> entry : playerPreferences.entrySet()) {
                dataStore.savePlayerPreferences(entry.getKey(), entry.getValue());
            }
            
            // Save disabled types
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
        
        configFile = new File(dataFolder, "announcePreferences.yml");
        config = new YamlConfiguration();
        
        // Save both disabled types and preferences
        for (Map.Entry<UUID, Set<String>> entry : playerDisabledTypes.entrySet()) {
            String key = entry.getKey().toString();
            config.set(key + ".disabled_types", new ArrayList<>(entry.getValue()));
            
            // Save preferences if they exist
            String prefs = playerPreferences.get(entry.getKey());
            if (prefs != null) {
                config.set(key + ".preferences", prefs);
            }
        }

        try {
            config.save(configFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save preferences to YML: " + e.getMessage());
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

    public void setPlayerPreferences(UUID playerId, String preferences) {
        if (dataStore != null) {
            dataStore.connect();
            dataStore.savePlayerPreferences(playerId, preferences);
            dataStore.disconnect();
        }
        playerPreferences.put(playerId, preferences);
    }

    public String getPlayerPreferences(UUID playerId) {
        return playerPreferences.getOrDefault(playerId, null);
    }
}