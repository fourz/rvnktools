package org.fourz.rvnktools.announceManager.preferences;

import org.bukkit.plugin.java.JavaPlugin;
import org.fourz.rvnktools.announceManager.data.DataStoreManager;
import org.fourz.rvnktools.announceManager.data.YAMLManager;
import org.fourz.rvnktools.announceManager.integration.PreferencesServiceAdapter;
import org.fourz.rvnktools.announceManager.integration.PreferencesServiceLookup;
import org.fourz.rvnkcore.util.log.LogManager;

import java.util.*;
import java.util.concurrent.CompletableFuture;

public class AnnouncePreferences {
    private final JavaPlugin plugin;
    private final DataStoreManager dataManager;
    private final YAMLManager yamlManager;
    private final LogManager logger;
    private Map<UUID, Set<String>> playerDisabledTypes;
    private Map<UUID, String> playerPreferences;
    private Map<UUID, Map<String, String>> playerPreferenceMap;  // New field for structured preferences

    // PlayerPreferencesService integration
    private PreferencesServiceLookup serviceLookup;
    private PreferencesServiceAdapter adapter;

    public AnnouncePreferences(JavaPlugin plugin, DataStoreManager dataManager) {
        this.plugin = plugin;
        this.dataManager = dataManager;
        this.yamlManager = new YAMLManager(plugin);
        this.logger = LogManager.getInstance(plugin, "AnnouncePreferences");
        this.playerDisabledTypes = new HashMap<>();
        this.playerPreferences = new HashMap<>();
        this.playerPreferenceMap = new HashMap<>();

        // Initialize service integration
        this.serviceLookup = new PreferencesServiceLookup(plugin);
        if (serviceLookup.isAvailable()) {
            this.adapter = new PreferencesServiceAdapter(serviceLookup.getService());
        }

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

    // ========== ASYNC METHODS (NEW) ==========

    /**
     * Get a preference asynchronously using PlayerPreferencesService if available
     *
     * @param playerId The player's UUID
     * @param property The preference property key
     * @return CompletableFuture with preference value
     */
    public CompletableFuture<String> getPreferenceAsync(UUID playerId, String property) {
        // Use service if available
        if (serviceLookup != null && serviceLookup.isAvailable()) {
            return adapter.getPreference(playerId, property)
                .exceptionally(ex -> {
                    logger.warning("Failed to get preference from service, falling back to DataStore: " + ex.getMessage());
                    return getPreferenceSync(playerId, property);
                });
        }

        // Fallback to synchronous DataStore access
        return CompletableFuture.completedFuture(getPreferenceSync(playerId, property));
    }

    /**
     * Set a preference asynchronously using PlayerPreferencesService if available
     *
     * @param playerId The player's UUID
     * @param property The preference property key
     * @param value The preference value
     * @return CompletableFuture that completes when saved
     */
    public CompletableFuture<Void> setPreferenceAsync(UUID playerId, String property, String value) {
        // Update in-memory cache synchronously
        playerPreferenceMap
            .computeIfAbsent(playerId, k -> new HashMap<>())
            .put(property, value);

        // Use service if available
        if (serviceLookup != null && serviceLookup.isAvailable()) {
            return adapter.setPreference(playerId, property, value)
                .exceptionally(ex -> {
                    logger.warning("Failed to set preference in service, falling back to DataStore: " + ex.getMessage());
                    // Fallback to DataStore
                    if (dataManager != null && dataManager.isInitialized()) {
                        dataManager.getDataStore().setPlayerPreference(playerId, property, value);
                    }
                    syncToYAML();
                    return null;
                });
        }

        // Fallback to DataStore
        if (dataManager != null && dataManager.isInitialized()) {
            dataManager.getDataStore().setPlayerPreference(playerId, property, value);
        }
        syncToYAML();
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Get disabled types asynchronously
     *
     * @param playerId The player's UUID
     * @return CompletableFuture with set of disabled types
     */
    public CompletableFuture<Set<String>> getDisabledTypesAsync(UUID playerId) {
        // Use service if available
        if (serviceLookup != null && serviceLookup.isAvailable()) {
            return adapter.getDisabledTypes(playerId)
                .exceptionally(ex -> {
                    logger.warning("Failed to get disabled types from service, using cache: " + ex.getMessage());
                    return getDisabledTypes(playerId);
                });
        }

        // Fallback to cache
        return CompletableFuture.completedFuture(getDisabledTypes(playerId));
    }

    /**
     * Add a disabled type asynchronously
     *
     * @param playerId The player's UUID
     * @param type The notification type
     * @return CompletableFuture that completes when saved
     */
    public CompletableFuture<Void> addDisabledTypeAsync(UUID playerId, String type) {
        // Update in-memory cache
        playerDisabledTypes.computeIfAbsent(playerId, k -> new HashSet<>()).add(type);

        // Use service if available
        if (serviceLookup != null && serviceLookup.isAvailable()) {
            return adapter.addDisabledType(playerId, type)
                .exceptionally(ex -> {
                    logger.warning("Failed to add disabled type in service, falling back to DataStore: " + ex.getMessage());
                    // Fallback to DataStore
                    if (dataManager != null && dataManager.isInitialized()) {
                        dataManager.savePlayerDisabledType(playerId, type);
                    }
                    syncToYAML();
                    return null;
                });
        }

        // Fallback to DataStore
        if (dataManager != null && dataManager.isInitialized()) {
            dataManager.savePlayerDisabledType(playerId, type);
        }
        syncToYAML();
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Remove a disabled type asynchronously
     *
     * @param playerId The player's UUID
     * @param type The notification type
     * @return CompletableFuture that completes when saved
     */
    public CompletableFuture<Void> removeDisabledTypeAsync(UUID playerId, String type) {
        // Update in-memory cache
        Set<String> types = playerDisabledTypes.get(playerId);
        if (types != null) {
            types.remove(type);
        }

        // Use service if available
        if (serviceLookup != null && serviceLookup.isAvailable()) {
            return adapter.removeDisabledType(playerId, type)
                .exceptionally(ex -> {
                    logger.warning("Failed to remove disabled type in service, falling back to DataStore: " + ex.getMessage());
                    // Fallback to DataStore
                    if (dataManager != null && dataManager.isInitialized()) {
                        dataManager.removePlayerDisabledType(playerId, type);
                    }
                    syncToYAML();
                    return null;
                });
        }

        // Fallback to DataStore
        if (dataManager != null && dataManager.isInitialized()) {
            dataManager.removePlayerDisabledType(playerId, type);
        }
        syncToYAML();
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Helper method: Get preference synchronously from DataStore
     */
    private String getPreferenceSync(UUID playerId, String property) {
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
}