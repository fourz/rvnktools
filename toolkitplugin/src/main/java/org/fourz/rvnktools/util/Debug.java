package org.fourz.rvnktools.util;

import org.bukkit.plugin.java.JavaPlugin;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.logging.Level;

// This class is used for logging debug messages
// with different log levels and timestamps in the format [HH:mm:ss.SSS]
// It also provides a method to log errors with stack traces
// and a method to check if debug mode is enabled
// The log level can be set and retrieved
// The log level is used to determine which messages should be logged
// based on their log level
// The log level can be set to OFF to disable logging
// The log level can be set to ALL to log all messages
// The log level can be set to a specific level to log messages of that level and higher

public abstract class Debug {
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
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    protected Debug(JavaPlugin plugin, String className, LogLevel level) {
        this.plugin = plugin;
        this.className = className;
        this.logLevel = level;
    }

    public void log(String message) {
        log(LogLevel.INFO, message);
    }

    public void log(LogLevel level, String message) {
        if (shouldLog(level)) {
            String timestamp = LocalDateTime.now().format(TIME_FORMAT);
            plugin.getLogger().log(level.getLevel(), 
                String.format("%s [%s] %s", timestamp, className, message));
        }
    }

    public void error(String message, Throwable e) {
        log(LogLevel.SEVERE, message);
        if (e != null && shouldLog(LogLevel.SEVERE)) {
            e.printStackTrace();
        }
    }

    public boolean isDebugEnabled() {
        return logLevel.ordinal() <= LogLevel.FINE.ordinal();
    }

    private boolean shouldLog(LogLevel level) {
        return logLevel != LogLevel.OFF && level.ordinal() >= logLevel.ordinal();
    }

    public void setLogLevel(LogLevel level) {
        this.logLevel = level;
    }

    public LogLevel getLogLevel() {
        return logLevel;
    }
}