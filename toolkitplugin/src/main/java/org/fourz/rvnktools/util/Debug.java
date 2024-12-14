package org.fourz.rvnktools.util;

import org.bukkit.plugin.java.JavaPlugin;
import java.util.logging.Level;

// Provides debug logging functionality with configurable log levels and timestamp formatting
public abstract class Debug {
    // Maps internal log levels to Java logging levels
    public enum LogLevel {
        OFF(Level.OFF),
        SEVERE(Level.SEVERE),
        WARNING(Level.WARNING),
        INFO(Level.INFO),
        CONFIG(Level.CONFIG),
        FINE(Level.FINE),
        FINER(Level.FINER),
        FINEST(Level.FINEST),
        ALL(Level.ALL);

        private final Level level;

        LogLevel(Level level) {
            this.level = level;
        }

        public Level getLevel() {
            return level;
        }

        public static LogLevel fromString(String level) {
            try {
                return valueOf(level.toUpperCase());
            } catch (IllegalArgumentException e) {
                return INFO;
            }
        }
    }

    private final JavaPlugin plugin;
    private final String className;
    private LogLevel logLevel;

    // Initializes debug logger with plugin instance, class name and log level
    protected Debug(JavaPlugin plugin, String className, LogLevel level) {
        this.plugin = plugin;
        this.className = className;
        this.logLevel = level;
    }

    // Logs a message with default INFO level
    public void log(String message) {
        log(LogLevel.INFO, message);
    }

    // Logs a message with specified level
    public void log(LogLevel level, String message) {
        if (shouldLog(level)) {
            plugin.getLogger().log(level.getLevel(), 
                String.format("[%s] %s", className, message));
        }
    }

    // Logs an error message with optional stack trace
    public void error(String message, Throwable e) {
        log(LogLevel.SEVERE, message);
        if (e != null && shouldLog(LogLevel.SEVERE)) {
            e.printStackTrace();
        }
    }

    // Checks if debug logging is enabled (FINE or lower level)
    public boolean isDebugEnabled() {
        return logLevel.ordinal() <= LogLevel.FINE.ordinal();
    }

    // Determines if a message should be logged based on current log level
    private boolean shouldLog(LogLevel level) {
        // INFO (ordinal 800) should be >= INFO (800)
        return logLevel != LogLevel.OFF && 
               level.getLevel().intValue() >= logLevel.getLevel().intValue();
    }
    

    // Sets the current log level
    public void setLogLevel(LogLevel level) {
        this.logLevel = level;
    }

    // Gets the current log level
    public LogLevel getLogLevel() {
        return logLevel;
    }
}