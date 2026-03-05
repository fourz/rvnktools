package org.fourz.rvnkcore.util.log;

import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Centralized logging manager for RVNKCore and all RVNK plugins.
 *
 * Provides consistent logging capabilities with enhanced error handling,
 * structured output formatting, and intelligent exception analysis.
 *
 * Features:
 * - Java Level enum support with config string aliases
 * - Per-instance and global log level control
 * - Concise error summaries with actionable guidance
 * - Root cause analysis of complex exception chains
 * - Debug level control and performance tracking
 * - Structured formatting for stand-out error messages
 * - Thread-safe instance caching with ConcurrentHashMap
 *
 * Level Aliases (for config files):
 * - DEBUG, FINE → Level.FINE
 * - INFO → Level.INFO
 * - WARN, WARNING → Level.WARNING
 * - ERROR, SEVERE → Level.SEVERE
 * - OFF → Level.OFF
 *
 * @since 1.3.0
 * @since 1.4.0 Added Level enum support and global level control
 */
public class LogManager {

    private static final ConcurrentMap<String, LogManager> instances = new ConcurrentHashMap<>();
    private static final ConcurrentMap<String, Level> pluginDefaultLevels = new ConcurrentHashMap<>();

    /** Level string aliases for config file compatibility */
    private static final Map<String, Level> LEVEL_ALIASES = Map.of(
        "DEBUG", Level.FINE,
        "FINE", Level.FINE,
        "INFO", Level.INFO,
        "WARN", Level.WARNING,
        "WARNING", Level.WARNING,
        "ERROR", Level.SEVERE,
        "SEVERE", Level.SEVERE,
        "OFF", Level.OFF,
        "ALL", Level.ALL
    );

    /** Static default level inherited by new instances. Updated by setGlobalLogLevel(). */
    private static volatile Level defaultLevel = Level.INFO;

    private final Logger logger;
    private final String prefix;
    private final String pluginName;
    private volatile Level logLevel = defaultLevel;
    private volatile boolean debugEnabled = false;
    
    /**
     * Private constructor for singleton pattern.
     *
     * @param plugin The plugin instance
     * @param prefix The log message prefix
     */
    private LogManager(Plugin plugin, String prefix) {
        this.logger = plugin.getLogger();
        this.prefix = prefix;
        this.pluginName = plugin.getName();
    }
    
    /**
     * Gets a LogManager instance for a specific class.
     * 
     * @param plugin The plugin instance
     * @param clazz The class requesting the logger
     * @return LogManager instance for the class
     */
    public static LogManager getInstance(Plugin plugin, Class<?> clazz) {
        String key = plugin.getName() + ":" + clazz.getSimpleName();
        return instances.computeIfAbsent(key, k -> {
            LogManager lm = new LogManager(plugin, "[" + clazz.getSimpleName() + "] ");
            Level pluginLevel = pluginDefaultLevels.get(plugin.getName());
            if (pluginLevel != null) lm.setLogLevel(pluginLevel);
            return lm;
        });
    }
    
    /**
     * Gets a LogManager instance for a specific class name.
     *
     * @param plugin The plugin instance
     * @param className The class name for the logger prefix
     * @return LogManager instance for the class
     */
    public static LogManager getInstance(Plugin plugin, String className) {
        String key = plugin.getName() + ":" + className;
        return instances.computeIfAbsent(key, k -> {
            LogManager lm = new LogManager(plugin, "[" + className + "] ");
            Level pluginLevel = pluginDefaultLevels.get(plugin.getName());
            if (pluginLevel != null) lm.setLogLevel(pluginLevel);
            return lm;
        });
    }

    /**
     * Gets a LogManager instance for the plugin.
     * Uses empty prefix since Bukkit already adds [PluginName] automatically.
     *
     * @param plugin The plugin instance
     * @return LogManager instance for the plugin
     */
    public static LogManager getInstance(Plugin plugin) {
        String key = plugin.getName() + ":Main";
        return instances.computeIfAbsent(key, k -> {
            LogManager lm = new LogManager(plugin, "");
            Level pluginLevel = pluginDefaultLevels.get(plugin.getName());
            if (pluginLevel != null) lm.setLogLevel(pluginLevel);
            return lm;
        });
    }
    
    // ========================================================================
    // Core Logging Methods
    // ========================================================================

    /**
     * Logs an info message (if log level permits).
     *
     * @param message The message to log
     */
    public void info(String message) {
        if (shouldLog(Level.INFO)) {
            logger.info(prefix + message);
        }
    }

    /**
     * Logs an info message with exception details (if log level permits).
     *
     * @param message The message to log
     * @param throwable The exception to log
     */
    public void info(String message, Throwable throwable) {
        if (shouldLog(Level.INFO)) {
            logger.log(Level.INFO, prefix + message, throwable);
        }
    }

    /**
     * Logs a debug message (only if debug is enabled or level is FINE/ALL).
     *
     * @param message The message to log
     */
    public void debug(String message) {
        if (shouldLog(Level.FINE)) {
            logger.info(prefix + "[DEBUG] " + message);
        }
    }

    /**
     * Logs a debug message with exception details (only if debug is enabled).
     *
     * @param message The message to log
     * @param throwable The exception to log
     */
    public void debug(String message, Throwable throwable) {
        if (shouldLog(Level.FINE)) {
            logger.log(Level.INFO, prefix + "[DEBUG] " + message, throwable);
        }
    }

    /**
     * Logs a warning message (if log level permits).
     *
     * @param message The message to log
     */
    public void warning(String message) {
        if (shouldLog(Level.WARNING)) {
            logger.warning(prefix + message);
        }
    }

    /**
     * Logs a warning message with exception details (if log level permits).
     *
     * @param message The message to log
     * @param throwable The exception to log
     */
    public void warning(String message, Throwable throwable) {
        if (shouldLog(Level.WARNING)) {
            logger.log(Level.WARNING, prefix + message, throwable);
        }
    }

    /**
     * Logs an error message (always logged unless level is OFF).
     *
     * @param message The message to log
     */
    public void error(String message) {
        if (shouldLog(Level.SEVERE)) {
            logger.severe(prefix + message);
        }
    }

    /**
     * Logs an error message with exception details (always logged unless level is OFF).
     *
     * @param message The message to log
     * @param throwable The exception to log
     */
    public void error(String message, Throwable throwable) {
        if (shouldLog(Level.SEVERE)) {
            logger.log(Level.SEVERE, prefix + message, throwable);
        }
    }

    // ========================================================================
    // Level Filtering
    // ========================================================================

    /**
     * Determines if a message at the given level should be logged.
     *
     * @param messageLevel The level of the message to check
     * @return true if the message should be logged
     */
    private boolean shouldLog(Level messageLevel) {
        if (logLevel == Level.OFF) {
            return false;
        }
        if (logLevel == Level.ALL) {
            return true;
        }
        // FINE (debug) level uses the debugEnabled flag for backward compatibility
        if (messageLevel == Level.FINE) {
            return debugEnabled || logLevel.intValue() <= Level.FINE.intValue();
        }
        return messageLevel.intValue() >= logLevel.intValue();
    }

    // ========================================================================
    // Level Management
    // ========================================================================

    /**
     * Sets the log level for this instance.
     *
     * @param level The new log level
     */
    public void setLogLevel(Level level) {
        this.logLevel = level;
        // Sync debugEnabled flag for backward compatibility
        this.debugEnabled = (level.intValue() <= Level.FINE.intValue());
    }

    /**
     * Gets the current log level for this instance.
     *
     * @return The current log level
     */
    public Level getLogLevel() {
        return logLevel;
    }

    /**
     * Parses a level string to a Java Level enum.
     * Supports common aliases: DEBUG, FINE, INFO, WARN, WARNING, ERROR, SEVERE, OFF, ALL
     *
     * @param levelString The level string from config
     * @return The corresponding Level, or Level.INFO if not recognized
     */
    public static Level parseLevel(String levelString) {
        if (levelString == null || levelString.isBlank()) {
            return Level.INFO;
        }
        String normalized = levelString.trim().toUpperCase();
        Level aliasLevel = LEVEL_ALIASES.get(normalized);
        if (aliasLevel != null) {
            return aliasLevel;
        }
        // Fallback to Java's Level.parse() for any other valid levels
        try {
            return Level.parse(normalized);
        } catch (IllegalArgumentException e) {
            return Level.INFO;
        }
    }

    /**
     * Sets the log level for all existing LogManager instances and updates the
     * default level for future instances.
     *
     * @param level The new global log level
     */
    public static void setGlobalLogLevel(Level level) {
        defaultLevel = level;
        instances.values().forEach(manager -> manager.setLogLevel(level));
    }

    /**
     * Sets the log level for all LogManager instances of a specific plugin.
     *
     * @param plugin The plugin to update
     * @param level The new log level
     */
    public static void setPluginLogLevel(Plugin plugin, Level level) {
        String pluginPrefix = plugin.getName() + ":";
        pluginDefaultLevels.put(plugin.getName(), level);
        instances.entrySet().stream()
            .filter(entry -> entry.getKey().startsWith(pluginPrefix))
            .forEach(entry -> entry.getValue().setLogLevel(level));
    }

    /**
     * Clears all LogManager instances for a specific plugin.
     * Call this in your plugin's onDisable() to prevent memory leaks.
     *
     * @param plugin The plugin to clear loggers for
     */
    public static void clearLoggers(Plugin plugin) {
        String pluginPrefix = plugin.getName() + ":";
        instances.keySet().removeIf(key -> key.startsWith(pluginPrefix));
        pluginDefaultLevels.remove(plugin.getName());
    }
    
    /**
     * Logs a structured error summary with actionable guidance.
     * 
     * Provides concise, stand-out error messages that help administrators
     * quickly identify and resolve issues without parsing verbose stack traces.
     * 
     * @param operation The operation that failed (e.g., "Database Connection")
     * @param problem The specific problem encountered
     * @param solution Suggested solution or next steps
     */
    public void errorSummary(String operation, String problem, String solution) {
        logger.severe("================================================================================");
        logger.severe(prefix + "CRITICAL ERROR - " + operation);
        logger.severe("================================================================================");
        logger.severe(prefix + "Problem: " + problem);
        logger.severe(prefix + "Solution: " + solution);
        logger.severe("================================================================================");
    }
    
    /**
     * Logs performance timing information (only if debug is enabled).
     *
     * @param section The operation being timed
     * @param timeInNanos The elapsed time in nanoseconds
     */
    public void performance(String section, long timeInNanos) {
        if (shouldLog(Level.FINE)) {
            double timeInMs = timeInNanos / 1_000_000.0;
            logger.info(prefix + "[PERFORMANCE] " + section + " took " +
                       String.format("%.2f", timeInMs) + "ms");
        }
    }

    // ========================================================================
    // Backward Compatibility Methods
    // ========================================================================

    /**
     * Checks if debug logging is enabled.
     *
     * @return True if debug logging is enabled
     */
    public boolean isDebugEnabled() {
        return debugEnabled;
    }

    /**
     * Sets debug logging state.
     * This method is kept for backward compatibility.
     * Prefer using setLogLevel(Level.FINE) for new code.
     *
     * @param enabled True to enable debug logging
     */
    public void setDebugEnabled(boolean enabled) {
        this.debugEnabled = enabled;
        // Sync logLevel when using the legacy method
        if (enabled && logLevel.intValue() > Level.FINE.intValue()) {
            this.logLevel = Level.FINE;
        }
    }
    
    /**
     * Extracts the root cause message from an exception chain.
     * 
     * Traverses the exception chain to find the most specific, actionable
     * error message, filtering out generic wrapper exceptions.
     * 
     * @param throwable The exception to analyze
     * @return The root cause message
     */
    public static String getRootCauseMessage(Throwable throwable) {
        if (throwable == null) {
            return "Unknown error";
        }
        
        Throwable rootCause = throwable;
        Throwable current = throwable;
        
        // Traverse the exception chain to find the root cause
        while (current != null) {
            // Skip generic wrapper exceptions and focus on specific database errors
            if (isSpecificException(current)) {
                rootCause = current;
            }
            current = current.getCause();
        }
        
        String message = rootCause.getMessage();
        return message != null ? message.trim() : rootCause.getClass().getSimpleName();
    }
    
    /**
     * Determines if an exception contains specific, actionable information.
     * 
     * @param throwable The exception to check
     * @return True if the exception provides specific error details
     */
    private static boolean isSpecificException(Throwable throwable) {
        String className = throwable.getClass().getSimpleName();
        String message = throwable.getMessage();
        
        // Skip generic wrapper exceptions
        if (className.contains("RuntimeException") || 
            className.contains("SQLException") ||
            className.contains("HikariPool") ||
            className.contains("PoolInitializationException")) {
            return false;
        }
        
        // Prefer exceptions with specific error messages
        if (message != null) {
            String lowerMessage = message.toLowerCase();
            return lowerMessage.contains("access denied") ||
                   lowerMessage.contains("unknown database") ||
                   lowerMessage.contains("connection refused") ||
                   lowerMessage.contains("unknown host") ||
                   lowerMessage.contains("ssl") ||
                   lowerMessage.contains("communications link failure") ||
                   lowerMessage.contains("timeout");
        }
        
        return true;
    }
    
    /**
     * Sets debug mode for all LogManager instances.
     * 
     * @param enabled True to enable debug logging globally
     */
    public static void setGlobalDebugEnabled(boolean enabled) {
        instances.values().forEach(manager -> manager.setDebugEnabled(enabled));
    }
    
    /**
     * Gets statistics about LogManager instances.
     * 
     * @return Formatted string with LogManager statistics
     */
    public static String getStatistics() {
        return "LogManager instances: " + instances.size() + 
               ", Debug enabled: " + instances.values().stream().anyMatch(LogManager::isDebugEnabled);
    }
}
