package org.fourz.rvnkcore.api.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;
import org.fourz.rvnkcore.util.log.LogManager;

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
    private final String[] sanHostnames;
    private final Level apiLogLevel;
    private final Level globalLogLevel;
    
    private final LogManager logger;

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
        this.globalLogLevel = LogManager.parseLevel(logLevelStr);
        
        this.enabled = config.getBoolean("api.enabled", false);
        this.host = config.getString("api.host", "localhost");
        this.httpPort = config.getInt("api.http.port", 8080);
        this.httpsPort = config.getInt("api.https.port", 8081);
        this.apiKey = config.getString("api.auth.key", "changeme");
        this.corsEnabled = config.getBoolean("api.cors.enabled", true);
        this.corsAllowedOrigins = config.getString("api.cors.allowed-origins", "*");
        this.corsAllowedMethods = config.getString("api.cors.allowed-methods", "GET,POST,PUT,PATCH,DELETE,OPTIONS");
        this.maxThreads = config.getInt("api.server.max-threads", 50);
        this.idleTimeout = config.getInt("api.server.idle-timeout", 30000);
        this.httpsEnabled = config.getBoolean("api.https.enabled", false);
        this.keystorePath = config.getString("api.https.keystore-path", "");
        this.keystorePassword = config.getString("api.https.keystore-password", "");
        this.sendServerVersion = config.getBoolean("api.server.send-version", false);
        
        // Read API-specific log level, defaulting to global log level
        String apiLogLevelStr = config.getString("api.logging.level", logLevelStr);
        this.apiLogLevel = LogManager.parseLevel(apiLogLevelStr);
        
        this.contextPath = config.getString("api.context-path", "/api");
        this.connectionTimeout = config.getInt("api.server.connection-timeout", 60000);
        this.useForwardedHeaders = config.getBoolean("api.server.use-forwarded-headers", true);
        
        // Parse allowed IPs
        String allowedIPsStr = config.getString("api.security.allowed-ips", "");
        this.allowedIPs = allowedIPsStr.isEmpty() ? new String[0] : allowedIPsStr.split(",");

        // Parse SAN hostnames for TLS cert generation (includes api.host if not localhost)
        this.sanHostnames = parseSanHostnames(host, config.getString("api.https.san-hostnames", ""));

        // Log configuration summary
        String apiLogStr = apiLogLevelStr.equals(logLevelStr) ? "inherits global" : apiLogLevelStr;
        logger.info("RVNKCore API configuration loaded - Enabled: " + enabled +
                   ", Global Log Level: " + logLevelStr +
                   ", API Log Level: " + apiLogStr +
                   ", HTTP Port: " + httpPort +
                   ", HTTPS Port: " + httpsPort);
    }

    /**
     * Creates ApiConfig from ConfigurationSection with plugin context.
     * This method is used by ConfigLoader to create ApiConfig from the API section.
     *
     * @param plugin The plugin instance for logging context
     * @param apiSection The API configuration section from the core config
     * @param globalLogLevel The global log level string from the core config
     * @return ApiConfig instance
     */
    public static ApiConfig fromConfigurationSection(Plugin plugin, ConfigurationSection apiSection, String globalLogLevel) {
        LogManager logger = LogManager.getInstance(plugin);
        
        // Parse global log level
        Level parsedGlobalLogLevel = LogManager.parseLevel(globalLogLevel);
        
        // Create a private constructor call
        return new ApiConfig(plugin, apiSection, parsedGlobalLogLevel, logger);
    }
    
    /**
     * Private constructor used by the static factory method.
     */
    private ApiConfig(Plugin plugin, ConfigurationSection apiSection, Level globalLogLevel, LogManager logger) {
        this.logger = logger;
        this.globalLogLevel = globalLogLevel;
        
        this.enabled = apiSection.getBoolean("enabled", false);
        this.host = apiSection.getString("host", "localhost");
        this.httpPort = apiSection.getInt("http.port", 8080);
        this.httpsPort = apiSection.getInt("https.port", 8081);
        this.apiKey = apiSection.getString("auth.key", "changeme");
        this.corsEnabled = apiSection.getBoolean("cors.enabled", true);
        this.corsAllowedOrigins = apiSection.getString("cors.allowed-origins", "*");
        this.corsAllowedMethods = apiSection.getString("cors.allowed-methods", "GET,POST,PUT,PATCH,DELETE,OPTIONS");
        this.maxThreads = apiSection.getInt("server.max-threads", 50);
        this.idleTimeout = apiSection.getInt("server.idle-timeout", 30000);
        this.httpsEnabled = apiSection.getBoolean("https.enabled", false);
        this.keystorePath = apiSection.getString("https.keystore-path", "");
        this.keystorePassword = apiSection.getString("https.keystore-password", "");
        this.sendServerVersion = apiSection.getBoolean("server.send-version", false);
        
        // Read API-specific log level, defaulting to global log level
        String apiLogLevelStr = apiSection.getString("logging.level", globalLogLevel.getName());
        this.apiLogLevel = LogManager.parseLevel(apiLogLevelStr);
        
        this.contextPath = apiSection.getString("context-path", "/api");
        this.connectionTimeout = apiSection.getInt("server.connection-timeout", 60000);
        this.useForwardedHeaders = apiSection.getBoolean("server.use-forwarded-headers", true);
        
        // Parse allowed IPs
        String allowedIPsStr = apiSection.getString("security.allowed-ips", "");
        this.allowedIPs = allowedIPsStr.isEmpty() ? new String[0] : allowedIPsStr.split(",");

        // Parse SAN hostnames for TLS cert generation (includes api.host if not localhost)
        this.sanHostnames = parseSanHostnames(host, apiSection.getString("https.san-hostnames", ""));

        // Log configuration summary
        String apiLogStr = apiLogLevelStr.equals(globalLogLevel.getName()) ? "inherits global" : apiLogLevelStr;
        logger.info("RVNKCore API configuration loaded - Enabled: " + enabled +
                   ", Global Log Level: " + globalLogLevel.getName() +
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
    public String[] getSanHostnames() { return sanHostnames; }

    /**
     * Builds the SAN hostname list from the configured host and explicit san-hostnames value.
     */
    private static String[] parseSanHostnames(String host, String sanHostnamesStr) {
        java.util.Set<String> hostnames = new java.util.LinkedHashSet<>();
        if (host != null && !host.trim().isEmpty() && !"localhost".equalsIgnoreCase(host.trim())) {
            hostnames.add(host.trim());
        }
        if (sanHostnamesStr != null && !sanHostnamesStr.trim().isEmpty()) {
            for (String h : sanHostnamesStr.split(",")) {
                if (!h.trim().isEmpty() && !"localhost".equalsIgnoreCase(h.trim())) {
                    hostnames.add(h.trim());
                }
            }
        }
        return hostnames.toArray(new String[0]);
    }

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
