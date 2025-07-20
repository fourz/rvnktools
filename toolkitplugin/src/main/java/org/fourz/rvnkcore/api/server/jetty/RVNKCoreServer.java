package org.fourz.rvnkcore.api.server.jetty;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.SecureRequestCustomizer;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.SslConnectionFactory;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.fourz.rvnkcore.api.config.ApiConfig;
import org.fourz.rvnkcore.api.controller.PlayerController;
import org.fourz.rvnkcore.api.security.AuthFilter;
import org.fourz.rvnkcore.api.service.PlayerService;
import org.fourz.rvnktools.util.log.LogManager;
import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * RVNKCore Jetty server for REST API services.
 * Provides HTTP/HTTPS endpoints for player and core data access.
 */
public class RVNKCoreServer {
    private Server server;
    private final ApiConfig config;
    private final PlayerService playerService;
    private final Gson gson;
    private final LogManager logger;
    private final Plugin plugin;

    /**
     * Creates a new RVNKCore server instance.
     *
     * @param config API configuration
     * @param playerService Player service for data operations
     * @param plugin Plugin instance
     */
    public RVNKCoreServer(ApiConfig config, PlayerService playerService, Plugin plugin) {
        this.config = config;
        this.playerService = playerService;
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, getClass());
        this.gson = createGson();
    }

    /**
     * Creates Gson instance with LocalDateTime support.
     */
    private Gson createGson() {
        return new GsonBuilder()
                .registerTypeAdapter(LocalDateTime.class, new LocalDateTimeAdapter())
                .setPrettyPrinting()
                .create();
    }

    /**
     * Starts the Jetty server with configured endpoints.
     */
    public void start() {
        if (!config.isEnabled()) {
            logger.info("RVNKCore API is disabled in configuration");
            return;
        }

        logger.info("Starting RVNKCore REST API server...");
        logger.info("Server Configuration Details:");
        logger.info("  - HTTP Port: " + config.getHttpPort());
        logger.info("  - HTTPS Enabled: " + config.isHttpsEnabled());
        if (config.isHttpsEnabled()) {
            logger.info("  - HTTPS Port: " + config.getHttpsPort());
        }
        logger.info("  - Context Path: " + config.getContextPath());
        logger.info("  - API Key Required: " + (config.getApiKey() != null && !config.getApiKey().isEmpty()));
        logger.info("  - IP Restrictions: " + (config.getAllowedIPs().length == 0 ? "None" : config.getAllowedIPs().length + " allowed IPs"));

        if (!config.validate()) {
            logger.error("RVNKCore API configuration validation failed");
            logger.error("Please check your configuration and try again");
            return;
        }

        try {
            server = new Server();
            logger.info("Jetty server instance created");
            
            setupConnectors();
            logger.info("Connectors setup completed");
            
            setupServlets();
            logger.info("Servlet context setup completed");
            
            logger.info("Starting Jetty server...");
            server.start();
            
            // Log successful startup with connection details
            logger.info("=== RVNKCore REST API Server Successfully Started ===");
            logger.info("HTTP Endpoint: http://localhost:" + config.getHttpPort() + config.getContextPath());
            if (config.isHttpsEnabled() && !config.getKeystorePath().isEmpty()) {
                logger.info("HTTPS Endpoint: https://localhost:" + config.getHttpsPort() + config.getContextPath());
            }
            logger.info("Available API Routes:");
            logger.info("  - GET  " + config.getContextPath() + "/v1/players");
            logger.info("  - GET  " + config.getContextPath() + "/v1/players/online");
            logger.info("  - GET  " + config.getContextPath() + "/v1/players/{uuid}");
            logger.info("  - GET  " + config.getContextPath() + "/v1/players/name/{name}");
            logger.info("  - GET  " + config.getContextPath() + "/v1/players/group/{group}");
            logger.info("  - GET  " + config.getContextPath() + "/v1/players/search?name={pattern}");
            logger.info("  - GET  " + config.getContextPath() + "/v1/players/count");
            logger.info("  - PUT  " + config.getContextPath() + "/v1/players/{uuid}/location");
            logger.info("  - PUT  " + config.getContextPath() + "/v1/players/{uuid}/groups");
            logger.info("Authentication: X-API-Key header required");
            logger.info("======================================================");
            
        } catch (Exception e) {
            logger.error("Failed to start RVNKCore REST API server", e);
            logger.error("Server startup failed with error: " + e.getMessage());
            logger.error("Error type: " + e.getClass().getSimpleName());
            
            // Detailed error analysis
            if (e.getMessage().contains("Address already in use")) {
                logger.error("Port conflict detected!");
                logger.error("  - HTTP Port " + config.getHttpPort() + " may already be in use");
                if (config.isHttpsEnabled()) {
                    logger.error("  - HTTPS Port " + config.getHttpsPort() + " may already be in use");
                }
                logger.error("  - Try changing ports in your configuration or stop conflicting services");
            } else if (e.getMessage().contains("Permission denied")) {
                logger.error("Permission denied - this may be due to:");
                logger.error("  - Insufficient privileges to bind to port " + config.getHttpPort());
                logger.error("  - Try using a port above 1024 or run with elevated privileges");
            }
            
            if (e.getCause() != null) {
                logger.error("Root cause: " + e.getCause().getMessage());
                logger.error("Root cause type: " + e.getCause().getClass().getSimpleName());
            }
        }
    }

    /**
     * Sets up HTTP/HTTPS connectors based on configuration.
     */
    private void setupConnectors() {
        logger.info("Setting up HTTP/HTTPS connectors...");
        logger.info("Connector configuration - HTTP Port: " + config.getHttpPort() + 
                   ", HTTPS Enabled: " + config.isHttpsEnabled() + 
                   ", Idle Timeout: " + config.getIdleTimeout() + "ms");
        
        HttpConfiguration httpConfig = new HttpConfiguration();
        httpConfig.setSendServerVersion(config.isSendServerVersion());
        
        // HTTP connector
        logger.info("Creating HTTP connector on port " + config.getHttpPort());
        try {
            ServerConnector httpConnector = new ServerConnector(server, 
                    new HttpConnectionFactory(httpConfig));
            httpConnector.setPort(config.getHttpPort());
            httpConnector.setIdleTimeout(config.getIdleTimeout());
            httpConnector.setName("http-connector");
            server.addConnector(httpConnector);
            logger.info("HTTP connector successfully created and added to server");
            logger.info("HTTP endpoint will be available at: http://localhost:" + config.getHttpPort() + config.getContextPath());
        } catch (Exception e) {
            logger.error("Failed to create HTTP connector on port " + config.getHttpPort(), e);
            logger.error("HTTP connector error details: " + e.getMessage());
            throw new RuntimeException("HTTP connector setup failed", e);
        }

        // HTTPS connector if enabled
        if (config.isHttpsEnabled() && !config.getKeystorePath().isEmpty()) {
            logger.info("HTTPS is enabled, setting up HTTPS connector...");
            setupHttpsConnector(httpConfig);
        } else if (config.isHttpsEnabled()) {
            logger.warning("HTTPS is enabled but keystore path is empty - HTTPS will not be available");
            logger.warning("To enable HTTPS, configure 'api.keystore.path' in your configuration");
        } else {
            logger.info("HTTPS is disabled in configuration");
        }
    }

    /**
     * Sets up HTTPS connector with SSL configuration.
     */
    private void setupHttpsConnector(HttpConfiguration httpConfig) {
        try {
            logger.info("Configuring HTTPS connector on port " + config.getHttpsPort());
            logger.info("HTTPS Configuration:");
            logger.info("  - Keystore Path: " + config.getKeystorePath());
            logger.info("  - Keystore Password: " + (config.getKeystorePassword().isEmpty() ? "Not Set" : "********"));
            logger.info("  - Idle Timeout: " + config.getIdleTimeout() + "ms");
            
            HttpConfiguration httpsConfig = new HttpConfiguration(httpConfig);
            httpsConfig.addCustomizer(new SecureRequestCustomizer());

            SslContextFactory.Server sslContextFactory = new SslContextFactory.Server();
            sslContextFactory.setKeyStorePath(config.getKeystorePath());
            sslContextFactory.setKeyStorePassword(config.getKeystorePassword());

            logger.info("Creating SSL context factory for HTTPS connector");
            ServerConnector httpsConnector = new ServerConnector(server,
                    new SslConnectionFactory(sslContextFactory, "http/1.1"),
                    new HttpConnectionFactory(httpsConfig));
            httpsConnector.setPort(config.getHttpsPort());
            httpsConnector.setIdleTimeout(config.getIdleTimeout());
            httpsConnector.setName("https-connector");

            server.addConnector(httpsConnector);
            logger.info("HTTPS connector successfully created and added to server");
            logger.info("HTTPS endpoint will be available at: https://localhost:" + config.getHttpsPort() + config.getContextPath());
        } catch (Exception e) {
            logger.error("Failed to setup HTTPS connector on port " + config.getHttpsPort(), e);
            logger.error("HTTPS setup error details: " + e.getMessage());
            logger.error("HTTPS Configuration Issues:");
            logger.error("  - Keystore Path: " + config.getKeystorePath());
            logger.error("  - Keystore Exists: " + java.nio.file.Files.exists(java.nio.file.Paths.get(config.getKeystorePath())));
            logger.error("  - Port Availability: Check if port " + config.getHttpsPort() + " is available");
            if (e.getCause() != null) {
                logger.error("HTTPS root cause: " + e.getCause().getMessage());
                logger.error("HTTPS root cause type: " + e.getCause().getClass().getSimpleName());
            }
            // Don't throw exception - allow HTTP-only operation
            logger.warning("HTTPS connector setup failed - continuing with HTTP-only mode");
        }
    }

    /**
     * Sets up servlet context and registers controllers.
     */
    private void setupServlets() {
        logger.info("Setting up servlet context and controllers...");
        logger.info("Context path: " + config.getContextPath());
        
        ServletContextHandler context = new ServletContextHandler(ServletContextHandler.NO_SESSIONS);
        context.setContextPath(config.getContextPath());

        // Add authentication filter
        logger.info("Configuring authentication filter for /v1/* endpoints");
        AuthFilter authFilter = new AuthFilter(config.getApiKey(), config.getAllowedIPs(), plugin);
        context.addFilter(new FilterHolder(authFilter), "/v1/*", null);

        // Register player controller
        logger.info("Registering PlayerController for /v1/players/* endpoints");
        PlayerController playerController = new PlayerController(playerService, gson, logger);
        context.addServlet(new ServletHolder(playerController), "/v1/players/*");

        server.setHandler(context);
        logger.info("Servlet context configuration completed");
    }

    /**
     * Stops the Jetty server.
     */
    public void stop() {
        if (server != null && server.isStarted()) {
            try {
                logger.info("Stopping RVNKCore API server...");
                server.stop();
                logger.info("RVNKCore API server stopped successfully");
            } catch (Exception e) {
                logger.error("Error stopping RVNKCore API server", e);
                logger.error("Server shutdown error details: " + e.getMessage());
                if (e.getCause() != null) {
                    logger.error("Shutdown root cause: " + e.getCause().getMessage());
                }
            }
        } else {
            logger.info("RVNKCore API server was not running");
        }
    }

    /**
     * Returns whether the server is running.
     */
    public boolean isRunning() {
        return server != null && server.isStarted();
    }

    /**
     * Custom TypeAdapter for LocalDateTime JSON serialization.
     */
    private static class LocalDateTimeAdapter extends TypeAdapter<LocalDateTime> {
        private final DateTimeFormatter formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

        @Override
        public void write(JsonWriter out, LocalDateTime value) throws IOException {
            if (value == null) {
                out.nullValue();
            } else {
                out.value(value.format(formatter));
            }
        }

        @Override
        public LocalDateTime read(JsonReader in) throws IOException {
            String value = in.nextString();
            return value != null ? LocalDateTime.parse(value, formatter) : null;
        }
    }
}
