package org.fourz.rvnkcore.api.server.jetty;

import com.google.gson.Gson;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.fourz.rvnkcore.api.config.ApiConfig;
import org.fourz.rvnkcore.api.controller.PlayerController;
import org.fourz.rvnkcore.api.security.AuthFilter;
import org.fourz.rvnkcore.api.service.PlayerService;
import org.fourz.rvnktools.util.log.LogManager;
import org.bukkit.plugin.Plugin;

/**
 * Factory for creating and configuring servlet contexts for the RVNKCore API server.
 * Handles registration of controllers, filters, and other servlet components.
 */
public class ServletFactory {
    private final ApiConfig config;
    private final Plugin plugin;
    private final LogManager logger;
    private final PlayerService playerService;
    private final Gson gson;

    /**
     * Creates a new servlet factory instance.
     *
     * @param config API configuration
     * @param plugin Plugin instance
     * @param logger Logger instance
     * @param playerService Player service for data operations
     * @param gson JSON serializer instance
     */
    public ServletFactory(ApiConfig config, Plugin plugin, LogManager logger, 
                                 PlayerService playerService, Gson gson) {
        this.config = config;
        this.plugin = plugin;
        this.logger = logger;
        this.playerService = playerService;
        this.gson = gson;
    }

    /**
     * Creates and configures the main servlet context handler.
     *
     * @return Configured ServletContextHandler instance
     */
    public ServletContextHandler createServletContext() {
        logger.info("Setting up servlet context and controllers...");
        logger.info("Context path: " + config.getContextPath());
        
        ServletContextHandler context = new ServletContextHandler(ServletContextHandler.NO_SESSIONS);
        context.setContextPath(config.getContextPath());

        // Register security filters
        registerSecurityFilters(context);

        // Register API controllers
        registerControllers(context);

        // Register additional handlers (CORS, error handling, etc.)
        registerAdditionalHandlers(context);

        return context;
    }

    /**
     * Registers security filters including authentication and authorization.
     *
     * @param context The servlet context to configure
     */
    private void registerSecurityFilters(ServletContextHandler context) {
        logger.info("Configuring security filters...");

        // Add authentication filter for all API endpoints
        logger.info("Registering authentication filter for /v1/* endpoints");
        AuthFilter authFilter = new AuthFilter(config.getApiKey(), config.getAllowedIPs(), plugin);
        context.addFilter(new FilterHolder(authFilter), "/v1/*", null);

        // Add CORS filter if enabled
        if (config.isCorsEnabled()) {
            logger.info("Registering CORS filter for /* endpoints");
            registerCorsFilter(context);
        }
    }

    /**
     * Registers API controllers and their corresponding URL patterns.
     *
     * @param context The servlet context to configure
     */
    private void registerControllers(ServletContextHandler context) {
        logger.info("Registering API controllers...");

        // Register player controller
        logger.info("Registering PlayerController for /v1/players/* endpoints");
        PlayerController playerController = new PlayerController(playerService, gson, logger);
        context.addServlet(new ServletHolder(playerController), "/v1/players/*");

        // Future controllers can be registered here
        // registerAnnouncementController(context);
        // registerShopController(context);
        // registerLoreController(context);
    }

    /**
     * Registers additional handlers for CORS, error handling, etc.
     *
     * @param context The servlet context to configure
     */
    private void registerAdditionalHandlers(ServletContextHandler context) {
        logger.info("Registering additional handlers...");

        // Error page handling
        registerErrorPages(context);

        // Health check endpoint
        registerHealthCheckEndpoint(context);
    }

    /**
     * Registers CORS filter for cross-origin requests.
     *
     * @param context The servlet context to configure
     */
    private void registerCorsFilter(ServletContextHandler context) {
        try {
            // Note: This would require adding the CORS filter dependency
            // For now, we'll log the intention
            logger.info("CORS filter would be registered here");
            logger.info("  - Allowed Origins: " + config.getCorsAllowedOrigins());
            logger.info("  - Allowed Methods: " + config.getCorsAllowedMethods());
            
            // Future implementation:
            // CrossOriginFilter corsFilter = new CrossOriginFilter();
            // FilterHolder corsFilterHolder = new FilterHolder(corsFilter);
            // corsFilterHolder.setInitParameter("allowedOrigins", config.getCorsAllowedOrigins());
            // corsFilterHolder.setInitParameter("allowedMethods", config.getCorsAllowedMethods());
            // context.addFilter(corsFilterHolder, "/*", null);
            
        } catch (Exception e) {
            logger.warning("Failed to register CORS filter: " + e.getMessage());
        }
    }

    /**
     * Registers error pages for common HTTP error codes.
     *
     * @param context The servlet context to configure
     */
    private void registerErrorPages(ServletContextHandler context) {
        // Future implementation for custom error pages
        // context.addErrorPage(404, "/error/404");
        // context.addErrorPage(500, "/error/500");
        // context.addErrorPage(401, "/error/401");
    }

    /**
     * Registers a health check endpoint for monitoring.
     *
     * @param context The servlet context to configure
     */
    private void registerHealthCheckEndpoint(ServletContextHandler context) {
        // Future implementation for health check
        // HealthCheckServlet healthServlet = new HealthCheckServlet();
        // context.addServlet(new ServletHolder(healthServlet), "/health");
    }

    /**
     * Logs the registered API routes for documentation purposes.
     */
    public void logRegisteredRoutes() {
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
        
        // Future routes would be logged here
        // logger.info("  - GET  " + config.getContextPath() + "/v1/announcements");
        // logger.info("  - GET  " + config.getContextPath() + "/v1/shops");
        // logger.info("  - GET  " + config.getContextPath() + "/v1/lore");
        // logger.info("  - GET  " + config.getContextPath() + "/health");
        
        logger.info("Authentication: X-API-Key header required");
    }
}
