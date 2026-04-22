package org.fourz.rvnkcore.api.server.jetty;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.fourz.rvnkcore.api.auth.AuthTokenStore;
import org.fourz.rvnkcore.api.config.ApiConfig;
import org.fourz.rvnkcore.api.service.IServletRegistrationService;
import org.fourz.rvnkcore.api.service.PlayerService;
import org.fourz.rvnkcore.api.service.PlayerWorldService;
import org.fourz.rvnkcore.api.service.AnnouncementService;
import org.fourz.rvnkcore.api.service.WorldService;
import org.fourz.rvnkcore.api.service.impl.ServletRegistrationServiceImpl;
import org.fourz.rvnkcore.util.log.LogManager;
import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * RVNKCore Jetty server for REST API services.
 * Provides HTTP/HTTPS endpoints for player and core data access.
 * 
 * <p>This class has been refactored to use composition with specialized factories
 * for better separation of concerns and maintainability.</p>
 * 
 * <p>External plugins can register their own servlets via the {@link IServletRegistrationService}
 * which is registered with the ServiceRegistry on server startup.</p>
 * 
 * @since 1.3.0-alpha
 * @since 1.4.0 Added external servlet registration support
 */
public class CoreServer {
    private Server server;
    private final ApiConfig config;
    private final PlayerService playerService;
    private final PlayerWorldService playerWorldService;
    private final AnnouncementService announcementService;
    private final WorldService worldService;
    private final Gson gson;
    private final LogManager logger;
    private final Plugin plugin;
    
    // Specialized factories for different aspects of server setup
    private final ServerConnectorFactory connectorFactory;
    private final ServletFactory servletFactory;
    private final ServerLifecycle serverLifecycle;
    
    // External servlet registration service
    private final ServletRegistrationServiceImpl servletRegistrationService;

    /**
     * Creates a new RVNKCore server instance.
     *
     * @param config API configuration
     * @param playerService Player service for data operations
     * @param playerWorldService Player world service for world-specific data operations
     * @param announcementService Announcement service for data operations
     * @param worldService World service for world tracking and management
     * @param authTokenStore Authentication token store for magic link flow
     * @param plugin Plugin instance
     */
    public CoreServer(ApiConfig config, PlayerService playerService, PlayerWorldService playerWorldService,
                                    AnnouncementService announcementService,
                                    WorldService worldService,
                                    AuthTokenStore authTokenStore, Plugin plugin) {
        this.config = config;
        this.playerService = playerService;
        this.playerWorldService = playerWorldService;
        this.announcementService = announcementService;
        this.worldService = worldService;
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, getClass());
        this.gson = createGson();

        // Configure Jetty logging to be silent
        configureJettyLogging();

        // Initialize specialized factories
        this.connectorFactory = new ServerConnectorFactory(config, plugin, logger);
        this.servletFactory = new ServletFactory(config, plugin, logger, playerService, playerWorldService, announcementService, worldService, authTokenStore, gson);
        this.serverLifecycle = new ServerLifecycle(config, logger);

        // Initialize external servlet registration service
        this.servletRegistrationService = new ServletRegistrationServiceImpl(config, plugin, gson);
    }

    /**
     * Configures Jetty's internal logging to suppress verbose console output.
     * Uses system properties to set appropriate log levels for cleaner output.
     */
    private void configureJettyLogging() {
        try {
            // Set Jetty to use StdErrLog and configure it to be quiet
            System.setProperty("org.eclipse.jetty.util.log.class", "org.eclipse.jetty.util.log.StdErrLog");
            System.setProperty("org.eclipse.jetty.LEVEL", "WARN");
            
            // Suppress specific verbose components to WARN level
            System.setProperty("org.eclipse.jetty.server.LEVEL", "WARN");
            System.setProperty("org.eclipse.jetty.server.handler.LEVEL", "WARN");
            System.setProperty("org.eclipse.jetty.util.ssl.LEVEL", "WARN");
            System.setProperty("org.eclipse.jetty.server.AbstractConnector.LEVEL", "WARN");
            System.setProperty("org.eclipse.jetty.server.handler.ContextHandler.LEVEL", "WARN");
            
            // Additional suppressions for completely silent startup
            System.setProperty("org.eclipse.jetty.server.Server.LEVEL", "WARN");
            System.setProperty("org.eclipse.jetty.util.ssl.SslContextFactory.LEVEL", "WARN");
            
        } catch (Exception e) {
            logger.warning("Failed to configure Jetty logging: " + e.getMessage());
        }
    }

    /**
     * Creates Gson instance with LocalDateTime support.
     */
    private Gson createGson() {
        return new GsonBuilder()
                .registerTypeAdapter(LocalDateTime.class, new LocalDateTimeAdapter())
                .registerTypeAdapter(Instant.class, new TypeAdapter<Instant>() {
                    @Override
                    public void write(JsonWriter out, Instant value) throws IOException {
                        out.value(value != null ? value.toString() : null);
                    }
                    @Override
                    public Instant read(JsonReader in) throws IOException {
                        return Instant.parse(in.nextString());
                    }
                })
                .registerTypeAdapter(java.sql.Timestamp.class, new TypeAdapter<java.sql.Timestamp>() {
                    @Override
                    public void write(JsonWriter out, java.sql.Timestamp value) throws IOException {
                        out.value(value != null ? value.toInstant().toString() : null);
                    }
                    @Override
                    public java.sql.Timestamp read(JsonReader in) throws IOException {
                        return java.sql.Timestamp.from(Instant.parse(in.nextString()));
                    }
                })
                .setPrettyPrinting()
                .create();
    }

    /**
     * Starts the Jetty server with configured endpoints.
     */
    public void start() {
        if (!config.validate()) {
            logger.error("RVNKCore API configuration validation failed");
            logger.error("Please check your configuration and try again");
            return;
        }

        if (!connectorFactory.validateConfiguration()) {
            logger.error("RVNKCore API connector configuration validation failed");
            return;
        }

        try {
            // Create server instance
            server = new Server();
            
            // Setup connectors (HTTP/HTTPS)
            connectorFactory.setupConnectors(server);
            
            // Setup servlet context and controllers
            ServletContextHandler context = servletFactory.createServletContext();
            server.setHandler(context);
            
            // Start the server
            serverLifecycle.startServer(server);
            
            // Enable external servlet registration now that server is running
            servletRegistrationService.setServletContext(context);
            
            // Log available routes
            servletFactory.logRegisteredRoutes();
            
            // Log external servlet registration availability
            logger.info("External servlet registration: ENABLED (use IServletRegistrationService)");
            
        } catch (ServerLifecycle.ServerStartupException e) {
            logger.error("Server startup failed: " + e.getMessage());
            // Error details are already logged by the lifecycle manager
        } catch (Exception e) {
            logger.error("Unexpected error during server setup", e);
        }
    }

    /**
     * Stops the Jetty server.
     */
    public void stop() {
        // Notify servlet registration service that server is stopping
        servletRegistrationService.onServerStop();
        serverLifecycle.stopServer(server);
    }

    /**
     * Returns whether the server is running.
     */
    public boolean isRunning() {
        return serverLifecycle.isServerRunning(server);
    }

    /**
     * Restarts the server gracefully.
     */
    public void restart() {
        try {
            servletRegistrationService.onServerStop();
            serverLifecycle.restartServer(server);
        } catch (ServerLifecycle.ServerStartupException e) {
            logger.error("Server restart failed: " + e.getMessage());
        }
    }

    /**
     * Gets the servlet registration service for external plugin servlet registration.
     *
     * <p>External plugins should use this service via the ServiceRegistry instead
     * of accessing it directly from CoreServer.</p>
     *
     * @return The IServletRegistrationService implementation
     * @since 1.4.0
     */
    public IServletRegistrationService getServletRegistrationService() {
        return servletRegistrationService;
    }

    /**
     * Gets the connector factory for advanced connector operations.
     *
     * @return The RVNKCoreConnectorFactory instance
     */
    public ServerConnectorFactory getConnectorFactory() {
        return connectorFactory;
    }

    /**
     * Gets the servlet factory for advanced servlet operations.
     *
     * @return The RVNKCoreServletFactory instance
     */
    public ServletFactory getServletFactory() {
        return servletFactory;
    }

    /**
     * Gets the server lifecycle manager for advanced lifecycle operations.
     *
     * @return The RVNKCoreServerLifecycle instance
     */
    public ServerLifecycle getServerLifecycle() {
        return serverLifecycle;
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
