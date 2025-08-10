package org.fourz.rvnkcore.api.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;
import org.fourz.rvnktools.util.log.LogManager;
import org.fourz.rvnktools.util.Debug;

import java.util.logging.Level;

/**
 * Configuration management for RVNKCore REST API services.
 * Provides centralized configuration with environment-specific overrides
 * and runtime configuration updates.
 */
public class ApiConfig {
    private final int httpPort;
    private final int httpsPort;
    private final String apiKey;
    private final boolean enabled;
    private final String host;
    private final boolean corsEnabled;
    private final String corsAllowedOrigins;
    private final String corsAllowedMethods;
    private final int maxThreads;
    private final int idleTimeout;
    private final boolean httpsEnabled;
    private final String keystorePath;
    private final String keystorePassword;
    private final boolean sendServerVersion;
    private final String contextPath;
    private final int connectionTimeout;
    private final boolean useForwardedHeaders;
    private final String[] allowedIPs;
    private final Level apiLogLevel;
    private final Level globalLogLevel;
    
    private final LogManager logger;

    /**
     * Creates ApiConfig from plugin configuration with default values.
     *
     * @param plugin The plugin instance for configuration access
     */
    public ApiConfig(Plugin plugin) {
        this(plugin, plugin.getConfig());
    }
    
    /**
     * Creates ApiConfig from specific FileConfiguration with plugin context.
     *
     * @param plugin The plugin instance for logging context
     * @param config The configuration to read from
     */
    public ApiConfig(Plugin plugin, FileConfiguration config) {
        this.logger = LogManager.getInstance(plugin);
        
        // Read global log level first and apply it
        String logLevelStr = config.getString("logging.level", "INFO");
        this.globalLogLevel = Debug.getLevel(logLevelStr);
        
        this.enabled = config.getBoolean("api.enabled", false);
        this.host = config.getString("api.host", "localhost");
        this.httpPort = config.getInt("api.http.port", 8080);
        this.httpsPort = config.getInt("api.https.port", 8081);
        this.apiKey = config.getString("api.auth.key", "changeme");
        this.corsEnabled = config.getBoolean("api.cors.enabled", true);
        this.corsAllowedOrigins = config.getString("api.cors.allowed-origins", "*");
        this.corsAllowedMethods = config.getString("api.cors.allowed-methods", "GET,POST,PUT,DELETE,OPTIONS");
        this.maxThreads = config.getInt("api.server.max-threads", 50);
        this.idleTimeout = config.getInt("api.server.idle-timeout", 30000);
        this.httpsEnabled = config.getBoolean("api.https.enabled", false);
        this.keystorePath = config.getString("api.https.keystore-path", "");
        this.keystorePassword = config.getString("api.https.keystore-password", "");
        this.sendServerVersion = config.getBoolean("api.server.send-version", false);
        
        // Read API-specific log level, defaulting to global log level
        String apiLogLevelStr = config.getString("api.logging.level", logLevelStr);
        this.apiLogLevel = Debug.getLevel(apiLogLevelStr);
        
        this.contextPath = config.getString("api.context-path", "/api");
        this.connectionTimeout = config.getInt("api.server.connection-timeout", 60000);
        this.useForwardedHeaders = config.getBoolean("api.server.use-forwarded-headers", true);
        
        // Parse allowed IPs
        String allowedIPsStr = config.getString("api.security.allowed-ips", "");
        this.allowedIPs = allowedIPsStr.isEmpty() ? new String[0] : allowedIPsStr.split(",");
        
        // Log configuration summary
        String apiLogStr = apiLogLevelStr.equals(logLevelStr) ? "inherits global" : apiLogLevelStr;
        logger.info("RVNKCore API configuration loaded - Enabled: " + enabled + 
                   ", Global Log Level: " + logLevelStr + 
                   ", API Log Level: " + apiLogStr + 
                   ", HTTP Port: " + httpPort + 
                   ", HTTPS Port: " + httpsPort);
    }

    // Getters
    public boolean isEnabled() { return enabled; }
    public String getHost() { return host; }
    public int getHttpPort() { return httpPort; }
    public int getHttpsPort() { return httpsPort; }
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
    public Level getApiLogLevel() { return apiLogLevel; }
    public boolean isApiDebugLogging() { return apiLogLevel == Level.FINE; }
    public Level getGlobalLogLevel() { return globalLogLevel; }
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
            if (httpPort <= 0 || httpPort > 65535) {
                logger.error("Invalid HTTP port: " + httpPort);
                isValid = false;
            }
            
            if (httpsEnabled && (httpsPort <= 0 || httpsPort > 65535)) {
                logger.error("Invalid HTTPS port: " + httpsPort);
                isValid = false;
            }
            
            if (httpPort == httpsPort && httpsEnabled) {
                logger.error("HTTP and HTTPS ports cannot be the same: " + httpPort);
                isValid = false;
            }
            
            if (apiKey == null || apiKey.trim().isEmpty() || "changeme".equals(apiKey.trim())) {
                logger.warning("API key is set to default value 'changeme' - please change for security");
            }
            
            if (httpsEnabled && (keystorePath == null || keystorePath.trim().isEmpty())) {
                logger.error("HTTPS enabled but keystore path not specified");
                isValid = false;
            }
            
            if (maxThreads <= 0) {
                logger.error("Invalid max threads value: " + maxThreads);
                isValid = false;
            }
            
            if (connectionTimeout <= 0) {
                logger.error("Invalid connection timeout: " + connectionTimeout);
                isValid = false;
            }
            
            if (idleTimeout <= 0) {
                logger.error("Invalid idle timeout: " + idleTimeout);
                isValid = false;
            }
        }
        
        return isValid;
    }
}
