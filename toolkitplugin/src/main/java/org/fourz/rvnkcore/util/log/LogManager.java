package org.fourz.rvnkcore.util.log;

import org.bukkit.plugin.Plugin;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Centralized logging manager for RVNKCore components.
 * 
 * Provides consistent logging capabilities with enhanced error handling,
 * structured output formatting, and intelligent exception analysis.
 * 
 * Features:
 * - Concise error summaries with actionable guidance
 * - Root cause analysis of complex exception chains
 * - Debug level control and performance tracking
 * - Structured formatting for stand-out error messages
 * 
 * @since 1.3.0
 */
public class LogManager {
    
    private static final ConcurrentMap<String, LogManager> instances = new ConcurrentHashMap<>();
    
    private final Logger logger;
    private final String prefix;
    private boolean debugEnabled = false;
    
    /**
     * Private constructor for singleton pattern.
     * 
     * @param plugin The plugin instance
     * @param prefix The log message prefix
     */
    private LogManager(Plugin plugin, String prefix) {
        this.logger = plugin.getLogger();
        this.prefix = prefix;
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
        return instances.computeIfAbsent(key, k -> 
            new LogManager(plugin, "[" + clazz.getSimpleName() + "] "));
    }
    
    /**
     * Gets a LogManager instance for the plugin.
     * 
     * @param plugin The plugin instance
     * @return LogManager instance for the plugin
     */
    public static LogManager getInstance(Plugin plugin) {
        String key = plugin.getName() + ":Main";
        return instances.computeIfAbsent(key, k -> 
            new LogManager(plugin, "[" + plugin.getName() + "] "));
    }
    
    /**
     * Logs an info message.
     * 
     * @param message The message to log
     */
    public void info(String message) {
        logger.info(prefix + message);
    }
    
    /**
     * Logs an info message with exception details.
     * 
     * @param message The message to log
     * @param throwable The exception to log
     */
    public void info(String message, Throwable throwable) {
        logger.log(Level.INFO, prefix + message, throwable);
    }
    
    /**
     * Logs a debug message (only if debug is enabled).
     * 
     * @param message The message to log
     */
    public void debug(String message) {
        if (debugEnabled) {
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
        if (debugEnabled) {
            logger.log(Level.INFO, prefix + "[DEBUG] " + message, throwable);
        }
    }
    
    /**
     * Logs a warning message.
     * 
     * @param message The message to log
     */
    public void warning(String message) {
        logger.warning(prefix + message);
    }
    
    /**
     * Logs a warning message with exception details.
     * 
     * @param message The message to log
     * @param throwable The exception to log
     */
    public void warning(String message, Throwable throwable) {
        logger.log(Level.WARNING, prefix + message, throwable);
    }
    
    /**
     * Logs an error message.
     * 
     * @param message The message to log
     */
    public void error(String message) {
        logger.severe(prefix + message);
    }
    
    /**
     * Logs an error message with exception details.
     * 
     * @param message The message to log
     * @param throwable The exception to log
     */
    public void error(String message, Throwable throwable) {
        logger.log(Level.SEVERE, prefix + message, throwable);
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
     * Logs performance timing information.
     * 
     * @param section The operation being timed
     * @param timeInNanos The elapsed time in nanoseconds
     */
    public void performance(String section, long timeInNanos) {
        if (debugEnabled) {
            double timeInMs = timeInNanos / 1_000_000.0;
            logger.info(prefix + "[PERFORMANCE] " + section + " took " + 
                       String.format("%.2f", timeInMs) + "ms");
        }
    }
    
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
     * 
     * @param enabled True to enable debug logging
     */
    public void setDebugEnabled(boolean enabled) {
        this.debugEnabled = enabled;
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
