package org.fourz.rvnkcore.api.server.jetty;

import org.eclipse.jetty.server.Server;
import org.fourz.rvnkcore.api.config.ApiConfig;
import org.fourz.rvnkcore.util.log.LogManager;

/**
 * Manages the lifecycle of the RVNKCore API server including startup, shutdown, and monitoring.
 * Provides centralized lifecycle management with detailed logging and error handling.
 */
public class ServerLifecycle {
    private final ApiConfig config;
    private final LogManager logger;
    private volatile boolean isShuttingDown = false;

    /**
     * Creates a new server lifecycle manager.
     *
     * @param config API configuration
     * @param logger Logger instance
     */
    public ServerLifecycle(ApiConfig config, LogManager logger) {
        this.config = config;
        this.logger = logger;
    }

    /**
     * Starts the Jetty server with comprehensive error handling and logging.
     *
     * @param server The Jetty server instance to start
     * @throws ServerStartupException if server startup fails
     */
    public void startServer(Server server) throws ServerStartupException {
        if (!config.isEnabled()) {
            logger.info("RVNKCore API is disabled in configuration");
            return;
        }

        logger.info("Starting RVNKCore REST API server...");

        try {
            server.start();
            logSuccessfulStartup();
            
        } catch (Exception e) {
            handleStartupError(e);
            throw new ServerStartupException("Failed to start RVNKCore REST API server", e);
        }
    }

    /**
     * Stops the Jetty server gracefully with proper cleanup.
     *
     * @param server The Jetty server instance to stop
     */
    public void stopServer(Server server) {
        if (server == null || !server.isStarted()) {
            logger.info("RVNKCore API server was not running");
            return;
        }

        isShuttingDown = true;
        
        try {
            logger.info("Stopping RVNKCore API server...");
            
            // Set a reasonable stop timeout
            server.setStopTimeout(5000);
            server.stop();
            
            logger.info("RVNKCore API server stopped successfully");
            
        } catch (Exception e) {
            logger.error("Error stopping RVNKCore API server", e);
            logger.error("Server shutdown error details: " + e.getMessage());
            
            if (e.getCause() != null) {
                logger.error("Shutdown root cause: " + e.getCause().getMessage());
            }
        } finally {
            isShuttingDown = false;
        }
    }

    /**
     * Checks if the server is currently running.
     *
     * @param server The Jetty server instance to check
     * @return true if the server is running, false otherwise
     */
    public boolean isServerRunning(Server server) {
        return server != null && server.isStarted() && !isShuttingDown;
    }

    /**
     * Performs a graceful restart of the server.
     *
     * @param server The Jetty server instance to restart
     * @throws ServerStartupException if restart fails
     */
    public void restartServer(Server server) throws ServerStartupException {
        logger.info("Restarting RVNKCore API server...");
        
        stopServer(server);
        
        // Brief pause to ensure clean shutdown
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warning("Server restart interrupted during sleep");
        }
        
        startServer(server);
    }

    /**
     * Logs successful startup information.
     */
    private void logSuccessfulStartup() {
        String protocol = config.isHttpsEnabled() ? "HTTPS" : "HTTP";
        int port = config.isHttpsEnabled() ? config.getHttpsPort() : config.getHttpPort();
        String endpoint = (config.isHttpsEnabled() ? "https" : "http") + "://localhost:" + port + config.getContextPath();
        
        logger.info("=== RVNKCore REST API Server Started ===");
        logger.info(protocol + " Endpoint: " + endpoint);
        logger.info("Authentication: " + (config.getApiKey() != null && !config.getApiKey().isEmpty() ? "API Key Required" : "Open Access"));
        logger.info("==========================================");
    }

    /**
     * Handles and logs startup errors with detailed analysis.
     *
     * @param e The exception that occurred during startup
     */
    private void handleStartupError(Exception e) {
        logger.error("Failed to start RVNKCore REST API server", e);
        logger.error("Server startup failed with error: " + e.getMessage());
        logger.error("Error type: " + e.getClass().getSimpleName());
        
        // Analyze common startup errors
        analyzeStartupError(e);
        
        if (e.getCause() != null) {
            logger.error("Root cause: " + e.getCause().getMessage());
            logger.error("Root cause type: " + e.getCause().getClass().getSimpleName());
        }
    }

    /**
     * Analyzes startup errors and provides helpful troubleshooting information.
     *
     * @param e The exception that occurred during startup
     */
    private void analyzeStartupError(Exception e) {
        String errorMessage = e.getMessage().toLowerCase();
        
        if (errorMessage.contains("address already in use")) {
            logger.error("Port conflict detected!");
            logger.error("  - HTTP Port " + config.getHttpPort() + " may already be in use");
            if (config.isHttpsEnabled()) {
                logger.error("  - HTTPS Port " + config.getHttpsPort() + " may already be in use");
            }
            logger.error("  - Try changing ports in your configuration or stop conflicting services");
            
        } else if (errorMessage.contains("permission denied")) {
            logger.error("Permission denied - this may be due to:");
            logger.error("  - Insufficient privileges to bind to port " + config.getHttpPort());
            logger.error("  - Try using a port above 1024 or run with elevated privileges");
            
        } else if (errorMessage.contains("keystore")) {
            logger.error("SSL/Keystore related error:");
            logger.error("  - Check keystore file exists: " + config.getKeystorePath());
            logger.error("  - Verify keystore password is correct");
            logger.error("  - Ensure keystore format is supported (JKS/PKCS12)");
            
        } else if (errorMessage.contains("timeout")) {
            logger.error("Timeout during server startup:");
            logger.error("  - Server may be taking longer than expected to start");
            logger.error("  - Check system resources and load");
            logger.error("  - Consider increasing timeout values");
        }
    }

    /**
     * Gets the current shutdown status.
     *
     * @return true if server is currently shutting down, false otherwise
     */
    public boolean isShuttingDown() {
        return isShuttingDown;
    }

    /**
     * Exception thrown when server startup fails.
     */
    public static class ServerStartupException extends Exception {
        public ServerStartupException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
