package org.fourz.rvnktools.api.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import java.io.File;

public class RestConfig {
    private final int port;
    private final String apiKey;
    private final boolean enabled;
    private final boolean corsEnabled;
    private final String corsAllowedOrigins;
    private final String corsAllowedMethods;
    private final int maxThreads;
    private final int idleTimeout;
    private final int requestHeaderSize;
    private final int responseHeaderSize;
    private final boolean sendServerVersion;
    private final boolean sendDateHeader;
    private final boolean loggingEnabled;
    private final int logRetainDays;
    private final String logFilename;
    private final String contextPath;
    private final int minThreads;
    private final int queueSize;
    private final int connectionTimeout;
    private final boolean useForwardedHeaders;
    private final String[] allowedIPs;
    private final int rateLimitRequests;
    private final int rateLimitPeriod;
    private final boolean compressionEnabled;
    private final int compressionMinSize;
    private final boolean tlsEnabled;
    private final String keystorePath;
    private final String keystorePassword;
    private final String keyManagerPassword;
    private final int httpsPort;

    public RestConfig(Plugin plugin) {
        File configFile = new File(plugin.getDataFolder(), "config.yml");
        
        if (!configFile.exists()) {
            plugin.saveResource("config.yml", false);
        }
        
        FileConfiguration config = YamlConfiguration.loadConfiguration(configFile);
        
        this.enabled = config.getBoolean("api.enabled", false);
        this.port = config.getInt("api.port", 8080);
        this.apiKey = config.getString("api.key", "changeme");
        this.contextPath = config.getString("api.context-path", "/api");
        
        // CORS settings
        this.corsEnabled = config.getBoolean("api.cors.enabled", false);
        this.corsAllowedOrigins = config.getString("api.cors.allowed-origins", "*");
        this.corsAllowedMethods = config.getString("api.cors.allowed-methods", "GET,POST,DELETE");
        
        // Server settings
        this.minThreads = config.getInt("api.server.min-threads", 2);
        this.maxThreads = config.getInt("api.server.max-threads", 8);
        this.queueSize = config.getInt("api.server.queue-size", 100);
        this.idleTimeout = config.getInt("api.server.idle-timeout", 30000);
        this.connectionTimeout = config.getInt("api.server.connection-timeout", 5000);
        
        // Security settings
        this.useForwardedHeaders = config.getBoolean("api.security.use-forwarded-headers", false);
        this.allowedIPs = config.getStringList("api.security.allowed-ips").toArray(new String[0]);
        this.rateLimitRequests = config.getInt("api.security.rate-limit.requests", 30);
        this.rateLimitPeriod = config.getInt("api.security.rate-limit.period", 60);
        this.requestHeaderSize = config.getInt("api.security.request-header-size", 8192);
        this.responseHeaderSize = config.getInt("api.security.response-header-size", 8192);
        this.sendServerVersion = config.getBoolean("api.security.send-server-version", false);
        this.sendDateHeader = config.getBoolean("api.security.send-date-header", true);
        
        // Performance settings
        this.compressionEnabled = config.getBoolean("api.performance.compression", true);
        this.compressionMinSize = config.getInt("api.performance.compression-min-size", 2048);
        
        // Logging settings
        this.loggingEnabled = config.getBoolean("api.logging.enabled", true);
        this.logRetainDays = config.getInt("api.logging.retain-days", 7);
        this.logFilename = config.getString("api.logging.filename", "api-access-%d{yyyy-MM-dd}.log");
        
        // TLS settings
        this.tlsEnabled = config.getBoolean("api.tls.enabled", false);
        this.httpsPort = config.getInt("api.tls.port", 8443);
        this.keystorePath = config.getString("api.tls.keystore-path", "keystore.jks");
        this.keystorePassword = config.getString("api.tls.keystore-password", "changeme");
        this.keyManagerPassword = config.getString("api.tls.keymanager-password", "changeme");
    }

    // Add getters for all fields
    public int getPort() {
        return port;
    }

    public String getApiKey() {
        return apiKey;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isCorsEnabled() {
        return corsEnabled;
    }

    public String getCorsAllowedOrigins() {
        return corsAllowedOrigins;
    }

    public String getCorsAllowedMethods() {
        return corsAllowedMethods;
    }

    public int getMaxThreads() {
        return maxThreads;
    }

    public int getIdleTimeout() {
        return idleTimeout;
    }

    public int getRequestHeaderSize() {
        return requestHeaderSize;
    }

    public int getResponseHeaderSize() {
        return responseHeaderSize;
    }

    public boolean isSendServerVersion() {
        return sendServerVersion;
    }

    public boolean isSendDateHeader() {
        return sendDateHeader;
    }

    public boolean isLoggingEnabled() {
        return loggingEnabled;
    }

    public int getLogRetainDays() {
        return logRetainDays;
    }

    public String getLogFilename() {
        return logFilename;
    }

    public String getContextPath() {
        return contextPath;
    }

    public int getMinThreads() {
        return minThreads;
    }

    public int getQueueSize() {
        return queueSize;
    }

    public int getConnectionTimeout() {
        return connectionTimeout;
    }

    public boolean isUseForwardedHeaders() {
        return useForwardedHeaders;
    }

    public String[] getAllowedIPs() {
        return allowedIPs;
    }

    public int getRateLimitRequests() {
        return rateLimitRequests;
    }

    public int getRateLimitPeriod() {
        return rateLimitPeriod;
    }

    public boolean isCompressionEnabled() {
        return compressionEnabled;
    }

    public int getCompressionMinSize() {
        return compressionMinSize;
    }

    public boolean isTlsEnabled() {
        return tlsEnabled;
    }

    public String getKeystorePath() {
        return keystorePath;
    }

    public String getKeystorePassword() {
        return keystorePassword;
    }

    public String getKeyManagerPassword() {
        return keyManagerPassword;
    }

    public int getHttpsPort() {
        return httpsPort;
    }
}
