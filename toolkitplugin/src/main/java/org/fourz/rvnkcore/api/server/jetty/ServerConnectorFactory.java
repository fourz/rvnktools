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
     * If HTTPS is enabled, HTTP will be disabled for security.
     *
     * @param server The Jetty server instance to configure
     * @throws RuntimeException if connector setup fails
     */
    public void setupConnectors(Server server) {
        HttpConfiguration httpConfig = createBaseHttpConfiguration();

        // If HTTPS is enabled, disable HTTP for security
        if (config.isHttpsEnabled()) {
            logger.info("HTTPS enabled - setting up secure connector (HTTP disabled)");
            ServerConnector httpsConnector = sslFactory.createHttpsConnector(server, httpConfig);
            if (httpsConnector != null) {
                server.addConnector(httpsConnector);
                logger.info("HTTPS connector active on port " + config.getHttpsPort());
            } else {
                logger.error("HTTPS setup failed, falling back to HTTP");
                createAndAddHttpConnector(server, httpConfig);
            }
        } else {
            // HTTP only mode
            logger.info("HTTP mode - setting up HTTP connector");
            createAndAddHttpConnector(server, httpConfig);
        }
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
     * Creates and adds an HTTP connector to the server.
     *
     * @param server The Jetty server instance
     * @param httpConfig Base HTTP configuration
     * @throws RuntimeException if HTTP connector creation fails
     */
    private void createAndAddHttpConnector(Server server, HttpConfiguration httpConfig) {
        try {
            ServerConnector httpConnector = new ServerConnector(server, 
                    new HttpConnectionFactory(httpConfig));
            httpConnector.setPort(config.getHttpPort());
            httpConnector.setIdleTimeout(config.getIdleTimeout());
            httpConnector.setName("http-connector");
            
            server.addConnector(httpConnector);
            logger.info("HTTP connector active on port " + config.getHttpPort());
            
        } catch (Exception e) {
            logger.error("Failed to create HTTP connector on port " + config.getHttpPort(), e);
            throw new RuntimeException("HTTP connector setup failed", e);
        }
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
