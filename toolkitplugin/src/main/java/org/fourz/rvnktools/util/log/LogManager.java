package org.fourz.rvnktools.util.log;

import org.bukkit.plugin.Plugin;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Centralized logging manager for RVNKTools.
 * Provides standard logging functionality with configurable debug output.
 */
public class LogManager implements RVNKLogger {
    private static final Map<String, LogManager> instances = new ConcurrentHashMap<>();
    private final Logger logger;
    private boolean debugEnabled = false;
    private final String prefix;

    private LogManager(Plugin plugin, String prefix) {
        this.logger = plugin.getLogger();
        this.prefix = prefix;
    }

    /**
     * Get or create a LogManager instance for the given plugin and class.
     *
     * @param plugin The plugin requesting the logger
     * @param clazz The class requesting the logger
     * @return A LogManager instance
     */
    public static LogManager getInstance(Plugin plugin, Class<?> clazz) {
        String key = plugin.getName() + ":" + clazz.getSimpleName();
        return instances.computeIfAbsent(key, k -> new LogManager(plugin, "[" + clazz.getSimpleName() + "] "));
    }

    /**
     * Get or create a LogManager instance for the given plugin.
     *
     * @param plugin The plugin requesting the logger
     * @return A LogManager instance
     */
    public static LogManager getInstance(Plugin plugin) {
        return getInstance(plugin, plugin.getClass());
    }

    @Override
    public void debug(String message) {
        if (debugEnabled) {
            logger.log(Level.FINE, prefix + message);
        }
    }

    @Override
    public void debug(String message, Throwable throwable) {
        if (debugEnabled) {
            logger.log(Level.FINE, prefix + message, throwable);
        }
    }

    @Override
    public void info(String message) {
        logger.log(Level.INFO, prefix + message);
    }

    @Override
    public void info(String message, Throwable throwable) {
        logger.log(Level.INFO, prefix + message, throwable);
    }

    @Override
    public void warning(String message) {
        logger.log(Level.WARNING, prefix + message);
    }

    @Override
    public void warning(String message, Throwable throwable) {
        logger.log(Level.WARNING, prefix + message, throwable);
    }

    @Override
    public void error(String message) {
        logger.log(Level.SEVERE, prefix + message);
    }

    @Override
    public void error(String message, Throwable throwable) {
        logger.log(Level.SEVERE, prefix + message, throwable);
    }

    @Override
    public void performance(String section, long timeInNanos) {
        if (debugEnabled) {
            double timeInMs = timeInNanos / 1_000_000.0;
            logger.log(Level.FINE, prefix + String.format("Performance [%s]: %.2fms", section, timeInMs));
        }
    }

    @Override
    public boolean isDebugEnabled() {
        return debugEnabled;
    }

    @Override
    public void setDebugEnabled(boolean enabled) {
        this.debugEnabled = enabled;
    }
    
    /**
     * Logs a concise, helpful error summary without full stack traces.
     * Used for graceful error handling with actionable information.
     * 
     * @param context The error context (e.g., "MySQL Connection")
     * @param problem The specific problem
     * @param solution The suggested solution
     */
    public void errorSummary(String context, String problem, String solution) {
        logger.log(Level.SEVERE, "");
        logger.log(Level.SEVERE, prefix + "ERROR: " + context + " Failed");
        logger.log(Level.SEVERE, prefix + "Issue: " + problem);
        logger.log(Level.SEVERE, prefix + "Action: " + solution);
        logger.log(Level.SEVERE, "");
    }
    
    /**
     * Extracts the root cause message from an exception chain.
     * 
     * @param throwable The exception to analyze
     * @return The root cause message
     */
    public static String getRootCauseMessage(Throwable throwable) {
        Throwable rootCause = throwable;
        while (rootCause.getCause() != null && rootCause.getCause() != rootCause) {
            rootCause = rootCause.getCause();
        }
        return rootCause.getMessage() != null ? rootCause.getMessage() : rootCause.getClass().getSimpleName();
    }
}
