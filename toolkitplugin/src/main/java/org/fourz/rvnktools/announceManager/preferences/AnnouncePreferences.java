package org.fourz.rvnktools.announceManager.preferences;

import org.bukkit.plugin.java.JavaPlugin;
import org.fourz.rvnktools.announceManager.data.DataStoreManager;
import org.fourz.rvnktools.announceManager.data.YAMLManager;
import org.fourz.rvnkcore.util.log.LogManager;

import java.util.*;

public class AnnouncePreferences {
    private final JavaPlugin plugin;
    private final DataStoreManager dataManager;
    private final YAMLManager yamlManager;
    private final LogManager logger;
    private Map<UUID, Set<String>> playerDisabledTypes;
    private Map<UUID, String> playerPreferences;
    private Map<UUID, Map<String, String>> playerPreferenceMap;  // New field for structured preferences

    public AnnouncePreferences(JavaPlugin plugin, DataStoreManager dataManager) {
        this.plugin = plugin;
        this.dataManager = dataManager;
        this.yamlManager = new YAMLManager(plugin);
        this.logger = LogManager.getInstance(plugin, "AnnouncePreferences");
        this.playerDisabledTypes = new HashMap<>();
        this.playerPreferences = new HashMap<>();
        this.playerPreferenceMap = new HashMap<>();
        loadPreferences();
    }

    public void loadPreferences() {
        if (dataManager != null && dataManager.isInitialized()) {
            // Load from database
            playerDisabledTypes = dataManager.getAllPlayerDisabledTypes();
            
            // Load new structured preferences
            playerPreferenceMap.clear();
            for (UUID playerId : playerDisabledTypes.keySet()) {
                Map<String, String> prefs = dataManager.getPlayerPreferences(playerId);
                if (!prefs.isEmpty()) {
                    playerPreferenceMap.put(playerId, prefs);
                    
                    // Handle legacy preference conversion
                    if (prefs.containsKey("legacy")) {
                        playerPreferences.put(playerId, prefs.get("legacy"));
                    }
                }
            }
            
            // Sync to YML backup
            syncToYAML();
            logger.debug("Loaded preferences from database");
            return;
        }

        // Fallback to YML if no database
        loadFromYAML();
        logger.debug("Loaded preferences from YAML");
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
        // Convert structured preferences to legacy format for YAML backup
        Map<UUID, String> legacyPrefs = new HashMap<>();
        for (Map.Entry<UUID, Map<String, String>> entry : playerPreferenceMap.entrySet()) {
            if (entry.getValue().containsKey("legacy")) {
                legacyPrefs.put(entry.getKey(), entry.getValue().get("legacy"));
            }
        }
        yamlManager.savePreferences(playerDisabledTypes, legacyPrefs);
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

    // Structured preference methods (replacement for legacy string-based preferences)
    public void setPreference(UUID playerId, String property, String value) {
        // Update structured preferences
        playerPreferenceMap
            .computeIfAbsent(playerId, k -> new HashMap<>())
            .put(property, value);

        // Update database if available
        if (dataManager != null && dataManager.isInitialized()) {
            dataManager.getDataStore().setPlayerPreference(playerId, property, value);
        }

        syncToYAML();
    }

    public String getPreference(UUID playerId, String property) {
        // First try structured preferences
        Map<String, String> prefs = playerPreferenceMap.get(playerId);
        if (prefs != null && prefs.containsKey(property)) {
            return prefs.get(property);
        }

        // Try database if available
        if (dataManager != null && dataManager.isInitialized()) {
            String value = dataManager.getDataStore().getPlayerPreference(playerId, property);
            if (value != null) {
                // Cache the value
                playerPreferenceMap
                    .computeIfAbsent(playerId, k -> new HashMap<>())
                    .put(property, value);
                return value;
            }
        }

        // Return default value from PreferenceProperty
        return PreferenceProperty.fromKey(property).getDefaultValue();
    }

    public Map<String, String> getAllPreferences(UUID playerId) {
        if (dataManager != null && dataManager.isInitialized()) {
            Map<String, String> prefs = dataManager.getPlayerPreferences(playerId);
            if (!prefs.isEmpty()) {
                // Update cache
                playerPreferenceMap.put(playerId, new HashMap<>(prefs));
                return prefs;
            }
        }
        return playerPreferenceMap.getOrDefault(playerId, new HashMap<>());
    }

    public void deletePreference(UUID playerId, String property) {
        // Remove from structured preferences
        Map<String, String> prefs = playerPreferenceMap.get(playerId);
        if (prefs != null) {
            prefs.remove(property);
        }

        // Remove from database if available
        if (dataManager != null && dataManager.isInitialized()) {
            dataManager.getDataStore().deletePlayerPreference(playerId, property);
        }
        syncToYAML();
    }
}