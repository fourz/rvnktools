package org.fourz.rvnktools.dhlogfilter.model;

import java.util.List;
import java.util.Objects;

/**
 * Configuration model for the DH log filter system.
 * Contains all settings for log level filtering and keyword-based message filtering.
 * 
 * @since 1.1-alpha
 */
public class DHLogFilterConfig {
    
    /**
     * Available log levels in order of verbosity (most to least verbose).
     */
    public enum LogLevel {
        DEBUG("DEBUG", 0),
        INFO("INFO", 1),
        WARN("WARN", 2),
        ERROR("ERROR", 3);
        
        private final String name;
        private final int level;
        
        LogLevel(String name, int level) {
            this.name = name;
            this.level = level;
        }
        
        public String getName() {
            return name;
        }
        
        public int getLevel() {
            return level;
        }
        
        /**
         * Check if this log level should allow messages of the specified level.
         * 
         * @param messageLevel The level of the message to check
         * @return true if the message should be allowed
         */
        public boolean shouldAllow(LogLevel messageLevel) {
            return messageLevel.level >= this.level;
        }
        
        /**
         * Parse a log level from string.
         * 
         * @param level The level string to parse
         * @return The corresponding LogLevel
         * @throws IllegalArgumentException if the level is invalid
         */
        public static LogLevel fromString(String level) {
            if (level == null) {
                throw new IllegalArgumentException("Log level cannot be null");
            }
            
            for (LogLevel logLevel : values()) {
                if (logLevel.name.equalsIgnoreCase(level.trim())) {
                    return logLevel;
                }
            }
            
            throw new IllegalArgumentException("Invalid log level: " + level + 
                ". Valid levels are: DEBUG, INFO, WARN, ERROR");
        }
    }
    
    private LogLevel level = LogLevel.INFO;
    private List<String> keywords = List.of("[DHS] Received");
    private boolean enabled = true;
    private long rateLimitSeconds = 30;
    private int maxCacheSize = 1000;
    private boolean useRegexPatterns = false;
    
    /**
     * Default constructor with default settings.
     */
    public DHLogFilterConfig() {
        // Use default values
    }
    
    /**
     * Constructor with specified settings.
     * 
     * @param level The log level threshold
     * @param keywords List of keywords to filter
     * @param enabled Whether filtering is enabled
     */
    public DHLogFilterConfig(LogLevel level, List<String> keywords, boolean enabled) {
        this.level = level;
        this.keywords = keywords != null ? List.copyOf(keywords) : List.of();
        this.enabled = enabled;
    }
    
    /**
     * Get the current log level threshold.
     * 
     * @return The log level
     */
    public LogLevel getLevel() {
        return level;
    }
    
    /**
     * Set the log level threshold.
     * 
     * @param level The log level to set
     */
    public void setLevel(LogLevel level) {
        this.level = level != null ? level : LogLevel.INFO;
    }
    
    /**
     * Set the log level from string.
     * 
     * @param level The log level string
     * @throws IllegalArgumentException if the level is invalid
     */
    public void setLevel(String level) {
        this.level = LogLevel.fromString(level);
    }
    
    /**
     * Get the list of keywords to filter.
     * 
     * @return List of filter keywords
     */
    public List<String> getKeywords() {
        return keywords;
    }
    
    /**
     * Set the list of keywords to filter.
     * 
     * @param keywords List of keywords
     */
    public void setKeywords(List<String> keywords) {
        this.keywords = keywords != null ? List.copyOf(keywords) : List.of();
    }
    
    /**
     * Check if filtering is enabled.
     * 
     * @return true if enabled
     */
    public boolean isEnabled() {
        return enabled;
    }
    
    /**
     * Set whether filtering is enabled.
     * 
     * @param enabled true to enable filtering
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
    
    /**
     * Get the rate limit duration in seconds.
     * 
     * @return Rate limit seconds
     */
    public long getRateLimitSeconds() {
        return rateLimitSeconds;
    }
    
    /**
     * Set the rate limit duration in seconds.
     * 
     * @param rateLimitSeconds The duration in seconds
     */
    public void setRateLimitSeconds(long rateLimitSeconds) {
        this.rateLimitSeconds = Math.max(1, rateLimitSeconds);
    }
    
    /**
     * Get the maximum cache size for rate limiting.
     * 
     * @return Maximum cache size
     */
    public int getMaxCacheSize() {
        return maxCacheSize;
    }
    
    /**
     * Set the maximum cache size for rate limiting.
     * 
     * @param maxCacheSize The maximum cache size
     */
    public void setMaxCacheSize(int maxCacheSize) {
        this.maxCacheSize = Math.max(100, maxCacheSize);
    }
    
    /**
     * Check if regex patterns are enabled for keyword matching.
     * 
     * @return true if regex patterns are enabled
     */
    public boolean isUseRegexPatterns() {
        return useRegexPatterns;
    }
    
    /**
     * Set whether to use regex patterns for keyword matching.
     * 
     * @param useRegexPatterns true to enable regex patterns
     */
    public void setUseRegexPatterns(boolean useRegexPatterns) {
        this.useRegexPatterns = useRegexPatterns;
    }
    
    /**
     * Check if a message should be filtered based on keywords.
     * 
     * @param message The message to check
     * @return true if the message should be filtered
     */
    public boolean shouldFilterMessage(String message) {
        if (message == null || keywords.isEmpty() || !enabled) {
            return false;
        }
        
        for (String keyword : keywords) {
            if (useRegexPatterns) {
                try {
                    if (message.matches(keyword)) {
                        return true;
                    }
                } catch (Exception e) {
                    // If regex fails, fall back to simple contains check
                    if (message.contains(keyword)) {
                        return true;
                    }
                }
            } else {
                if (message.contains(keyword)) {
                    return true;
                }
            }
        }
        
        return false;
    }
    
    /**
     * Validate the configuration settings.
     * 
     * @throws IllegalArgumentException if configuration is invalid
     */
    public void validate() {
        if (level == null) {
            throw new IllegalArgumentException("Log level cannot be null");
        }
        
        if (rateLimitSeconds < 1) {
            throw new IllegalArgumentException("Rate limit seconds must be at least 1");
        }
        
        if (maxCacheSize < 100) {
            throw new IllegalArgumentException("Max cache size must be at least 100");
        }
        
        // Validate regex patterns if enabled
        if (useRegexPatterns && keywords != null) {
            for (String keyword : keywords) {
                try {
                    // Test compile the regex pattern
                    "test".matches(keyword);
                } catch (Exception e) {
                    throw new IllegalArgumentException("Invalid regex pattern: " + keyword, e);
                }
            }
        }
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DHLogFilterConfig that = (DHLogFilterConfig) o;
        return enabled == that.enabled &&
               rateLimitSeconds == that.rateLimitSeconds &&
               maxCacheSize == that.maxCacheSize &&
               useRegexPatterns == that.useRegexPatterns &&
               level == that.level &&
               Objects.equals(keywords, that.keywords);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(level, keywords, enabled, rateLimitSeconds, maxCacheSize, useRegexPatterns);
    }
    
    @Override
    public String toString() {
        return String.format(
            "DHLogFilterConfig{level=%s, keywords=%s, enabled=%s, rateLimitSeconds=%d, " +
            "maxCacheSize=%d, useRegexPatterns=%s}",
            level, keywords, enabled, rateLimitSeconds, maxCacheSize, useRegexPatterns
        );
    }
}