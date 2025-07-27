package org.fourz.rvnktools.dhlogfilter;

import java.util.List;

/**
 * Configuration model for DH log filtering settings.
 * 
 * Represents the hierarchical configuration structure for controlling
 * log filtering behavior, including log levels and keyword-based filtering.
 */
public class DHLogFilterConfig {
    
    private DHLogLevel level = DHLogLevel.INFO;
    private List<String> keywords = List.of("[DHS] Received");
    private boolean enabled = true;
    private int rateLimitSeconds = 30;
    private boolean useRegexPatterns = false;
    
    /**
     * Default constructor with default values.
     */
    public DHLogFilterConfig() {
        // Use default values set above
    }
    
    /**
     * Constructor with all parameters.
     * 
     * @param level The log level to filter at
     * @param keywords List of keywords to filter
     * @param enabled Whether filtering is enabled
     * @param rateLimitSeconds Rate limiting cache duration in seconds
     * @param useRegexPatterns Whether to treat keywords as regex patterns
     */
    public DHLogFilterConfig(DHLogLevel level, List<String> keywords, boolean enabled, 
                           int rateLimitSeconds, boolean useRegexPatterns) {
        this.level = level;
        this.keywords = keywords;
        this.enabled = enabled;
        this.rateLimitSeconds = rateLimitSeconds;
        this.useRegexPatterns = useRegexPatterns;
    }
    
    /**
     * Gets the configured log level.
     * 
     * @return The log level
     */
    public DHLogLevel getLevel() {
        return level;
    }
    
    /**
     * Sets the log level.
     * 
     * @param level The log level to set
     */
    public void setLevel(DHLogLevel level) {
        this.level = level;
    }
    
    /**
     * Gets the list of keywords to filter.
     * 
     * @return List of keywords
     */
    public List<String> getKeywords() {
        return keywords;
    }
    
    /**
     * Sets the list of keywords to filter.
     * 
     * @param keywords List of keywords
     */
    public void setKeywords(List<String> keywords) {
        this.keywords = keywords;
    }
    
    /**
     * Checks if filtering is enabled.
     * 
     * @return true if filtering is enabled
     */
    public boolean isEnabled() {
        return enabled;
    }
    
    /**
     * Sets whether filtering is enabled.
     * 
     * @param enabled true to enable filtering
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
    
    /**
     * Gets the rate limiting cache duration in seconds.
     * 
     * @return Rate limit duration in seconds
     */
    public int getRateLimitSeconds() {
        return rateLimitSeconds;
    }
    
    /**
     * Sets the rate limiting cache duration in seconds.
     * 
     * @param rateLimitSeconds Rate limit duration in seconds
     */
    public void setRateLimitSeconds(int rateLimitSeconds) {
        this.rateLimitSeconds = rateLimitSeconds;
    }
    
    /**
     * Checks if keywords should be treated as regex patterns.
     * 
     * @return true if regex patterns are enabled
     */
    public boolean isUseRegexPatterns() {
        return useRegexPatterns;
    }
    
    /**
     * Sets whether keywords should be treated as regex patterns.
     * 
     * @param useRegexPatterns true to enable regex patterns
     */
    public void setUseRegexPatterns(boolean useRegexPatterns) {
        this.useRegexPatterns = useRegexPatterns;
    }
    
    /**
     * Validates the configuration settings.
     * 
     * @throws IllegalArgumentException if configuration is invalid
     */
    public void validate() {
        if (level == null) {
            throw new IllegalArgumentException("Log level cannot be null");
        }
        
        if (keywords == null) {
            throw new IllegalArgumentException("Keywords list cannot be null");
        }
        
        if (rateLimitSeconds < 1) {
            throw new IllegalArgumentException("Rate limit seconds must be positive");
        }
    }
}