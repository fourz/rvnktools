package org.fourz.rvnktools.dhlogfilter.repository;

import org.fourz.rvnktools.dhlogfilter.model.DHLogFilterConfig;
import java.util.concurrent.CompletableFuture;

/**
 * Repository interface for managing DH log filter configuration.
 * Handles asynchronous loading and saving of configuration data from/to storage.
 * 
 * @since 1.1-alpha
 */
public interface DHLogFilterConfigRepository {
    
    /**
     * Load the configuration from storage.
     * If no configuration exists, returns a default configuration.
     * 
     * @return CompletableFuture containing the loaded configuration
     * @since 1.1-alpha
     */
    CompletableFuture<DHLogFilterConfig> loadConfiguration();
    
    /**
     * Save the configuration to storage.
     * Creates the configuration file if it doesn't exist.
     * 
     * @param config The configuration to save
     * @return CompletableFuture that completes when the configuration is saved
     * @since 1.1-alpha
     */
    CompletableFuture<Void> saveConfiguration(DHLogFilterConfig config);
    
    /**
     * Check if a configuration file exists in storage.
     * 
     * @return CompletableFuture containing true if configuration exists
     * @since 1.1-alpha
     */
    CompletableFuture<Boolean> configurationExists();
    
    /**
     * Create a default configuration file in storage.
     * This will overwrite any existing configuration.
     * 
     * @return CompletableFuture that completes when the default configuration is created
     * @since 1.1-alpha
     */
    CompletableFuture<Void> createDefaultConfiguration();
    
    /**
     * Backup the current configuration to a backup file.
     * Useful for preserving settings during updates or migrations.
     * 
     * @return CompletableFuture that completes when the backup is created
     * @since 1.1-alpha
     */
    CompletableFuture<Void> backupConfiguration();
}