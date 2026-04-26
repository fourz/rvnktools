package org.fourz.rvnktools.announceManager.preferences;

import org.bukkit.plugin.java.JavaPlugin;
import org.fourz.rvnktools.announceManager.integration.PreferencesServiceAdapter;
import org.fourz.rvnktools.announceManager.integration.PreferencesServiceLookup;
import org.fourz.rvnkcore.util.log.LogManager;

import java.util.*;
import java.util.concurrent.CompletableFuture;

public class AnnouncePreferences {
    private final JavaPlugin plugin;
    private final LogManager logger;
    private Map<UUID, Set<String>> playerDisabledTypes;
    private Map<UUID, Map<String, String>> playerPreferenceMap;

    // PlayerPreferencesService integration
    private PreferencesServiceLookup serviceLookup;
    private PreferencesServiceAdapter adapter;

    public AnnouncePreferences(JavaPlugin plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, "AnnouncePreferences");
        this.playerDisabledTypes = new HashMap<>();
        this.playerPreferenceMap = new HashMap<>();

        // Initialize service integration
        this.serviceLookup = new PreferencesServiceLookup(plugin);
        if (serviceLookup.isAvailable()) {
            this.adapter = new PreferencesServiceAdapter(serviceLookup.getService());
            logger.info("AnnouncePreferences connected to PlayerPreferencesService");
        } else {
            logger.warning("PlayerPreferencesService unavailable — preferences will use in-memory only");
        }

        loadPreferences();
    }

    public void loadPreferences() {
        if (adapter == null) {
            logger.debug("No adapter available — starting with empty preferences");
            return;
        }

        // Preferences are loaded on-demand via getPreference/getDisabledTypes
        logger.debug("Preferences will be loaded on-demand via PreferencesServiceAdapter");
    }

    public void savePreferences() {
        // Preferences are written through to PreferencesServiceAdapter on each change
        // No bulk save needed
        logger.debug("Preferences are persisted via PreferencesServiceAdapter (no bulk save needed)");
    }

    public void addDisabledType(UUID playerId, String type) {
        playerDisabledTypes.computeIfAbsent(playerId, k -> new HashSet<>()).add(type);

        if (adapter != null) {
            adapter.addDisabledType(playerId, type)
                .exceptionally(ex -> {
                    logger.warning("Failed to persist disabled type for " + playerId + ": " + ex.getMessage());
                    return null;
                });
        }
    }

    public void removeDisabledType(UUID playerId, String type) {
        Set<String> types = playerDisabledTypes.get(playerId);
        if (types != null) {
            types.remove(type);
        }

        if (adapter != null) {
            adapter.removeDisabledType(playerId, type)
                .exceptionally(ex -> {
                    logger.warning("Failed to remove disabled type for " + playerId + ": " + ex.getMessage());
                    return null;
                });
        }
    }

    public Set<String> getDisabledTypes(UUID playerId) {
        return playerDisabledTypes.getOrDefault(playerId, new HashSet<>());
    }

    public Map<UUID, Set<String>> getAllDisabledTypes() {
        return new HashMap<>(playerDisabledTypes);
    }

    public void setPreference(UUID playerId, String property, String value) {
        playerPreferenceMap
            .computeIfAbsent(playerId, k -> new HashMap<>())
            .put(property, value);

        if (adapter != null) {
            adapter.setPreference(playerId, property, value)
                .exceptionally(ex -> {
                    logger.warning("Failed to persist preference for " + playerId + ": " + ex.getMessage());
                    return null;
                });
        }
    }

    public String getPreference(UUID playerId, String property) {
        // Check in-memory cache first
        Map<String, String> prefs = playerPreferenceMap.get(playerId);
        if (prefs != null && prefs.containsKey(property)) {
            return prefs.get(property);
        }

        // Try adapter if available
        if (adapter != null) {
            try {
                String value = adapter.getPreference(playerId, property).join();
                if (value != null) {
                    playerPreferenceMap
                        .computeIfAbsent(playerId, k -> new HashMap<>())
                        .put(property, value);
                    return value;
                }
            } catch (Exception e) {
                logger.warning("Failed to get preference from service: " + e.getMessage());
            }
        }

        PreferenceProperty prop = PreferenceProperty.fromKey(property);
        return prop != null ? prop.getDefaultValue() : null;
    }

    public Map<String, String> getAllPreferences(UUID playerId) {
        return playerPreferenceMap.getOrDefault(playerId, new HashMap<>());
    }

    public void deletePreference(UUID playerId, String property) {
        Map<String, String> prefs = playerPreferenceMap.get(playerId);
        if (prefs != null) {
            prefs.remove(property);
        }

        if (adapter != null) {
            adapter.setPreference(playerId, property, null)
                .exceptionally(ex -> {
                    logger.warning("Failed to delete preference for " + playerId + ": " + ex.getMessage());
                    return null;
                });
        }
    }

    // ========== ASYNC METHODS ==========

    public CompletableFuture<String> getPreferenceAsync(UUID playerId, String property) {
        if (adapter != null) {
            return adapter.getPreference(playerId, property)
                .exceptionally(ex -> {
                    logger.warning("Failed to get preference from service: " + ex.getMessage());
                    return getPreferenceFromCache(playerId, property);
                });
        }

        return CompletableFuture.completedFuture(getPreferenceFromCache(playerId, property));
    }

    public CompletableFuture<Void> setPreferenceAsync(UUID playerId, String property, String value) {
        playerPreferenceMap
            .computeIfAbsent(playerId, k -> new HashMap<>())
            .put(property, value);

        if (adapter != null) {
            return adapter.setPreference(playerId, property, value)
                .exceptionally(ex -> {
                    logger.warning("Failed to set preference in service: " + ex.getMessage());
                    return null;
                });
        }

        return CompletableFuture.completedFuture(null);
    }

    public CompletableFuture<Set<String>> getDisabledTypesAsync(UUID playerId) {
        if (adapter != null) {
            return adapter.getDisabledTypes(playerId)
                .exceptionally(ex -> {
                    logger.warning("Failed to get disabled types from service: " + ex.getMessage());
                    return getDisabledTypes(playerId);
                });
        }

        return CompletableFuture.completedFuture(getDisabledTypes(playerId));
    }

    public CompletableFuture<Void> addDisabledTypeAsync(UUID playerId, String type) {
        playerDisabledTypes.computeIfAbsent(playerId, k -> new HashSet<>()).add(type);

        if (adapter != null) {
            return adapter.addDisabledType(playerId, type)
                .exceptionally(ex -> {
                    logger.warning("Failed to add disabled type in service: " + ex.getMessage());
                    return null;
                });
        }

        return CompletableFuture.completedFuture(null);
    }

    public CompletableFuture<Void> removeDisabledTypeAsync(UUID playerId, String type) {
        Set<String> types = playerDisabledTypes.get(playerId);
        if (types != null) {
            types.remove(type);
        }

        if (adapter != null) {
            return adapter.removeDisabledType(playerId, type)
                .exceptionally(ex -> {
                    logger.warning("Failed to remove disabled type in service: " + ex.getMessage());
                    return null;
                });
        }

        return CompletableFuture.completedFuture(null);
    }

    private String getPreferenceFromCache(UUID playerId, String property) {
        Map<String, String> prefs = playerPreferenceMap.get(playerId);
        if (prefs != null && prefs.containsKey(property)) {
            return prefs.get(property);
        }
        PreferenceProperty prop = PreferenceProperty.fromKey(property);
        return prop != null ? prop.getDefaultValue() : null;
    }
}
