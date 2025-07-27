package org.fourz.rvnktools.dhlogfilter;

import java.time.LocalDateTime;

/**
 * Statistics and performance metrics for the DH log filter.
 * 
 * Tracks filtering effectiveness and performance impact to help with
 * monitoring and optimization.
 */
public class DHLogFilterStats {
    
    private final boolean filterActive;
    private final DHLogLevel currentLevel;
    private final long messagesFiltered;
    private final long messagesAllowed;
    private final LocalDateTime filterStartTime;
    private final LocalDateTime lastStatsReset;
    private final int keywordRulesActive;
    private final long cacheSize;
    private final double averageProcessingTimeMs;
    
    /**
     * Constructor for DHLogFilterStats.
     * 
     * @param filterActive Whether the filter is currently active
     * @param currentLevel The current log level setting
     * @param messagesFiltered Total number of messages filtered out
     * @param messagesAllowed Total number of messages allowed through
     * @param filterStartTime When the filter was first activated
     * @param lastStatsReset When statistics were last reset
     * @param keywordRulesActive Number of active keyword filtering rules
     * @param cacheSize Current size of the rate limiting cache
     * @param averageProcessingTimeMs Average processing time per message in milliseconds
     */
    public DHLogFilterStats(boolean filterActive, DHLogLevel currentLevel, 
                           long messagesFiltered, long messagesAllowed,
                           LocalDateTime filterStartTime, LocalDateTime lastStatsReset,
                           int keywordRulesActive, long cacheSize, 
                           double averageProcessingTimeMs) {
        this.filterActive = filterActive;
        this.currentLevel = currentLevel;
        this.messagesFiltered = messagesFiltered;
        this.messagesAllowed = messagesAllowed;
        this.filterStartTime = filterStartTime;
        this.lastStatsReset = lastStatsReset;
        this.keywordRulesActive = keywordRulesActive;
        this.cacheSize = cacheSize;
        this.averageProcessingTimeMs = averageProcessingTimeMs;
    }
    
    /**
     * Checks if the filter is currently active.
     * 
     * @return true if filter is active
     */
    public boolean isFilterActive() {
        return filterActive;
    }
    
    /**
     * Gets the current log level setting.
     * 
     * @return The current log level
     */
    public DHLogLevel getCurrentLevel() {
        return currentLevel;
    }
    
    /**
     * Gets the total number of messages filtered out.
     * 
     * @return Number of filtered messages
     */
    public long getMessagesFiltered() {
        return messagesFiltered;
    }
    
    /**
     * Gets the total number of messages allowed through.
     * 
     * @return Number of allowed messages
     */
    public long getMessagesAllowed() {
        return messagesAllowed;
    }
    
    /**
     * Gets when the filter was first activated.
     * 
     * @return Filter start time
     */
    public LocalDateTime getFilterStartTime() {
        return filterStartTime;
    }
    
    /**
     * Gets when statistics were last reset.
     * 
     * @return Last stats reset time
     */
    public LocalDateTime getLastStatsReset() {
        return lastStatsReset;
    }
    
    /**
     * Gets the number of active keyword filtering rules.
     * 
     * @return Number of active keyword rules
     */
    public int getKeywordRulesActive() {
        return keywordRulesActive;
    }
    
    /**
     * Gets the current size of the rate limiting cache.
     * 
     * @return Cache size
     */
    public long getCacheSize() {
        return cacheSize;
    }
    
    /**
     * Gets the average processing time per message.
     * 
     * @return Average processing time in milliseconds
     */
    public double getAverageProcessingTimeMs() {
        return averageProcessingTimeMs;
    }
    
    /**
     * Calculates the total number of messages processed.
     * 
     * @return Total messages processed
     */
    public long getTotalMessages() {
        return messagesFiltered + messagesAllowed;
    }
    
    /**
     * Calculates the filtering efficiency as a percentage.
     * 
     * @return Filtering efficiency (0.0 to 100.0)
     */
    public double getFilteringEfficiency() {
        long total = getTotalMessages();
        if (total == 0) {
            return 0.0;
        }
        return (double) messagesFiltered / total * 100.0;
    }
}