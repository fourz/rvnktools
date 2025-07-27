package org.fourz.rvnktools.dhlogfilter;

import java.util.concurrent.CompletableFuture;

/**
 * Service interface for managing DH (Distant Horizons) log filtering.
 * 
 * This service provides centralized management of log filtering capabilities
 * to reduce console spam from repetitive messages while maintaining visibility
 * of important warnings and errors.
 * 
 * @since 1.1-alpha
 */
public interface DHLogFilterService {
    
    /**
     * Applies the log filter with current configuration settings.
     * 
     * @return CompletableFuture that completes when filter is applied
     * @since 1.1-alpha
     */
    CompletableFuture<Void> applyFilter();
    
    /**
     * Removes the log filter and restores normal logging.
     * 
     * @return CompletableFuture that completes when filter is removed
     * @since 1.1-alpha
     */
    CompletableFuture<Void> removeFilter();
    
    /**
     * Reloads the configuration and applies updated settings.
     * 
     * @return CompletableFuture containing true if reload was successful
     * @since 1.1-alpha
     */
    CompletableFuture<Boolean> reloadConfiguration();
    
    /**
     * Retrieves current filter statistics and performance metrics.
     * 
     * @return CompletableFuture containing filter statistics
     * @since 1.1-alpha
     */
    CompletableFuture<DHLogFilterStats> getFilterStats();
    
    /**
     * Updates the log level temporarily without persisting to configuration.
     * 
     * @param level The new log level to apply
     * @return CompletableFuture that completes when level is updated
     * @since 1.1-alpha
     */
    CompletableFuture<Void> setLogLevel(DHLogLevel level);
    
    /**
     * Checks if the filter is currently active.
     * 
     * @return CompletableFuture containing true if filter is active
     * @since 1.1-alpha
     */
    CompletableFuture<Boolean> isFilterActive();
}