package org.fourz.rvnkcore.api.server.jetty;

import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.SecureRequestCustomizer;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.SslConnectionFactory;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.fourz.rvnkcore.api.config.ApiConfig;
import org.fourz.rvnktools.api.security.KeyStoreGenerator;
import org.fourz.rvnkcore.util.log.LogManager;
import org.bukkit.plugin.Plugin;

import java.io.File;

/**
 * RVNKCoreSSLFactory: Handles SSL/TLS configuration and HTTPS connector setup for RVNKCore API server.
 * Provides centralized SSL context factory management and keystore generation.
 *
 * This class follows the RVNKCore naming conventions and is part of the specialized factory architecture:
 * - {@link ServerSSLFactory} for SSL/TLS and HTTPS
 * - {@link ServerCacheFactory} for caching
 * - {@link ServerSecurityFactory} for security
 * - {@link ServerMonitoringFactory} for monitoring
 *
 * @see ServerCacheFactory
 * @see ServerSecurityFactory
 * @see ServerMonitoringFactory
 */
public class ServerSSLFactory {
    private final ApiConfig config;
    private final Plugin plugin;
    private final LogManager logger;

    /**
     * Creates a new RVNKCoreSSLFactory instance.
     *
     * @param config API configuration containing SSL settings
     * @param plugin Plugin instance for file system access
     * @param logger Logger instance for SSL operations
     */
    public ServerSSLFactory(ApiConfig config, Plugin plugin, LogManager logger) {
        this.config = config;
        this.plugin = plugin;
        this.logger = logger;
    }

    /**
     * Creates and configures an HTTPS connector for the given Jetty server.
     *
     * @param server The Jetty server instance
     * @param httpConfig Base HTTP configuration to extend for HTTPS
     * @return Configured HTTPS ServerConnector, or null if setup failed
     */
    public ServerConnector createHttpsConnector(Server server, HttpConfiguration httpConfig) {
        if (!config.isHttpsEnabled()) {
            return null;
        }

        if (config.getKeystorePath().isEmpty()) {
            logger.warning("HTTPS enabled but keystore path is empty - HTTPS unavailable");
            return null;
        }

        try {
            // Ensure keystore exists
            File keystoreFile = ensureKeystoreExists();
            if (keystoreFile == null) {
                return null;
            }

            // Create SSL context factory
            SslContextFactory.Server sslContextFactory = createSslContextFactory(keystoreFile);

            // Create HTTPS configuration
            HttpConfiguration httpsConfig = new HttpConfiguration(httpConfig);
            httpsConfig.addCustomizer(new SecureRequestCustomizer());

            // Create HTTPS connector
            ServerConnector httpsConnector = new ServerConnector(server,
                    new SslConnectionFactory(sslContextFactory, "http/1.1"),
                    new HttpConnectionFactory(httpsConfig));
            
            httpsConnector.setPort(config.getHttpsPort());
            httpsConnector.setIdleTimeout(config.getIdleTimeout());
            httpsConnector.setName("https-connector");

            return httpsConnector;

        } catch (Exception e) {
            logger.error("Failed to create HTTPS connector on port " + config.getHttpsPort(), e);
            logSSLError(e);
            return null;
        }
    }

    /**
     * Ensures the keystore file exists, generating it if necessary.
     * Used by RVNKCoreSSLFactory for HTTPS setup.
     *
     * @return The keystore file, or null if creation failed
     */
    private File ensureKeystoreExists() {
        File keystoreFile = new File(plugin.getDataFolder(), config.getKeystorePath());
        
        if (!keystoreFile.exists()) {
            logger.info("Keystore file does not exist, generating new keystore...");
            logger.info("Generating keystore at: " + keystoreFile.getAbsolutePath());
            
            try {
                // Ensure parent directory exists
                keystoreFile.getParentFile().mkdirs();
                
                KeyStoreGenerator.generateKeyStore(
                    keystoreFile.getAbsolutePath(), 
                    config.getKeystorePassword(), 
                    "jetty"
                );
                logger.info("Keystore generated successfully at: " + keystoreFile.getAbsolutePath());
            } catch (Exception e) {
                logger.error("Failed to generate keystore", e);
                logger.error("HTTPS setup aborted due to keystore generation failure");
                return null;
            }
        } else {
            logger.info("Using existing keystore: " + keystoreFile.getAbsolutePath());
        }
        
        return keystoreFile;
    }

    /**
     * Creates and configures the SSL context factory for Jetty HTTPS connector.
     *
     * @param keystoreFile The keystore file to use
     * @return Configured SslContextFactory.Server instance
     */
    private SslContextFactory.Server createSslContextFactory(File keystoreFile) {
        SslContextFactory.Server sslContextFactory = new SslContextFactory.Server();
        sslContextFactory.setKeyStorePath(keystoreFile.getAbsolutePath());
        sslContextFactory.setKeyStorePassword(config.getKeystorePassword());
        
        // Additional SSL security configurations
        sslContextFactory.setExcludeProtocols("SSLv3", "TLSv1", "TLSv1.1");
        sslContextFactory.setIncludeProtocols("TLSv1.2", "TLSv1.3");
        
        return sslContextFactory;
    }

    /**
     * Logs detailed SSL error information for troubleshooting.
     *
     * @param e The exception that occurred during SSL setup
     */
    private void logSSLError(Exception e) {
        logger.error("HTTPS setup error details: " + e.getMessage());
        logger.error("HTTPS Configuration Issues:");
        logger.error("  - Keystore Path: " + config.getKeystorePath());
        
        File keystoreFile = new File(plugin.getDataFolder(), config.getKeystorePath());
        logger.error("  - Keystore Exists: " + keystoreFile.exists());
        logger.error("  - Keystore Absolute Path: " + keystoreFile.getAbsolutePath());
        logger.error("  - Port Availability: Check if port " + config.getHttpsPort() + " is available");
        
        if (e.getCause() != null) {
            logger.error("HTTPS root cause: " + e.getCause().getMessage());
            logger.error("HTTPS root cause type: " + e.getCause().getClass().getSimpleName());
        }
    }

    /**
     * Validates SSL configuration before attempting to create connectors.
     *
     * @return true if SSL configuration is valid, false otherwise
     */
    public boolean validateSSLConfiguration() {
        if (!config.isHttpsEnabled()) {
            return true; // Valid to have HTTPS disabled
        }

        if (config.getKeystorePath().isEmpty()) {
            logger.error("HTTPS enabled but keystore path is empty");
            return false;
        }

        if (config.getKeystorePassword().isEmpty()) {
            logger.warning("HTTPS enabled but keystore password is empty - using default");
        }

        if (config.getHttpsPort() <= 0 || config.getHttpsPort() > 65535) {
            logger.error("Invalid HTTPS port: " + config.getHttpsPort());
            return false;
        }

        return true;
    }
}
