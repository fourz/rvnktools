package org.fourz.rvnktools.util.log;

/**
 * Common interface for all logging implementations in RVNKTools.
 * Allows for easy switching between different logging strategies.
 *
 * @deprecated Use {@link org.fourz.rvnkcore.util.log.LogManager} directly instead.
 *             This interface will be removed in a future version.
 */
@Deprecated(since = "1.4.0", forRemoval = true)
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
