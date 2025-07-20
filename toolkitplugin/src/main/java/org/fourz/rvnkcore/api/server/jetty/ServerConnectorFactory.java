package org.fourz.rvnkcore.api.server.jetty;

import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.fourz.rvnkcore.api.config.ApiConfig;
import org.fourz.rvnktools.util.log.LogManager;
import org.bukkit.plugin.Plugin;

/**
 * Factory for creating and configuring HTTP and HTTPS connectors for the RVNKCore API server.
 * Provides centralized connector management with consistent configuration.
 */
public class ServerConnectorFactory {
    private final ApiConfig config;
    private final LogManager logger;
    private final ServerSSLFactory sslFactory;

    /**
     * Creates a new connector factory instance.
     *
     * @param config API configuration
     * @param plugin Plugin instance
     * @param logger Logger instance
     */
    public ServerConnectorFactory(ApiConfig config, Plugin plugin, LogManager logger) {
        this.config = config;
        this.logger = logger;
        this.sslFactory = new ServerSSLFactory(config, plugin, logger);
    }

    /**
     * Sets up all connectors (HTTP and HTTPS) for the given server.
     *
     * @param server The Jetty server instance to configure
     * @throws RuntimeException if HTTP connector setup fails
     */
    public void setupConnectors(Server server) {
        logger.info("Setting up HTTP/HTTPS connectors...");
        logConnectorConfiguration();

        HttpConfiguration httpConfig = createBaseHttpConfiguration();

        // Create and add HTTP connector
        ServerConnector httpConnector = createHttpConnector(server, httpConfig);
        server.addConnector(httpConnector);

        // Create and add HTTPS connector if enabled
        if (config.isHttpsEnabled()) {
            logger.info("HTTPS is enabled, setting up HTTPS connector...");
            ServerConnector httpsConnector = sslFactory.createHttpsConnector(server, httpConfig);
            if (httpsConnector != null) {
                server.addConnector(httpsConnector);
            }
        } else {
            logger.info("HTTPS is disabled in configuration");
        }

        logger.info("Connectors setup completed");
    }

    /**
     * Creates the base HTTP configuration used by both HTTP and HTTPS connectors.
     *
     * @return Configured HttpConfiguration instance
     */
    private HttpConfiguration createBaseHttpConfiguration() {
        HttpConfiguration httpConfig = new HttpConfiguration();
        httpConfig.setSendServerVersion(config.isSendServerVersion());
        httpConfig.setSendDateHeader(true);
        httpConfig.setRequestHeaderSize(8192);
        httpConfig.setResponseHeaderSize(8192);
        
        return httpConfig;
    }

    /**
     * Creates and configures the HTTP connector.
     *
     * @param server The Jetty server instance
     * @param httpConfig Base HTTP configuration
     * @return Configured HTTP ServerConnector
     * @throws RuntimeException if HTTP connector creation fails
     */
    private ServerConnector createHttpConnector(Server server, HttpConfiguration httpConfig) {
        logger.info("Creating HTTP connector on port " + config.getHttpPort());
        
        try {
            ServerConnector httpConnector = new ServerConnector(server, 
                    new HttpConnectionFactory(httpConfig));
            httpConnector.setPort(config.getHttpPort());
            httpConnector.setIdleTimeout(config.getIdleTimeout());
            httpConnector.setName("http-connector");
            
            logger.info("HTTP connector successfully created");
            logger.info("HTTP endpoint will be available at: http://localhost:" + config.getHttpPort() + config.getContextPath());
            
            return httpConnector;
            
        } catch (Exception e) {
            logger.error("Failed to create HTTP connector on port " + config.getHttpPort(), e);
            logger.error("HTTP connector error details: " + e.getMessage());
            throw new RuntimeException("HTTP connector setup failed", e);
        }
    }

    /**
     * Logs connector configuration details for debugging.
     */
    private void logConnectorConfiguration() {
        logger.info("Connector Configuration:");
        logger.info("  - HTTP Port: " + config.getHttpPort());
        logger.info("  - HTTPS Enabled: " + config.isHttpsEnabled());
        if (config.isHttpsEnabled()) {
            logger.info("  - HTTPS Port: " + config.getHttpsPort());
        }
        logger.info("  - Idle Timeout: " + config.getIdleTimeout() + "ms");
        logger.info("  - Send Server Version: " + config.isSendServerVersion());
    }

    /**
     * Validates connector configuration before attempting to create connectors.
     *
     * @return true if configuration is valid, false otherwise
     */
    public boolean validateConfiguration() {
        boolean isValid = true;

        // Validate HTTP port
        if (config.getHttpPort() <= 0 || config.getHttpPort() > 65535) {
            logger.error("Invalid HTTP port: " + config.getHttpPort());
            isValid = false;
        }

        // Validate HTTPS configuration if enabled
        if (config.isHttpsEnabled()) {
            if (!sslFactory.validateSSLConfiguration()) {
                isValid = false;
            }

            // Check for port conflicts
            if (config.getHttpPort() == config.getHttpsPort()) {
                logger.error("HTTP and HTTPS ports cannot be the same: " + config.getHttpPort());
                isValid = false;
            }
        }

        return isValid;
    }

    /**
     * Gets the SSL factory instance for advanced SSL operations.
     *
     * @return The RVNKCoreSSLFactory instance
     */
    public ServerSSLFactory getSSLFactory() {
        return sslFactory;
    }
}
