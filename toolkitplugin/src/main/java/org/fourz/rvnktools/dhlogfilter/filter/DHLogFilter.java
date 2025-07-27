package org.fourz.rvnktools.dhlogfilter.filter;

import org.fourz.rvnktools.dhlogfilter.model.DHLogFilterConfig;
import org.fourz.rvnktools.dhlogfilter.model.DHLogFilterConfig.LogLevel;
import org.fourz.rvnktools.dhlogfilter.model.FilterStats;

import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Filter;
import java.util.logging.Level;
import java.util.logging.LogRecord;

/**
 * Java Logging API filter implementation for DH log filtering.
 * Integrates with the server's logging system to intercept and filter messages
 * based on configured rules and rate limiting.
 * 
 * Thread-safe implementation using concurrent collections for multi-threaded server environment.
 * 
 * @since 1.1-alpha
 */
public class DHLogFilter implements Filter {
    
    /**
     * Cache entry for rate limiting duplicate messages.
     */
    private static class CacheEntry {
        private final LocalDateTime firstSeen;
        private LocalDateTime lastSeen;
        private int count;
        
        public CacheEntry() {
            this.firstSeen = LocalDateTime.now();
            this.lastSeen = firstSeen;
            this.count = 1;
        }
        
        public void updateLastSeen() {
            this.lastSeen = LocalDateTime.now();
            this.count++;
        }
        
        public boolean isExpired(long rateLimitSeconds) {
            return LocalDateTime.now().minusSeconds(rateLimitSeconds).isAfter(lastSeen);
        }
        
        public LocalDateTime getFirstSeen() {
            return firstSeen;
        }
        
        public LocalDateTime getLastSeen() {
            return lastSeen;
        }
        
        public int getCount() {
            return count;
        }
    }
    
    private volatile DHLogFilterConfig config;
    private volatile FilterStats stats;
    private final ConcurrentMap<String, CacheEntry> messageCache;
    private volatile boolean active = false;
    
    /**
     * Constructor for DHLogFilter.
     * 
     * @param config Initial configuration
     * @param stats Statistics tracker
     */
    public DHLogFilter(DHLogFilterConfig config, FilterStats stats) {
        this.config = config;
        this.stats = stats;
        this.messageCache = new ConcurrentHashMap<>();
    }
    
    @Override
    public boolean isLoggable(LogRecord record) {
        if (!active || !config.isEnabled()) {
            return true; // Allow all messages if filter is not active
        }
        
        long startTime = System.nanoTime();
        boolean result = processLogRecord(record);
        long processingTime = System.nanoTime() - startTime;
        
        stats.recordProcessedMessage(processingTime);
        
        if (result) {
            stats.recordAllowedMessage();
        } else {
            stats.recordFilteredMessage();
        }
        
        return result;
    }
    
    /**
     * Process a log record and determine if it should be allowed.
     * 
     * @param record The log record to process
     * @return true if the message should be logged
     */
    private boolean processLogRecord(LogRecord record) {
        // Check log level filtering
        if (!shouldAllowLevel(record.getLevel())) {
            return false;
        }
        
        String message = record.getMessage();
        if (message == null) {
            return true; // Allow null messages
        }
        
        // Check keyword filtering
        if (!config.shouldFilterMessage(message)) {
            return true; // Message doesn't match filter keywords
        }
        
        // Apply rate limiting for matching messages
        return applyRateLimit(message);
    }
    
    /**
     * Check if a log level should be allowed based on current configuration.
     * 
     * @param level The Java logging level
     * @return true if the level should be allowed
     */
    private boolean shouldAllowLevel(Level level) {
        LogLevel messageLevel = mapJavaLevelToConfigLevel(level);
        return config.getLevel().shouldAllow(messageLevel);
    }
    
    /**
     * Map Java logging levels to configuration log levels.
     * 
     * @param level The Java logging level
     * @return The corresponding configuration log level
     */
    private LogLevel mapJavaLevelToConfigLevel(Level level) {
        if (level.intValue() >= Level.SEVERE.intValue()) {
            return LogLevel.ERROR;
        } else if (level.intValue() >= Level.WARNING.intValue()) {
            return LogLevel.WARN;
        } else if (level.intValue() >= Level.INFO.intValue()) {
            return LogLevel.INFO;
        } else {
            return LogLevel.DEBUG;
        }
    }
    
    /**
     * Apply rate limiting to a message.
     * 
     * @param message The message to rate limit
     * @return true if the message should be allowed through
     */
    private boolean applyRateLimit(String message) {
        String cacheKey = generateCacheKey(message);
        CacheEntry entry = messageCache.get(cacheKey);
        
        if (entry == null) {
            // First time seeing this message
            messageCache.put(cacheKey, new CacheEntry());
            cleanupExpiredEntries();
            return true;
        }
        
        if (entry.isExpired(config.getRateLimitSeconds())) {
            // Entry has expired, allow the message and update timestamp
            entry.updateLastSeen();
            return true;
        }
        
        // Message is within rate limit window, filter it
        entry.updateLastSeen();
        stats.recordRateLimitedMessage();
        return false;
    }
    
    /**
     * Generate a cache key for a message.
     * This normalizes the message for rate limiting purposes.
     * 
     * @param message The message to generate a key for
     * @return A normalized cache key
     */
    private String generateCacheKey(String message) {
        // Remove timestamps and variable parts to group similar messages
        String normalized = message
            .replaceAll("\\d{2}:\\d{2}:\\d{2}", "XX:XX:XX") // Remove timestamps
            .replaceAll("\\[\\d+\\]", "[X]") // Remove thread IDs
            .trim();
        
        // Limit key length to prevent memory issues
        if (normalized.length() > 200) {
            normalized = normalized.substring(0, 200);
        }
        
        return normalized;
    }
    
    /**
     * Clean up expired entries from the message cache.
     * This is called periodically to prevent memory leaks.
     */
    private void cleanupExpiredEntries() {
        if (messageCache.size() <= config.getMaxCacheSize()) {
            return; // No cleanup needed yet
        }
        
        long rateLimitSeconds = config.getRateLimitSeconds();
        messageCache.entrySet().removeIf(entry -> entry.getValue().isExpired(rateLimitSeconds));
        
        // Update stats with current cache size
        stats.setCacheSize(messageCache.size());
    }
    
    /**
     * Update the filter configuration.
     * 
     * @param newConfig The new configuration to apply
     */
    public void updateConfiguration(DHLogFilterConfig newConfig) {
        this.config = newConfig;
        
        // Clear cache if rate limit settings changed significantly
        if (messageCache.size() > newConfig.getMaxCacheSize()) {
            clearCache();
        }
    }
    
    /**
     * Update the statistics tracker.
     * 
     * @param newStats The new statistics tracker
     */
    public void updateStats(FilterStats newStats) {
        this.stats = newStats;
    }
    
    /**
     * Activate the filter to start processing messages.
     */
    public void activate() {
        this.active = true;
        stats.setFilterActive(true);
    }
    
    /**
     * Deactivate the filter to stop processing messages.
     */
    public void deactivate() {
        this.active = false;
        stats.setFilterActive(false);
    }
    
    /**
     * Check if the filter is currently active.
     * 
     * @return true if the filter is active
     */
    public boolean isActive() {
        return active;
    }
    
    /**
     * Clear the message cache.
     */
    public void clearCache() {
        messageCache.clear();
        stats.setCacheSize(0);
    }
    
    /**
     * Get the current size of the message cache.
     * 
     * @return Current cache size
     */
    public int getCacheSize() {
        return messageCache.size();
    }
    
    /**
     * Get debug information about the cache contents.
     * This is useful for troubleshooting and monitoring.
     * 
     * @return Debug information string
     */
    public String getCacheDebugInfo() {
        StringBuilder info = new StringBuilder();
        info.append("DHLogFilter Cache Debug Info:\n");
        info.append("Cache Size: ").append(messageCache.size()).append("/").append(config.getMaxCacheSize()).append("\n");
        info.append("Rate Limit: ").append(config.getRateLimitSeconds()).append(" seconds\n");
        
        if (!messageCache.isEmpty()) {
            info.append("Sample entries:\n");
            int count = 0;
            for (ConcurrentMap.Entry<String, CacheEntry> entry : messageCache.entrySet()) {
                if (count++ >= 5) break; // Show only first 5 entries
                CacheEntry cacheEntry = entry.getValue();
                info.append("  - \"").append(entry.getKey().substring(0, Math.min(50, entry.getKey().length())))
                    .append("...\" (count=").append(cacheEntry.getCount())
                    .append(", lastSeen=").append(cacheEntry.getLastSeen()).append(")\n");
            }
        }
        
        return info.toString();
    }
}