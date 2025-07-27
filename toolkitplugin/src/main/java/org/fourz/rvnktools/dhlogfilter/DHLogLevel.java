package org.fourz.rvnktools.dhlogfilter;

/**
 * Enumeration of available log levels for DH log filtering.
 * 
 * Each level includes more restrictive levels, following standard logging hierarchy.
 */
public enum DHLogLevel {
    /**
     * Show all messages including debug output (most verbose).
     */
    DEBUG("DEBUG", 0),
    
    /**
     * Show info, warnings, and errors (default level).
     */
    INFO("INFO", 1),
    
    /**
     * Show only warnings and errors.
     */
    WARN("WARN", 2),
    
    /**
     * Show only errors (most restrictive).
     */
    ERROR("ERROR", 3);
    
    private final String displayName;
    private final int level;
    
    DHLogLevel(String displayName, int level) {
        this.displayName = displayName;
        this.level = level;
    }
    
    /**
     * Gets the display name for this log level.
     * 
     * @return The display name
     */
    public String getDisplayName() {
        return displayName;
    }
    
    /**
     * Gets the numeric level for comparison purposes.
     * 
     * @return The numeric level (higher = more restrictive)
     */
    public int getLevel() {
        return level;
    }
    
    /**
     * Checks if this level should show messages of the specified level.
     * 
     * @param messageLevel The level of the message to check
     * @return true if the message should be shown
     */
    public boolean shouldShow(DHLogLevel messageLevel) {
        return messageLevel.level >= this.level;
    }
    
    /**
     * Parses a string into a DHLogLevel, case-insensitive.
     * 
     * @param levelString The string to parse
     * @return The corresponding DHLogLevel
     * @throws IllegalArgumentException if the string is not a valid level
     */
    public static DHLogLevel fromString(String levelString) {
        for (DHLogLevel level : values()) {
            if (level.displayName.equalsIgnoreCase(levelString)) {
                return level;
            }
        }
        throw new IllegalArgumentException("Invalid log level: " + levelString);
    }
}