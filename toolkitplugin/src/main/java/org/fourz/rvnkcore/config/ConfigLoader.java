package org.fourz.rvnkcore.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;
import org.fourz.rvnkcore.util.log.LogManager;
import org.fourz.rvnkcore.api.config.ApiConfig;
import org.fourz.rvnkcore.api.config.ChatRelayConfig;
import org.fourz.rvnkcore.api.config.PortalConfig;
import org.fourz.rvnkcore.api.config.TransferConfig;
import org.fourz.rvnkcore.api.config.WebhookConfig;
import org.fourz.rvnkcore.database.config.DatabaseConfig;

import java.io.File;
import java.util.logging.Level;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Unified configuration loader for RVNKCore components.
 *
 * Handles loading and validation of API and database configuration from config.yml,
 * using the standard Bukkit config API. Uses singleton pattern per plugin to prevent
 * duplicate loading.
 *
 * @since 1.0.0
 */
public class ConfigLoader {
    
    // Singleton instances per plugin
    private static final ConcurrentHashMap<String, ConfigLoader> instances = new ConcurrentHashMap<>();
    
    private final Plugin plugin;
    private final LogManager logger;
    private FileConfiguration coreConfig;
    
    // Cached configurations to prevent duplicate loading
    private DatabaseConfig cachedDatabaseConfig;
    private DatabaseConfig cachedClusterDatabaseConfig;
    private ApiConfig cachedApiConfig;
    private WebhookConfig cachedWebhookConfig;
    private ChatRelayConfig cachedChatRelayConfig;
    private TransferConfig cachedTransferConfig;
    private PortalConfig cachedPortalConfig;
    private volatile boolean initialized = false;
    private final Object initLock = new Object();
    
    private ConfigLoader(Plugin plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, getClass());
    }
    
    /**
     * Gets the singleton ConfigLoader instance for a plugin.
     * 
     * @param plugin The plugin instance
     * @return The ConfigLoader instance for this plugin
     */
    public static ConfigLoader getInstance(Plugin plugin) {
        return instances.computeIfAbsent(plugin.getName(), k -> new ConfigLoader(plugin));
    }
    
    /**
     * Ensures config.yml exists and is properly initialized.
     * Uses the standard Bukkit config API. Thread-safe and idempotent - only loads once.
     */
    public void ensureConfigExists() {
        if (initialized && coreConfig != null) {
            logger.debug("Core configuration already initialized; skipping re-load");
            return;
        }

        synchronized (initLock) {
            if (initialized && coreConfig != null) {
                logger.debug("Core configuration already initialized (within lock); skipping re-load");
                return;
            }

            // saveDefaultConfig() copies config.yml from the JAR if it doesn't exist yet
            plugin.saveDefaultConfig();

            // Migration: warn if old config-core.yml is still present
            File oldConfigFile = new File(plugin.getDataFolder(), "config-core.yml");
            if (oldConfigFile.exists()) {
                logger.warning("config-core.yml is deprecated and no longer used. " +
                    "Settings have been merged into config.yml. " +
                    "Please migrate your custom settings and delete config-core.yml.");
            }

            // Load and validate configuration once
            loadConfig();
            validateConfiguration();
            applyCoreLoggingSettings();
            initialized = true;
        }
    }
    
    /**
     * Loads the core configuration via the Bukkit config API.
     */
    private void loadConfig() {
        plugin.reloadConfig();
        coreConfig = plugin.getConfig();
        logger.info("Core configuration loaded from config.yml");
    }
    
    /**
     * Applies core logging settings from the configuration.
     * Sets the global log level on both LogManager implementations
     * for all existing and future instances.
     */
    @SuppressWarnings("deprecation")
    private void applyCoreLoggingSettings() {
        String coreLogLevel = coreConfig.getString("general.logLevel", "WARNING");
        Level level = LogManager.parseLevel(coreLogLevel);

        // Use plugin logger directly to guarantee visibility regardless of LogManager state
        plugin.getLogger().info("[ConfigLoader] Applying global logging level: "
            + coreLogLevel + " -> " + level.getName());

        // Apply to rvnkcore LogManager (primary - used by all plugins)
        LogManager.setGlobalLogLevel(level);

        // Legacy rvnktools LogManager removed — all callers migrated to rvnkcore.util.log.LogManager
    }
    
    /**
     * Validates the current configuration against requirements.
     */
    private void validateConfiguration() {
        if (coreConfig == null) {
            logger.error("Core configuration is null after loading");
            return;
        }
        
        // Validate API configuration
        validateApiConfiguration();
        
        // Validate database configuration  
        validateDatabaseConfiguration();
        
        // Validate logging configuration
        validateLoggingConfiguration();
    }
    
    /**
     * Validates API-specific configuration sections with comprehensive checks.
     */
    private void validateApiConfiguration() {
        if (!coreConfig.contains("api")) {
            logger.warning("API configuration section missing - API features will use defaults");
            return;
        }
        
        // Check for required settings
        String[] expectedPaths = {
            "api.enabled",
            "api.host", 
            "api.http.port",
            "api.auth.key"
        };
        
        for (String path : expectedPaths) {
            if (!coreConfig.contains(path)) {
                logger.warning("Missing API configuration: " + path + " (using default value)");
            }
        }
        
        // Check for deprecated settings that should be removed/renamed
        String[] deprecatedPaths = {
            "api.server.min-threads",
            "api.server.queue-size",
            "api.auth.enabled"
        };
        
        for (String path : deprecatedPaths) {
            if (coreConfig.contains(path)) {
                logger.warning("Deprecated API configuration found: " + path + " (will be ignored)");
            }
        }
        
        // Validate port ranges
        int httpPort = coreConfig.getInt("api.http.port", 8080);
        int httpsPort = coreConfig.getInt("api.https.port", 8081);
        
        if (httpPort <= 0 || httpPort > 65535) {
            logger.error("Invalid HTTP port in core configuration: " + httpPort);
        }
        
        if (httpsPort <= 0 || httpsPort > 65535) {
            logger.error("Invalid HTTPS port in core configuration: " + httpsPort);
        }
        
        if (httpPort == httpsPort) {
            logger.error("HTTP and HTTPS ports cannot be the same: " + httpPort);
        }
        
        // Validate API key security
        String apiKey = coreConfig.getString("api.auth.key", "changeme");
        if ("changeme".equals(apiKey)) {
            logger.warning("API key is set to default value 'changeme' - please change for security");
        }
        
        // Validate HTTPS configuration if enabled
        boolean httpsEnabled = coreConfig.getBoolean("api.https.enabled", false);
        if (httpsEnabled) {
            String keystorePath = coreConfig.getString("api.https.keystore-path", "");
            if (keystorePath.trim().isEmpty()) {
                logger.error("HTTPS enabled but keystore path not specified");
            }
        }
    }
    
    /**
     * Validates database configuration sections with detailed error checking.
     */
    private void validateDatabaseConfiguration() {
        if (!coreConfig.contains("database")) {
            logger.warning("Database configuration section missing - using SQLite defaults");
            return;
        }
        
        String dbType = coreConfig.getString("database.type", "sqlite");
        if (!"sqlite".equalsIgnoreCase(dbType) && !"mysql".equalsIgnoreCase(dbType)) {
            logger.error("Unsupported database type: " + dbType + " (supported: sqlite, mysql)");
        }
        
        if ("mysql".equalsIgnoreCase(dbType)) {
            String[] requiredMysqlPaths = {
                "database.mysql.host",
                "database.mysql.database", 
                "database.mysql.username"
            };
            
            for (String path : requiredMysqlPaths) {
                if (!coreConfig.contains(path) || coreConfig.getString(path, "").trim().isEmpty()) {
                    logger.error("Required MySQL configuration missing: " + path);
                }
            }
            
            // Validate MySQL pool configuration if present
            if (coreConfig.contains("database.mysql.pool")) {
                int maxConnections = coreConfig.getInt("database.mysql.pool.maxConnections", 20);
                int minIdleConnections = coreConfig.getInt("database.mysql.pool.minIdleConnections", 5);
                
                if (maxConnections <= 0) {
                    logger.error("Invalid MySQL pool maxConnections: " + maxConnections);
                }
                
                if (minIdleConnections < 0 || minIdleConnections > maxConnections) {
                    logger.error("Invalid MySQL pool minIdleConnections: " + minIdleConnections + 
                               " (must be >= 0 and <= maxConnections)");
                }
            }
        }
    }
    
    /**
     * Validates logging configuration with level validation.
     */
    private void validateLoggingConfiguration() {
        String logLevel = coreConfig.getString("general.logLevel", "WARNING");
        String[] validLevels = {"OFF", "SEVERE", "WARNING", "INFO", "FINE", "DEBUG"};

        boolean validLevel = false;
        for (String level : validLevels) {
            if (level.equalsIgnoreCase(logLevel)) {
                validLevel = true;
                break;
            }
        }

        if (!validLevel) {
            logger.warning("Invalid RVNKCore logging level: " + logLevel + " (valid: OFF, SEVERE, WARNING, INFO, FINE, DEBUG)");
        }
    }
    
    /**
     * Gets the RVNKCore log level from configuration.
     *
     * @return The configured log level
     */
    public Level getCoreLogLevel() {
        String logLevel = coreConfig.getString("general.logLevel", "WARNING");
        return LogManager.parseLevel(logLevel);
    }
    
    /**
     * Gets the API configuration instance with caching.
     * 
     * @return ApiConfig instance
     */
    public ApiConfig getApiConfig() {
        // Return cached config if available
        if (cachedApiConfig != null) {
            logger.debug("Using cached API configuration");
            return cachedApiConfig;
        }
        
        if (coreConfig == null) {
            ensureConfigExists();
        }
        
        // Get the API configuration section
        ConfigurationSection apiSection = coreConfig.getConfigurationSection("api");
        if (apiSection == null) {
            logger.warning("API configuration section missing - creating default config");
            // Create a minimal API section
            coreConfig.createSection("api");
            coreConfig.set("api.enabled", false);
            coreConfig.set("api.host", "localhost");
            coreConfig.set("api.http.port", 8080);
            coreConfig.set("api.https.port", 8081);
            coreConfig.set("api.auth.key", "changeme");
            apiSection = coreConfig.getConfigurationSection("api");
        }
        
        // Get global log level for API config
        String globalLogLevel = coreConfig.getString("general.logLevel", "WARNING");
        
        // Create ApiConfig using static factory method and cache it
        cachedApiConfig = ApiConfig.fromConfigurationSection(plugin, apiSection, globalLogLevel);
        logger.debug("API configuration loaded and cached");
        return cachedApiConfig;
    }

    /**
     * Gets the webhook configuration instance with caching.
     *
     * @return WebhookConfig instance
     */
    public WebhookConfig getWebhookConfig() {
        if (cachedWebhookConfig != null) {
            return cachedWebhookConfig;
        }

        if (coreConfig == null) {
            ensureConfigExists();
        }

        ConfigurationSection webhookSection = coreConfig.getConfigurationSection("webhook");
        cachedWebhookConfig = WebhookConfig.fromConfigurationSection(webhookSection);
        logger.debug("Webhook configuration loaded and cached");
        return cachedWebhookConfig;
    }

    /**
     * Gets the chat relay configuration instance with caching.
     *
     * @return ChatRelayConfig instance
     */
    public ChatRelayConfig getChatRelayConfig() {
        if (cachedChatRelayConfig != null) {
            return cachedChatRelayConfig;
        }

        if (coreConfig == null) {
            ensureConfigExists();
        }

        ConfigurationSection chatRelaySection = coreConfig.getConfigurationSection("chat-relay");
        cachedChatRelayConfig = ChatRelayConfig.fromConfigurationSection(chatRelaySection);
        logger.debug("Chat relay configuration loaded and cached");
        return cachedChatRelayConfig;
    }

    /**
     * Gets the transfer configuration instance with caching.
     *
     * @return TransferConfig instance
     */
    public TransferConfig getTransferConfig() {
        if (cachedTransferConfig != null) {
            return cachedTransferConfig;
        }

        if (coreConfig == null) {
            ensureConfigExists();
        }

        ConfigurationSection transferSection = coreConfig.getConfigurationSection("transfer");
        cachedTransferConfig = TransferConfig.fromConfigurationSection(transferSection);
        logger.debug("Transfer configuration loaded and cached");
        return cachedTransferConfig;
    }

    /**
     * Gets the portal configuration instance with caching.
     *
     * @return PortalConfig instance
     */
    public PortalConfig getPortalConfig() {
        if (cachedPortalConfig != null) {
            return cachedPortalConfig;
        }

        if (coreConfig == null) {
            ensureConfigExists();
        }

        ConfigurationSection portalSection = coreConfig.getConfigurationSection("portal");
        cachedPortalConfig = PortalConfig.fromConfigurationSection(portalSection);
        logger.debug("Portal configuration loaded and cached");
        return cachedPortalConfig;
    }

    /**
     * Gets the database configuration instance with caching.
     *
     * @return DatabaseConfig instance
     */
    public DatabaseConfig getDatabaseConfig() {
        // Return cached config if available
        if (cachedDatabaseConfig != null) {
            logger.debug("Using cached database configuration");
            return cachedDatabaseConfig;
        }
        
        if (coreConfig == null) {
            ensureConfigExists();
        }
        
        // Get database type
        String dbType = coreConfig.getString("database.type", "sqlite");
        
        // Build DatabaseConfig based on type
        if ("mysql".equalsIgnoreCase(dbType)) {
            cachedDatabaseConfig = DatabaseConfig.builder()
                .type("mysql")
                .host(coreConfig.getString("database.mysql.host", "localhost"))
                .port(coreConfig.getInt("database.mysql.port", 3306))
                .database(coreConfig.getString("database.mysql.database", "rvnkcore"))
                .username(coreConfig.getString("database.mysql.username", ""))
                .password(coreConfig.getString("database.mysql.password", ""))
                .useSSL(coreConfig.getBoolean("database.mysql.useSSL", true))
                // Safety parameters (socketTimeout/connectTimeout/tcpKeepAlive) are merged in
                // at code level so a fresh deployment cannot come up without them (#1546).
                .connectionParameters(DatabaseConfig.withRequiredMySqlParams(
                        coreConfig.getString("database.mysql.connectionParameters", "")))
                // Connection Pool Configuration
                .maxConnections(coreConfig.getInt("database.mysql.pool.maxConnections", 20))
                .minIdleConnections(coreConfig.getInt("database.mysql.pool.minIdleConnections", 5))
                .connectionTimeoutMs(coreConfig.getLong("database.mysql.pool.connectionTimeoutMs", 30000L))
                .idleTimeoutMs(coreConfig.getLong("database.mysql.pool.idleTimeoutMs", 600000L))
                .maxLifetimeMs(coreConfig.getLong("database.mysql.pool.maxLifetimeMs", 1800000L))
                .leakDetectionMs(coreConfig.getLong("database.mysql.pool.leakDetectionMs", 60000L))
                .build();
        } else {
            // Default to SQLite
            String databaseFile = coreConfig.getString("database.sqlite.file", "rvnkcore.db");
            cachedDatabaseConfig = DatabaseConfig.builder()
                .type("sqlite")
                .database(databaseFile)
                // Connection Pool Configuration (SQLite defaults: small pool, leak detection disabled)
                .maxConnections(coreConfig.getInt("database.sqlite.pool.maxConnections", 5))
                .minIdleConnections(coreConfig.getInt("database.sqlite.pool.minIdleConnections", 1))
                .connectionTimeoutMs(coreConfig.getLong("database.sqlite.pool.connectionTimeoutMs", 30000L))
                .idleTimeoutMs(coreConfig.getLong("database.sqlite.pool.idleTimeoutMs", 600000L))
                .maxLifetimeMs(coreConfig.getLong("database.sqlite.pool.maxLifetimeMs", 1800000L))
                .leakDetectionMs(coreConfig.getLong("database.sqlite.pool.leakDetectionMs", 0L))
                .build();
        }
        
        logger.debug("Database configuration loaded and cached: " + dbType);
        return cachedDatabaseConfig;
    }

    /**
     * This server's identity within the network, used to scope per-server rows (#1811).
     *
     * <p>There is no dedicated setting for this. Rather than introduce a fourth place to name the
     * same server — and a fourth chance for the names to disagree — this reuses the identifiers
     * already deployed and agreed across tiers:</p>
     *
     * <ol>
     *   <li>{@code chat-relay.server-id} — set on every tier (dev / event / prod) and already the
     *       de-facto network identity: peers address each other by it.</li>
     *   <li>{@code webhook.server-id} — the same value, used by the WebUI cache tags.</li>
     *   <li>{@code "local"} — standalone server with neither configured.</li>
     * </ol>
     *
     * <p>Read directly from config rather than via {@code ChatRelayConfig} because that config is
     * only populated when the relay is enabled, and a server's identity must not depend on whether
     * an unrelated feature is switched on.</p>
     *
     * @return the server identifier, never null or blank
     */
    public String getServerId() {
        if (coreConfig == null) {
            ensureConfigExists();
        }
        String id = coreConfig.getString("chat-relay.server-id", "");
        if (id == null || id.isBlank()) {
            id = coreConfig.getString("webhook.server-id", "");
        }
        return (id == null || id.isBlank()) ? "local" : id.trim();
    }

    // ============================================================
    // CLUSTER (shared network data) — #1796 Phase 2
    // ============================================================

    /** Cluster role: this server owns the cluster database. */
    public static final String CLUSTER_ROLE_AUTHORITATIVE = "authoritative";
    /** Cluster role: this server connects to another server's cluster database. */
    public static final String CLUSTER_ROLE_MEMBER = "member";

    /**
     * Whether cluster-shared data is enabled on this server.
     *
     * <p>When false (the default) RVNKCore behaves exactly as before: one pool, one database,
     * everything local. Nothing about the cluster feature is reachable.</p>
     */
    public boolean isClusterEnabled() {
        if (coreConfig == null) {
            ensureConfigExists();
        }
        return coreConfig.getBoolean("cluster.enabled", false);
    }

    /**
     * This server's role in the cluster — {@value #CLUSTER_ROLE_AUTHORITATIVE} (it hosts the
     * cluster database) or {@value #CLUSTER_ROLE_MEMBER} (it connects out to one).
     *
     * <p>Anything unrecognised is treated as {@code member}, because a member that cannot connect
     * fails loudly, whereas mis-classifying a member as authoritative would silently point
     * cluster reads at the local database and produce a second divergent data set — the exact
     * failure this whole cluster effort exists to undo (#1795).</p>
     */
    public String getClusterRole() {
        if (coreConfig == null) {
            ensureConfigExists();
        }
        String role = coreConfig.getString("cluster.role", CLUSTER_ROLE_MEMBER);
        return CLUSTER_ROLE_AUTHORITATIVE.equalsIgnoreCase(role)
                ? CLUSTER_ROLE_AUTHORITATIVE
                : CLUSTER_ROLE_MEMBER;
    }

    /**
     * Whether player <b>identity</b> is read from and written to the cluster database (#1812).
     *
     * <p>Separate from {@link #isClusterEnabled()} and default <b>false</b>, because turning it on
     * changes where every player record lives. A member server that flips this before its local
     * identity rows have been unioned into the cluster would see those players as absent and start
     * creating fresh records for them, losing {@code first_join} and name history.</p>
     *
     * <p>Intended sequence: deploy with this off → run {@code /rvnkcore migrate playeridentity} →
     * verify the union → enable → restart. On the authoritative server it is a no-op, since the
     * cluster database is already the local one.</p>
     *
     * <p>Per-server activity is unaffected either way — it always stays in the local
     * {@code rvnk_player_server_state}.</p>
     */
    public boolean isPlayerIdentityShared() {
        if (coreConfig == null) {
            ensureConfigExists();
        }
        return coreConfig.getBoolean("cluster.share-player-identity", false);
    }

    /**
     * Whether player <b>preferences</b> are read from the cluster database (#1813).
     *
     * <p>Its own flag, default <b>false</b>, for the same reason identity has one: flipping it moves
     * where every preference row is read from, and a member's existing local rows would appear to
     * vanish — players would find their chat display, list surface and announcement mutes reset.</p>
     *
     * <p>Requires {@link #isPlayerIdentityShared()}. The preference tables carry a foreign key into
     * {@code rvnk_players}, so they can only live where that table lives; enabling preferences while
     * identity is still local would point the constraint across databases, which InnoDB cannot
     * enforce.</p>
     *
     * <p>Intended sequence: deploy with this off → check the counts in {@code /rvnkcore db} → decide
     * whether the local rows need carrying over → enable → restart.</p>
     */
    public boolean isPlayerPreferencesShared() {
        if (coreConfig == null) {
            ensureConfigExists();
        }
        return coreConfig.getBoolean("cluster.share-player-preferences", false)
                && isPlayerIdentityShared();
    }

    /**
     * Connection details for the cluster database, used only by {@code member} servers.
     *
     * <p>Authoritative servers never call this — they reuse the primary provider, so the cluster
     * costs them no additional connections.</p>
     *
     * <p>The required MySQL safety parameters are merged in here for the same reason as the primary
     * config (#1546): the cluster link is by definition cross-host, and without {@code socketTimeout}
     * a stalled read blocks the calling thread forever and leaks the connection.</p>
     *
     * @return the cluster DatabaseConfig, or null when the cluster mysql block is absent
     */
    public DatabaseConfig getClusterDatabaseConfig() {
        if (cachedClusterDatabaseConfig != null) {
            return cachedClusterDatabaseConfig;
        }
        if (coreConfig == null) {
            ensureConfigExists();
        }
        String host = coreConfig.getString("cluster.mysql.host");
        String database = coreConfig.getString("cluster.mysql.database");
        if (host == null || host.isBlank() || database == null || database.isBlank()) {
            return null;
        }

        cachedClusterDatabaseConfig = DatabaseConfig.builder()
                .type("mysql")
                .host(host)
                .port(coreConfig.getInt("cluster.mysql.port", 3306))
                .database(database)
                .username(coreConfig.getString("cluster.mysql.username", ""))
                .password(coreConfig.getString("cluster.mysql.password", ""))
                .useSSL(coreConfig.getBoolean("cluster.mysql.useSSL", false))
                .connectionParameters(DatabaseConfig.withRequiredMySqlParams(
                        coreConfig.getString("cluster.mysql.connectionParameters", "")))
                // Deliberately smaller than the primary pool: the cluster serves a narrow set of
                // low-traffic content tables, not the hot per-player path.
                .maxConnections(coreConfig.getInt("cluster.mysql.pool.maxConnections", 5))
                .minIdleConnections(coreConfig.getInt("cluster.mysql.pool.minIdleConnections", 1))
                .connectionTimeoutMs(coreConfig.getLong("cluster.mysql.pool.connectionTimeoutMs", 10000L))
                .idleTimeoutMs(coreConfig.getLong("cluster.mysql.pool.idleTimeoutMs", 120000L))
                .maxLifetimeMs(coreConfig.getLong("cluster.mysql.pool.maxLifetimeMs", 180000L))
                .leakDetectionMs(coreConfig.getLong("cluster.mysql.pool.leakDetectionMs", 60000L))
                .build();

        logger.debug("Cluster database configuration loaded and cached: " + host + "/" + database);
        return cachedClusterDatabaseConfig;
    }

    /**
     * Reloads the configuration from disk.
     * Invalidates all cached configurations to force fresh parsing.
     */
    public void reloadConfiguration() {
        logger.info("Reloading core configuration from config.yml");
        plugin.reloadConfig();
        coreConfig = plugin.getConfig();
        validateConfiguration();
        applyCoreLoggingSettings();

        // Invalidate caches so subsequent calls re-parse from the fresh config
        cachedApiConfig = null;
        cachedDatabaseConfig = null;
        cachedClusterDatabaseConfig = null;
        cachedWebhookConfig = null;
        cachedChatRelayConfig = null;
        cachedTransferConfig = null;
        cachedPortalConfig = null;
    }
    
    /**
     * Returns whether the core configuration has been initialized.
     */
    public boolean isInitialized() {
        return initialized;
    }
    
    /**
     * Performs comprehensive validation of the current configuration.
     * 
     * @return true if configuration is valid, false if issues were found
     */
    public boolean validateConfigurationPublic() {
        if (coreConfig == null) {
            ensureConfigExists();
        }
        
        boolean isValid = true;
        
        // Validate each section
        try {
            validateApiConfiguration();
            validateDatabaseConfiguration(); 
            validateLoggingConfiguration();
        } catch (Exception e) {
            logger.error("Configuration validation failed", e);
            isValid = false;
        }
        
        logger.info("Configuration validation completed");
        return isValid;
    }
    
    /**
     * Gets a comprehensive summary of the current configuration for debugging.
     * 
     * @return Configuration summary string
     */
    public String getConfigurationSummary() {
        if (coreConfig == null) {
            return "Core configuration not loaded";
        }
        
        StringBuilder summary = new StringBuilder();
        summary.append("RVNKCore Configuration Summary:\n");
        
        // Logging configuration
        String logLevel = coreConfig.getString("general.logLevel", "WARNING");
        summary.append("  Logging Level: ").append(logLevel).append("\n");
        
        // Database configuration
        String dbType = coreConfig.getString("database.type", "sqlite");
        summary.append("  Database Type: ").append(dbType).append("\n");
        
        if ("sqlite".equalsIgnoreCase(dbType)) {
            String dbFile = coreConfig.getString("database.sqlite.file", "rvnkcore.db");
            summary.append("    SQLite File: ").append(dbFile).append("\n");
        } else if ("mysql".equalsIgnoreCase(dbType)) {
            String host = coreConfig.getString("database.mysql.host", "localhost");
            int port = coreConfig.getInt("database.mysql.port", 3306);
            String database = coreConfig.getString("database.mysql.database", "");
            summary.append("    MySQL Host: ").append(host).append(":").append(port).append("\n");
            summary.append("    MySQL Database: ").append(database).append("\n");
        }
        
        // API configuration
        boolean apiEnabled = coreConfig.getBoolean("api.enabled", false);
        summary.append("  API Enabled: ").append(apiEnabled).append("\n");
        
        if (apiEnabled) {
            String host = coreConfig.getString("api.host", "localhost");
            int httpPort = coreConfig.getInt("api.http.port", 8080);
            boolean httpsEnabled = coreConfig.getBoolean("api.https.enabled", false);
            int httpsPort = coreConfig.getInt("api.https.port", 8081);
            
            summary.append("    API Host: ").append(host).append("\n");
            summary.append("    HTTP Port: ").append(httpPort).append("\n");
            summary.append("    HTTPS Enabled: ").append(httpsEnabled);
            if (httpsEnabled) {
                summary.append(" (Port: ").append(httpsPort).append(")");
            }
            summary.append("\n");
            
            boolean corsEnabled = coreConfig.getBoolean("api.cors.enabled", true);
            summary.append("    CORS Enabled: ").append(corsEnabled).append("\n");
        }
        
        return summary.toString();
    }
}
