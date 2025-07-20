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
        this.logger = LogManager.getInstance(plugin);
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
            logger.info("RVNKCore API is disabled");
            return;
        }

        if (!config.validate()) {
            logger.error("RVNKCore API configuration is invalid");
            return;
        }

        try {
            server = new Server();
            setupConnectors();
            setupServlets();
            
            server.start();
            logger.info("RVNKCore API server started on port " + config.getPort());
        } catch (Exception e) {
            logger.error("Failed to start RVNKCore API server", e);
        }
    }

    /**
     * Sets up HTTP/HTTPS connectors based on configuration.
     */
    private void setupConnectors() {
        HttpConfiguration httpConfig = new HttpConfiguration();
        httpConfig.setSendServerVersion(config.isSendServerVersion());
        
        // HTTP connector
        ServerConnector httpConnector = new ServerConnector(server, 
                new HttpConnectionFactory(httpConfig));
        httpConnector.setPort(config.getPort());
        httpConnector.setIdleTimeout(config.getIdleTimeout());
        server.addConnector(httpConnector);

        // HTTPS connector if enabled
        if (config.isHttpsEnabled() && !config.getKeystorePath().isEmpty()) {
            setupHttpsConnector(httpConfig);
        }
    }

    /**
     * Sets up HTTPS connector with SSL configuration.
     */
    private void setupHttpsConnector(HttpConfiguration httpConfig) {
        try {
            HttpConfiguration httpsConfig = new HttpConfiguration(httpConfig);
            httpsConfig.addCustomizer(new SecureRequestCustomizer());

            SslContextFactory.Server sslContextFactory = new SslContextFactory.Server();
            sslContextFactory.setKeyStorePath(config.getKeystorePath());
            sslContextFactory.setKeyStorePassword(config.getKeystorePassword());

            ServerConnector httpsConnector = new ServerConnector(server,
                    new SslConnectionFactory(sslContextFactory, "http/1.1"),
                    new HttpConnectionFactory(httpsConfig));
            httpsConnector.setPort(config.getPort() + 1); // HTTPS on port + 1
            httpsConnector.setIdleTimeout(config.getIdleTimeout());

            server.addConnector(httpsConnector);
            logger.info("HTTPS connector enabled on port " + (config.getPort() + 1));
        } catch (Exception e) {
            logger.error("Failed to setup HTTPS connector", e);
        }
    }

    /**
     * Sets up servlet context and registers controllers.
     */
    private void setupServlets() {
        ServletContextHandler context = new ServletContextHandler(ServletContextHandler.NO_SESSIONS);
        context.setContextPath(config.getContextPath());

        // Add authentication filter
        AuthFilter authFilter = new AuthFilter(config.getApiKey(), config.getAllowedIPs(), plugin);
        context.addFilter(new FilterHolder(authFilter), "/v1/*", null);

        // Register player controller
        PlayerController playerController = new PlayerController(playerService, gson, logger);
        context.addServlet(new ServletHolder(playerController), "/v1/players/*");

        server.setHandler(context);
    }

    /**
     * Stops the Jetty server.
     */
    public void stop() {
        if (server != null && server.isStarted()) {
            try {
                server.stop();
                logger.info("RVNKCore API server stopped");
            } catch (Exception e) {
                logger.error("Error stopping RVNKCore API server", e);
            }
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
