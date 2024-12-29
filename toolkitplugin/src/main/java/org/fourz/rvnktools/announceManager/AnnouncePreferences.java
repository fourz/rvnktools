package org.fourz.rvnktools.announceManager;

import org.bukkit.plugin.java.JavaPlugin;
import org.fourz.rvnktools.announceManager.data.DataStoreManager;
import org.fourz.rvnktools.announceManager.data.YAMLManager;
import org.fourz.rvnktools.util.Debug;

import java.util.*;
import java.util.logging.Level;

public class AnnouncePreferences {
    private static final String CLASS_NAME = "AnnouncePreferences";
    private final JavaPlugin plugin;
    private final DataStoreManager dataManager;
    private final YAMLManager yamlManager;
    private final Debug debug;
    private Map<UUID, Set<String>> playerDisabledTypes;
    private Map<UUID, String> playerPreferences;

    public AnnouncePreferences(JavaPlugin plugin, DataStoreManager dataManager) {
        this.plugin = plugin;
        this.dataManager = dataManager;
        this.yamlManager = new YAMLManager(plugin);
        this.debug = new AnnouncePreferencesDebug(plugin, CLASS_NAME);
        this.playerDisabledTypes = new HashMap<>();
        this.playerPreferences = new HashMap<>();
        loadPreferences();
    }

    private class AnnouncePreferencesDebug extends Debug {
        public AnnouncePreferencesDebug(JavaPlugin plugin, String className) {
            super(plugin, CLASS_NAME, Level.FINE);
        }
    }    

    public void loadPreferences() {
        if (dataManager != null && dataManager.isInitialized()) {
            // Load from database
            playerDisabledTypes = dataManager.getAllPlayerDisabledTypes();
            
            // Load preferences for each player
            playerPreferences = new HashMap<>();
            for (UUID playerId : playerDisabledTypes.keySet()) {
                String prefs = dataManager.getPlayerPreferences(playerId);
                if (prefs != null) {
                    playerPreferences.put(playerId, prefs);
                }
            }
            
            // Sync to YML backup
            syncToYAML();
            debug.debug("Loaded preferences from database");
            return;
        }

        // Fallback to YML if no database
        loadFromYAML();
        debug.debug("Loaded preferences from YAML");
    }

    private void loadFromYAML() {
        playerDisabledTypes = yamlManager.loadPlayerDisabledTypes();
        playerPreferences = yamlManager.loadPlayerPreferences();
    }

    public void savePreferences() {
        // Always maintain YML backup
        syncToYAML();

        // Save to database if available
        if (dataManager != null && dataManager.isInitialized()) {
            // Save preferences
            for (Map.Entry<UUID, String> entry : playerPreferences.entrySet()) {
                dataManager.savePlayerPreferences(entry.getKey(), entry.getValue());
            }
            
            // Save disabled types
            for (Map.Entry<UUID, Set<String>> entry : playerDisabledTypes.entrySet()) {
                for (String type : entry.getValue()) {
                    dataManager.savePlayerDisabledType(entry.getKey(), type);
                }
            }
        }
    }

    private void syncToYAML() {
        yamlManager.savePreferences(playerDisabledTypes, playerPreferences);
    }

    public void addDisabledType(UUID playerId, String type) {
        if (dataManager != null && dataManager.isInitialized()) {
            dataManager.savePlayerDisabledType(playerId, type);
        }
        playerDisabledTypes.computeIfAbsent(playerId, k -> new HashSet<>()).add(type);
        syncToYAML();
    }

    public void removeDisabledType(UUID playerId, String type) {
        if (dataManager != null && dataManager.isInitialized()) {
            dataManager.removePlayerDisabledType(playerId, type);
        }
        Set<String> types = playerDisabledTypes.get(playerId);
        if (types != null) {
            types.remove(type);
            syncToYAML();
        }
    }

    public Set<String> getDisabledTypes(UUID playerId) {
        return playerDisabledTypes.getOrDefault(playerId, new HashSet<>());
    }

    public Map<UUID, Set<String>> getAllDisabledTypes() {
        return new HashMap<>(playerDisabledTypes);
    }

    public void setPlayerPreferences(UUID playerId, String preferences) {
        if (dataManager != null && dataManager.isInitialized()) {
            dataManager.savePlayerPreferences(playerId, preferences);
        }
        playerPreferences.put(playerId, preferences);
        syncToYAML();
    }

    public String getPlayerPreferences(UUID playerId) {
        return playerPreferences.get(playerId);
    }
}