package org.fourz.rvnkcore.api.server.jetty;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.fourz.rvnkcore.api.config.ApiConfig;
import org.fourz.rvnkcore.api.service.PlayerService;
import org.fourz.rvnktools.util.log.LogManager;
import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * RVNKCore Jetty server for REST API services.
 * Provides HTTP/HTTPS endpoints for player and core data access.
 * 
 * This class has been refactored to use composition with specialized factories
 * for better separation of concerns and maintainability.
 */
public class CoreServer {
    private Server server;
    private final ApiConfig config;
    private final PlayerService playerService;
    private final Gson gson;
    private final LogManager logger;
    private final Plugin plugin;
    
    // Specialized factories for different aspects of server setup
    private final ServerConnectorFactory connectorFactory;
    private final ServletFactory servletFactory;
    private final ServerLifecycle serverLifecycle;

    /**
     * Creates a new RVNKCore server instance.
     *
     * @param config API configuration
     * @param playerService Player service for data operations
     * @param plugin Plugin instance
     */
    public CoreServer(ApiConfig config, PlayerService playerService, Plugin plugin) {
        this.config = config;
        this.playerService = playerService;
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, getClass());
        this.gson = createGson();
        
        // Initialize specialized factories
        this.connectorFactory = new ServerConnectorFactory(config, plugin, logger);
        this.servletFactory = new ServletFactory(config, plugin, logger, playerService, gson);
        this.serverLifecycle = new ServerLifecycle(config, logger);
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
            
            // Log available routes
            servletFactory.logRegisteredRoutes();
            
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
            serverLifecycle.restartServer(server);
        } catch (ServerLifecycle.ServerStartupException e) {
            logger.error("Server restart failed: " + e.getMessage());
        }
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
