package org.fourz.rvnktools.util.log;

/**
 * Common interface for all logging implementations in RVNKTools.
 * Allows for easy switching between different logging strategies.
 */
public interface RVNKLogger {
    void debug(String message);
    void debug(String message, Throwable throwable);
    void info(String message);
    void info(String message, Throwable throwable);
    void warning(String message);
    void warning(String message, Throwable throwable);
    void error(String message);
    void error(String message, Throwable throwable);
    void performance(String section, long timeInNanos);
    boolean isDebugEnabled();
    void setDebugEnabled(boolean enabled);
}
