package org.fourz.rvnktools.announceManager.migration;

import org.fourz.rvnkcore.api.service.PlayerPreferencesService;
import org.fourz.rvnktools.announceManager.data.DataStoreManager;
import org.fourz.rvnktools.announceManager.preferences.PreferenceProperty;
import org.fourz.rvnkcore.util.log.LogManager;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * One-time migration of preferences from DataStore to PlayerPreferencesService.
 * Runs on first startup with config flag to prevent re-execution.
 */
public class PreferencesMigration {
    private static final String MIGRATION_FLAG = "preferences.migration-complete";
    private final Plugin plugin;
    private final LogManager logger;
    private final DataStoreManager dataStore;
    private final PlayerPreferencesService prefService;
    private final FileConfiguration config;

    public PreferencesMigration(Plugin plugin, DataStoreManager dataStore,
                                PlayerPreferencesService prefService,
                                FileConfiguration config) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, "PreferencesMigration");
        this.dataStore = dataStore;
        this.prefService = prefService;
        this.config = config;
    }

    /**
     * Perform the migration from DataStore to PlayerPreferencesService
     *
     * @return CompletableFuture<Boolean> true if migration succeeded
     */
    public CompletableFuture<Boolean> migrate() {
        // Check if migration already completed
        if (config.getBoolean(MIGRATION_FLAG, false)) {
            logger.info("Preferences migration already completed, skipping");
            return CompletableFuture.completedFuture(true);
        }

        logger.info("Starting preferences migration from DataStore to PlayerPreferencesService");

        return CompletableFuture.supplyAsync(() -> {
            try {
                int migratedCount = 0;
                int preferencesCount = 0;
                int typesCount = 0;

                // Get all players with preferences from DataStore
                Map<UUID, Set<String>> allDisabledTypes = dataStore.getAllPlayerDisabledTypes();

                for (UUID playerId : allDisabledTypes.keySet()) {
                    // Migrate player preferences
                    Map<String, String> prefs = dataStore.getPlayerPreferences(playerId);
                    for (Map.Entry<String, String> entry : prefs.entrySet()) {
                        String key = entry.getKey();
                        String value = entry.getValue();

                        // Map preferences to PlayerPreferencesService
                        if (PreferenceProperty.LOCATION.getKey().equals(key)) {
                            boolean enabled = !"none".equalsIgnoreCase(value);
                            prefService.setChannelEnabled(playerId, "rvnktools", "ANNOUNCEMENT", "CHAT", enabled).join();
                            preferencesCount++;
                        } else if (PreferenceProperty.SOUND.getKey().equals(key)) {
                            boolean enabled = !"none".equalsIgnoreCase(value);
                            prefService.setNotificationEnabled(playerId, "rvnktools", "ANNOUNCEMENT", enabled).join();
                            preferencesCount++;
                        }
                    }

                    // Migrate disabled types
                    Set<String> disabledTypes = allDisabledTypes.get(playerId);
                    for (String type : disabledTypes) {
                        prefService.setNotificationEnabled(playerId, "rvnktools", type, false).join();
                        typesCount++;
                    }

                    migratedCount++;
                }

                logger.info(String.format(
                    "Migrated preferences for %d players (%d preferences, %d disabled types)",
                    migratedCount, preferencesCount, typesCount
                ));

                // Mark migration complete
                config.set(MIGRATION_FLAG, true);
                plugin.saveConfig();

                logger.info("Preferences migration completed successfully");
                return true;
            } catch (Exception e) {
                logger.error("Preferences migration failed", e);
                return false;
            }
        });
    }
}
