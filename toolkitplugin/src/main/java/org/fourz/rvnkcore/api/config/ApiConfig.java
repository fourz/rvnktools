package org.fourz.rvnkcore.api.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;
import org.fourz.rvnktools.util.log.LogManager;

/**
 * Configuration management for RVNKCore REST API services.
 * Provides centralized configuration with environment-specific overrides
 * and runtime configuration updates.
 */
public class ApiConfig {
    private final int port;
    private final String apiKey;
    private final boolean enabled;
    private final boolean corsEnabled;
    private final String corsAllowedOrigins;
    private final String corsAllowedMethods;
    private final int maxThreads;
    private final int idleTimeout;
    private final boolean httpsEnabled;
    private final String keystorePath;
    private final String keystorePassword;
    private final boolean sendServerVersion;
    private final boolean loggingEnabled;
    private final String contextPath;
    private final int connectionTimeout;
    private final boolean useForwardedHeaders;
    private final String[] allowedIPs;
    
    private final LogManager logger;

    /**
     * Creates ApiConfig from plugin configuration with default values.
     *
     * @param plugin The plugin instance for configuration access
     */
    public ApiConfig(Plugin plugin) {
        this.logger = LogManager.getInstance(plugin);
        
        // Load configuration with defaults
        FileConfiguration config = plugin.getConfig();
        
        this.enabled = config.getBoolean("api.enabled", false);
        this.port = config.getInt("api.port", 8080);
        this.apiKey = config.getString("api.key", "default-api-key");
        this.corsEnabled = config.getBoolean("api.cors.enabled", true);
        this.corsAllowedOrigins = config.getString("api.cors.allowed-origins", "*");
        this.corsAllowedMethods = config.getString("api.cors.allowed-methods", "GET,POST,PUT,DELETE,OPTIONS");
        this.maxThreads = config.getInt("api.server.max-threads", 50);
        this.idleTimeout = config.getInt("api.server.idle-timeout", 30000);
        this.httpsEnabled = config.getBoolean("api.https.enabled", false);
        this.keystorePath = config.getString("api.https.keystore-path", "");
        this.keystorePassword = config.getString("api.https.keystore-password", "");
        this.sendServerVersion = config.getBoolean("api.server.send-version", false);
        this.loggingEnabled = config.getBoolean("api.logging.enabled", true);
        this.contextPath = config.getString("api.context-path", "/api");
        this.connectionTimeout = config.getInt("api.server.connection-timeout", 60000);
        this.useForwardedHeaders = config.getBoolean("api.server.use-forwarded-headers", true);
        
        // Parse allowed IPs
        String allowedIPsStr = config.getString("api.security.allowed-ips", "");
        this.allowedIPs = allowedIPsStr.isEmpty() ? new String[0] : allowedIPsStr.split(",");
        
        logger.info("RVNKCore API configuration loaded - Enabled: " + enabled + ", Port: " + port);
    }

    // Getters
    public boolean isEnabled() { return enabled; }
    public int getPort() { return port; }
    public String getApiKey() { return apiKey; }
    public boolean isCorsEnabled() { return corsEnabled; }
    public String getCorsAllowedOrigins() { return corsAllowedOrigins; }
    public String getCorsAllowedMethods() { return corsAllowedMethods; }
    public int getMaxThreads() { return maxThreads; }
    public int getIdleTimeout() { return idleTimeout; }
    public boolean isHttpsEnabled() { return httpsEnabled; }
    public String getKeystorePath() { return keystorePath; }
    public String getKeystorePassword() { return keystorePassword; }
    public boolean isSendServerVersion() { return sendServerVersion; }
    public boolean isLoggingEnabled() { return loggingEnabled; }
    public String getContextPath() { return contextPath; }
    public int getConnectionTimeout() { return connectionTimeout; }
    public boolean isUseForwardedHeaders() { return useForwardedHeaders; }
    public String[] getAllowedIPs() { return allowedIPs; }

    /**
     * Validates the configuration and logs any issues.
     *
     * @return true if configuration is valid, false otherwise
     */
    public boolean validate() {
        boolean isValid = true;
        
        if (enabled) {
            if (port <= 0 || port > 65535) {
                logger.error("Invalid API port: " + port);
                isValid = false;
            }
            
            if (apiKey == null || apiKey.trim().isEmpty()) {
                logger.error("API key cannot be empty");
                isValid = false;
            }
            
            if (httpsEnabled && (keystorePath == null || keystorePath.trim().isEmpty())) {
                logger.error("HTTPS enabled but keystore path not specified");
                isValid = false;
            }
        }
        
        return isValid;
    }
}
