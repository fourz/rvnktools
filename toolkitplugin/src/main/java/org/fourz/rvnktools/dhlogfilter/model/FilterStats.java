package org.fourz.rvnktools.dhlogfilter.model;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Statistics and performance metrics for the DH log filter system.
 * Provides insight into filtering effectiveness and system performance impact.
 * 
 * Thread-safe implementation using atomic operations for concurrent access.
 * 
 * @since 1.1-alpha
 */
public class FilterStats {
    
    private final AtomicLong messagesProcessed = new AtomicLong(0);
    private final AtomicLong messagesFiltered = new AtomicLong(0);
    private final AtomicLong messagesAllowed = new AtomicLong(0);
    private final AtomicLong rateLimitedMessages = new AtomicLong(0);
    private final AtomicLong totalProcessingTimeNanos = new AtomicLong(0);
    private final LocalDateTime startTime;
    private volatile String currentLogLevel = "INFO";
    private volatile boolean filterActive = false;
    private volatile long cacheSize = 0;
    
    /**
     * Create a new FilterStats instance with current timestamp.
     */
    public FilterStats() {
        this.startTime = LocalDateTime.now();
    }
    
    /**
     * Record a processed message.
     * 
     * @param processingTimeNanos Time taken to process the message in nanoseconds
     */
    public void recordProcessedMessage(long processingTimeNanos) {
        messagesProcessed.incrementAndGet();
        totalProcessingTimeNanos.addAndGet(processingTimeNanos);
    }
    
    /**
     * Record a filtered (blocked) message.
     */
    public void recordFilteredMessage() {
        messagesFiltered.incrementAndGet();
    }
    
    /**
     * Record an allowed (passed through) message.
     */
    public void recordAllowedMessage() {
        messagesAllowed.incrementAndGet();
    }
    
    /**
     * Record a message that was rate limited.
     */
    public void recordRateLimitedMessage() {
        rateLimitedMessages.incrementAndGet();
    }
    
    /**
     * Get the total number of messages processed by the filter.
     * 
     * @return Total messages processed
     */
    public long getMessagesProcessed() {
        return messagesProcessed.get();
    }
    
    /**
     * Get the number of messages that were filtered out.
     * 
     * @return Messages filtered
     */
    public long getMessagesFiltered() {
        return messagesFiltered.get();
    }
    
    /**
     * Get the number of messages that were allowed through.
     * 
     * @return Messages allowed
     */
    public long getMessagesAllowed() {
        return messagesAllowed.get();
    }
    
    /**
     * Get the number of messages that were rate limited.
     * 
     * @return Rate limited messages
     */
    public long getRateLimitedMessages() {
        return rateLimitedMessages.get();
    }
    
    /**
     * Get the filtering efficiency as a percentage.
     * 
     * @return Percentage of messages filtered (0.0 to 100.0)
     */
    public double getFilteringEfficiency() {
        long total = messagesProcessed.get();
        if (total == 0) {
            return 0.0;
        }
        return (messagesFiltered.get() * 100.0) / total;
    }
    
    /**
     * Get the average processing time per message in milliseconds.
     * 
     * @return Average processing time in milliseconds
     */
    public double getAverageProcessingTimeMs() {
        long total = messagesProcessed.get();
        if (total == 0) {
            return 0.0;
        }
        return (totalProcessingTimeNanos.get() / 1_000_000.0) / total;
    }
    
    /**
     * Get the total processing time in milliseconds.
     * 
     * @return Total processing time in milliseconds
     */
    public double getTotalProcessingTimeMs() {
        return totalProcessingTimeNanos.get() / 1_000_000.0;
    }
    
    /**
     * Get the time when statistics collection started.
     * 
     * @return Start time
     */
    public LocalDateTime getStartTime() {
        return startTime;
    }
    
    /**
     * Get the current log level setting.
     * 
     * @return Current log level
     */
    public String getCurrentLogLevel() {
        return currentLogLevel;
    }
    
    /**
     * Set the current log level.
     * 
     * @param currentLogLevel The log level to set
     */
    public void setCurrentLogLevel(String currentLogLevel) {
        this.currentLogLevel = currentLogLevel;
    }
    
    /**
     * Check if the filter is currently active.
     * 
     * @return true if filter is active
     */
    public boolean isFilterActive() {
        return filterActive;
    }
    
    /**
     * Set the filter active status.
     * 
     * @param filterActive true to mark filter as active
     */
    public void setFilterActive(boolean filterActive) {
        this.filterActive = filterActive;
    }
    
    /**
     * Get the current size of the message cache.
     * 
     * @return Cache size
     */
    public long getCacheSize() {
        return cacheSize;
    }
    
    /**
     * Set the current cache size.
     * 
     * @param cacheSize The cache size to set
     */
    public void setCacheSize(long cacheSize) {
        this.cacheSize = cacheSize;
    }
    
    /**
     * Reset all statistics to zero.
     * This does not change the start time.
     */
    public void reset() {
        messagesProcessed.set(0);
        messagesFiltered.set(0);
        messagesAllowed.set(0);
        rateLimitedMessages.set(0);
        totalProcessingTimeNanos.set(0);
        cacheSize = 0;
    }
    
    @Override
    public String toString() {
        return String.format(
            "FilterStats{processed=%d, filtered=%d, allowed=%d, rateLimited=%d, " +
            "efficiency=%.1f%%, avgTime=%.3fms, active=%s, level=%s}",
            getMessagesProcessed(),
            getMessagesFiltered(),
            getMessagesAllowed(),
            getRateLimitedMessages(),
            getFilteringEfficiency(),
            getAverageProcessingTimeMs(),
            isFilterActive(),
            getCurrentLogLevel()
        );
    }
}