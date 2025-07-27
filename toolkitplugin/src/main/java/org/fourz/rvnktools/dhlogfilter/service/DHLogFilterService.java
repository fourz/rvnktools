package org.fourz.rvnktools.dhlogfilter.service;

import org.fourz.rvnktools.dhlogfilter.model.FilterStats;
import java.util.concurrent.CompletableFuture;

/**
 * Service interface for managing DH log filtering functionality.
 * Provides centralized control over log filtering operations with asynchronous patterns.
 * 
 * This service handles the core business logic for filtering repetitive Distant Horizons
 * server plugin messages to reduce console spam while maintaining visibility of
 * important warnings and errors.
 * 
 * @since 1.1-alpha
 */
public interface DHLogFilterService {
    
    /**
     * Apply the log filter to the server's logging system.
     * This will start intercepting and filtering log messages based on the current configuration.
     * 
     * @return CompletableFuture that completes when the filter is successfully applied
     * @throws IllegalStateException if the filter is already active
     * @since 1.1-alpha
     */
    CompletableFuture<Void> applyFilter();
    
    /**
     * Remove the log filter from the server's logging system.
     * This will stop intercepting log messages and restore normal logging behavior.
     * 
     * @return CompletableFuture that completes when the filter is successfully removed
     * @throws IllegalStateException if the filter is not currently active
     * @since 1.1-alpha
     */
    CompletableFuture<Void> removeFilter();
    
    /**
     * Reload the configuration from disk and apply the new settings.
     * This operation will temporarily disable filtering while reloading to ensure
     * no messages are lost during the transition.
     * 
     * @return CompletableFuture that completes with true if reload was successful
     * @since 1.1-alpha
     */
    CompletableFuture<Boolean> reloadConfiguration();
    
    /**
     * Get current filtering statistics and performance metrics.
     * Provides insights into how many messages have been filtered, performance impact,
     * and other operational metrics.
     * 
     * @return CompletableFuture containing current filter statistics
     * @since 1.1-alpha
     */
    CompletableFuture<FilterStats> getFilterStats();
    
    /**
     * Temporarily change the log level for the current session.
     * This change will not persist across server restarts unless explicitly saved.
     * 
     * @param level The new log level (DEBUG, INFO, WARN, ERROR)
     * @return CompletableFuture that completes when the level change is applied
     * @throws IllegalArgumentException if the log level is invalid
     * @since 1.1-alpha
     */
    CompletableFuture<Void> setLogLevel(String level);
    
    /**
     * Get the current log level setting.
     * 
     * @return CompletableFuture containing the current log level
     * @since 1.1-alpha
     */
    CompletableFuture<String> getCurrentLogLevel();
    
    /**
     * Check if the log filter is currently active.
     * 
     * @return CompletableFuture containing true if the filter is active
     * @since 1.1-alpha
     */
    CompletableFuture<Boolean> isFilterActive();
    
    /**
     * Clear the message cache used for rate limiting.
     * This will allow previously filtered messages to be displayed again.
     * 
     * @return CompletableFuture that completes when the cache is cleared
     * @since 1.1-alpha
     */
    CompletableFuture<Void> clearMessageCache();
}