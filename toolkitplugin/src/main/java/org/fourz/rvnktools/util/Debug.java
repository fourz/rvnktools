package org.fourz.rvnktools.util;

import org.bukkit.plugin.java.JavaPlugin;
import java.util.logging.Level;
import java.util.logging.Logger;

public abstract class Debug {
    private final JavaPlugin plugin;
    private final String className;
    private Level logLevel;
    private boolean debugEnabled;

    protected Debug(JavaPlugin plugin, String className, Level level) {
        this.plugin = plugin;
        this.className = className;
        setLogLevel(level);
    }

    public void log(String message) {
        log(Level.INFO, message);
    }

    public void log(Level level, String message) {
        if (shouldLog(level)) {
            // Map FINE to INFO when actually logging
            Level logLevel = (level == Level.FINE) ? Level.INFO : level;
            plugin.getLogger().log(logLevel,
                String.format("[%s] %s%s", 
                    className,
                    (level == Level.FINE) ? "[DEBUG] " : "",
                    message));
        }
    }

    public void error(String message, Throwable e) {
        log(Level.SEVERE, message);
        if (e != null && shouldLog(Level.SEVERE)) {
            e.printStackTrace();
        }
    }

    public void debug(String message) {
        log(Level.FINE, message);
    }

    private boolean shouldLog(Level messageLevel) {
        if (logLevel == Level.OFF) {
            return false;
        }
        // Special handling for FINE (debug) level
        if (messageLevel == Level.FINE) {
            return logLevel == Level.FINE;
        }
        return messageLevel.intValue() >= logLevel.intValue();
    }

    public static Level parseLevel(String levelStr) {
        if (levelStr == null) return Level.INFO;
        try {
            if (levelStr.equalsIgnoreCase("DEBUG")) {
                return Level.FINE;
            }
            return Level.parse(levelStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            return Level.INFO;
        }
    }

    public void setLogLevel(Level level) {
        this.logLevel = level;
        debugEnabled = (level == Level.FINE);
    }

    public Level getLogLevel() {
        return logLevel;
    }
}