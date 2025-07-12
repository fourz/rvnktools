package org.fourz.rvnktools.util.logging;

import org.bukkit.plugin.Plugin;

/**
 * Example usage of the logging system.
 * Shows how to use both LogManager and DebugLogger in a typical class.
 */
public class ExampleUsage {
    private final RVNKLogger logger;
    private final DebugLogger debugLogger;

    public ExampleUsage(Plugin plugin) {
        // For normal logging, use LogManager
        this.logger = LogManager.getInstance(plugin, getClass());
        
        // For performance-critical sections, use DebugLogger
        this.debugLogger = new DebugLogger(plugin, getClass());
        
        // Enable debug logging if needed (e.g., based on config)
        debugLogger.setDebugEnabled(true);
    }

    public void performOperation() {
        logger.info("Starting operation");

        try (AutoCloseable timer = debugLogger.timeSection("criticalOperation")) {
            // Your performance-critical code here
            performCriticalOperation();
            
            // Debug messages will only be logged if debug is enabled
            debugLogger.debug("Critical operation details: ...");
        } catch (Exception e) {
            logger.error("Operation failed", e);
        }

        // You can also manually track performance
        long startTime = System.nanoTime();
        performAnotherOperation();
        debugLogger.performance("anotherOperation", System.nanoTime() - startTime);

        // Check accumulated metrics
        debugLogger.getPerformanceMetrics().forEach((section, time) -> 
            logger.info(String.format("Total time for %s: %.2fms", section, time / 1_000_000.0))
        );
    }

    private void performCriticalOperation() {
        // Critical operation implementation
    }

    private void performAnotherOperation() {
        // Another operation implementation
    }
}
