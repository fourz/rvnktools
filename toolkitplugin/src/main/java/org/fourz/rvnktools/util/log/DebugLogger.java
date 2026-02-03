package org.fourz.rvnktools.util.log;

import org.bukkit.plugin.Plugin;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Performance-focused logger implementation that collects metrics
 * and provides detailed debugging information when enabled.
 *
 * @deprecated Use {@link org.fourz.rvnkcore.util.log.LogManager} directly instead.
 *             This class will be removed in a future version.
 */
@Deprecated(since = "1.4.0", forRemoval = true)
public class DebugLogger implements RVNKLogger {
    private final RVNKLogger baseLogger;
    private final Map<String, AtomicLong> performanceMetrics = new ConcurrentHashMap<>();
    private boolean debugEnabled = false;

    public DebugLogger(Plugin plugin, Class<?> clazz) {
        this.baseLogger = LogManager.getInstance(plugin, clazz);
    }

    public DebugLogger(Plugin plugin) {
        this.baseLogger = LogManager.getInstance(plugin);
    }

    @Override
    public void debug(String message) {
        if (debugEnabled) {
            baseLogger.debug(message);
        }
    }

    @Override
    public void debug(String message, Throwable throwable) {
        if (debugEnabled) {
            baseLogger.debug(message, throwable);
        }
    }

    @Override
    public void info(String message) {
        baseLogger.info(message);
    }

    @Override
    public void info(String message, Throwable throwable) {
        baseLogger.info(message, throwable);
    }

    @Override
    public void warning(String message) {
        baseLogger.warning(message);
    }

    @Override
    public void warning(String message, Throwable throwable) {
        baseLogger.warning(message, throwable);
    }

    @Override
    public void error(String message) {
        baseLogger.error(message);
    }

    @Override
    public void error(String message, Throwable throwable) {
        baseLogger.error(message, throwable);
    }

    @Override
    public void performance(String section, long timeInNanos) {
        if (debugEnabled) {
            performanceMetrics.computeIfAbsent(section, k -> new AtomicLong())
                            .addAndGet(timeInNanos);
            baseLogger.performance(section, timeInNanos);
        }
    }

    @Override
    public boolean isDebugEnabled() {
        return debugEnabled;
    }

    @Override
    public void setDebugEnabled(boolean enabled) {
        this.debugEnabled = enabled;
        baseLogger.setDebugEnabled(enabled);
    }

    /**
     * Get accumulated performance metrics for all monitored sections.
     * @return Map of section names to their total execution time in nanoseconds
     */
    public Map<String, Long> getPerformanceMetrics() {
        Map<String, Long> result = new ConcurrentHashMap<>();
        performanceMetrics.forEach((key, value) -> result.put(key, value.get()));
        return result;
    }

    /**
     * Reset all performance metrics to zero.
     */
    public void resetMetrics() {
        performanceMetrics.clear();
    }

    /**
     * Utility method to time a section of code.
     * @param section The name of the section being timed
     * @return AutoCloseable timer that will log the duration when closed
     */
    public AutoCloseable timeSection(String section) {
        if (!debugEnabled) {
            return () -> {};
        }
        long startTime = System.nanoTime();
        return () -> performance(section, System.nanoTime() - startTime);
    }
}
