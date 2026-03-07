package org.fourz.rvnktools.util.log;

import org.bukkit.plugin.Plugin;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Centralized logging manager for RVNKTools.
 * Provides standard logging functionality with configurable debug output.
 *
 * @deprecated Use {@link org.fourz.rvnkcore.util.log.LogManager} instead.
 *             This class will be removed in a future version.
 */
@Deprecated(since = "1.4.0", forRemoval = true)
public class LogManager implements RVNKLogger {
    private static final Map<String, LogManager> instances = new ConcurrentHashMap<>();

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
    private volatile Level logLevel = defaultLevel;
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
        if (shouldLog(Level.FINE)) {
            logger.info(prefix + "[DEBUG] " + message);
        }
    }

    @Override
    public void debug(String message, Throwable throwable) {
        if (shouldLog(Level.FINE)) {
            logger.log(Level.INFO, prefix + "[DEBUG] " + message, throwable);
        }
    }

    @Override
    public void info(String message) {
        if (shouldLog(Level.INFO)) {
            logger.log(Level.INFO, prefix + message);
        }
    }

    @Override
    public void info(String message, Throwable throwable) {
        if (shouldLog(Level.INFO)) {
            logger.log(Level.INFO, prefix + message, throwable);
        }
    }

    @Override
    public void warning(String message) {
        if (shouldLog(Level.WARNING)) {
            logger.log(Level.WARNING, prefix + message);
        }
    }

    @Override
    public void warning(String message, Throwable throwable) {
        if (shouldLog(Level.WARNING)) {
            logger.log(Level.WARNING, prefix + message, throwable);
        }
    }

    @Override
    public void error(String message) {
        if (shouldLog(Level.SEVERE)) {
            logger.log(Level.SEVERE, prefix + message);
        }
    }

    @Override
    public void error(String message, Throwable throwable) {
        if (shouldLog(Level.SEVERE)) {
            logger.log(Level.SEVERE, prefix + message, throwable);
        }
    }

    @Override
    public void performance(String section, long timeInNanos) {
        if (shouldLog(Level.FINE)) {
            double timeInMs = timeInNanos / 1_000_000.0;
            logger.info(prefix + String.format("[PERFORMANCE] %s: %.2fms", section, timeInMs));
        }
    }

    @Override
    public boolean isDebugEnabled() {
        return debugEnabled;
    }

    @Override
    public void setDebugEnabled(boolean enabled) {
        this.debugEnabled = enabled;
        if (enabled && logLevel.intValue() > Level.FINE.intValue()) {
            this.logLevel = Level.FINE;
        }
    }

    // ========================================================================
    // Level Filtering
    // ========================================================================

    private boolean shouldLog(Level messageLevel) {
        if (logLevel == Level.OFF) return false;
        if (logLevel == Level.ALL) return true;
        if (messageLevel == Level.FINE) {
            return debugEnabled || logLevel.intValue() <= Level.FINE.intValue();
        }
        return messageLevel.intValue() >= logLevel.intValue();
    }

    public void setLogLevel(Level level) {
        this.logLevel = level;
        this.debugEnabled = (level.intValue() <= Level.FINE.intValue());
    }

    public Level getLogLevel() {
        return logLevel;
    }

    /**
     * Parses a level string to a Java Level enum.
     * Supports aliases: DEBUG, FINE, INFO, WARN, WARNING, ERROR, SEVERE, OFF, ALL
     */
    public static Level parseLevel(String levelString) {
        if (levelString == null || levelString.isBlank()) return Level.INFO;
        Level aliasLevel = LEVEL_ALIASES.get(levelString.trim().toUpperCase());
        return aliasLevel != null ? aliasLevel : Level.INFO;
    }

    /**
     * Sets the log level for all existing instances and updates the default
     * for future instances.
     */
    public static void setGlobalLogLevel(Level level) {
        defaultLevel = level;
        instances.values().forEach(m -> m.setLogLevel(level));
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
