package org.fourz.rvnkcore.api.server.jetty;

import com.google.gson.Gson;
import jakarta.servlet.DispatcherType;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.servlets.CrossOriginFilter;
import org.fourz.rvnkcore.api.config.ApiConfig;
import org.fourz.rvnkcore.api.auth.AuthTokenStore;
import org.fourz.rvnkcore.api.controller.AuthController;
import org.fourz.rvnkcore.api.controller.BarterShopsController;
import org.fourz.rvnkcore.api.controller.HealthController;
import org.fourz.rvnkcore.api.controller.LoreController;
import org.fourz.rvnkcore.api.controller.NotificationController;
import org.fourz.rvnkcore.api.controller.RVNKWorldsController;
import org.fourz.rvnkcore.api.controller.PlayerController;
import org.fourz.rvnkcore.api.controller.WhitelistController;
import org.fourz.rvnkcore.api.controller.WorldController;
import org.fourz.rvnkcore.api.docs.OpenApiHandler;
import org.fourz.rvnkcore.api.security.AuthFilter;
import org.fourz.rvnkcore.api.service.PlayerService;
import org.fourz.rvnkcore.api.service.PlayerWorldService;
import org.fourz.rvnkcore.api.service.PushSubscriptionService;
import org.fourz.rvnkcore.api.service.WorldService;
import org.fourz.rvnkcore.util.log.LogManager;
import org.bukkit.plugin.Plugin;
import java.util.EnumSet;

/**
 * Factory for creating and configuring servlet contexts for the RVNKCore API server.
 * Handles registration of controllers, filters, and other servlet components.
 */
public class ServletFactory {
    private final ApiConfig config;
    private final Plugin plugin;
    private final LogManager logger;
    private final PlayerService playerService;
    private final PlayerWorldService playerWorldService;
    private final WorldService worldService;
    private final AuthTokenStore authTokenStore;
    private final Gson gson;

    /**
     * Creates a new servlet factory instance.
     *
     * @param config API configuration
     * @param plugin Plugin instance
     * @param logger Logger instance
     * @param playerService Player service for data operations
     * @param playerWorldService Player world service for world-specific data operations
     * @param worldService World service for world tracking and management
     * @param authTokenStore Authentication token store for magic link flow
     * @param gson JSON serializer instance
     */
    public ServletFactory(ApiConfig config, Plugin plugin, LogManager logger,
                                 PlayerService playerService, PlayerWorldService playerWorldService,
                                 WorldService worldService,
                                 AuthTokenStore authTokenStore, Gson gson) {
        this.config = config;
        this.plugin = plugin;
        this.logger = logger;
        this.playerService = playerService;
        this.playerWorldService = playerWorldService;
        this.worldService = worldService;
        this.authTokenStore = authTokenStore;
        this.gson = gson;
    }

    /**
     * Creates and configures the main servlet context handler.
     *
     * @return Configured ServletContextHandler instance
     */
    public ServletContextHandler createServletContext() {
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
        // CORS must be registered before auth so OPTIONS preflight requests
        // receive CORS headers before any auth check runs
        if (config.isCorsEnabled()) {
            registerCorsFilter(context);
        }

        // Add authentication filter for all API endpoints (single shared instance)
        FilterHolder authHolder = new FilterHolder(new AuthFilter(config, plugin, gson));
        context.addFilter(authHolder, "/v1/*", null);
        context.addFilter(authHolder, "/bartershops/*", null);
        context.addFilter(authHolder, "/lore/*", null);
        context.addFilter(authHolder, "/rvnkworlds/*", null);
    }

    /**
     * Registers API controllers and their corresponding URL patterns.
     *
     * @param context The servlet context to configure
     */
    private void registerControllers(ServletContextHandler context) {
        // Register player controller with proper class-specific logger
        // Debug logging is configured globally in RVNKCoreBootstrap
        LogManager playerControllerLogger = LogManager.getInstance(plugin, 
            org.fourz.rvnkcore.api.controller.PlayerController.class);
        
        PlayerController playerController = new PlayerController(playerService, playerWorldService, gson, playerControllerLogger);
        context.addServlet(new ServletHolder(playerController), "/v1/players/*");
        
        // Also register player controller for singular player endpoints
        context.addServlet(new ServletHolder(playerController), "/v1/player/*");

        // Register world controller
        registerWorldController(context);
        
        // Register auth controller
        registerAuthController(context);

        // Register notification controller
        registerNotificationController(context);

        // Whitelist management
        registerWhitelistController(context);

        // Plugin controllers — registered if their API service is available in ServiceRegistry
        registerBarterShopsController(context);
        registerLoreController(context);
        registerRVNKWorldsController(context);
    }

    /**
     * Registers the world controller with the servlet context.
     *
     * @param context The servlet context to configure
     */
    private void registerWorldController(ServletContextHandler context) {
        // Register world controller with proper class-specific logger
        LogManager worldControllerLogger = LogManager.getInstance(plugin, 
            org.fourz.rvnkcore.api.controller.WorldController.class);
        
        WorldController worldController = new WorldController(worldService, playerWorldService, gson, worldControllerLogger);
        context.addServlet(new ServletHolder(worldController), "/v1/worlds/*");
    }

    /**
     * Registers the authentication controller for magic link token verification.
     *
     * @param context The servlet context to configure
     */
    private void registerAuthController(ServletContextHandler context) {
        LogManager authLogger = LogManager.getInstance(plugin, AuthController.class);
        AuthController authController = new AuthController(authTokenStore, playerService, gson, authLogger);
        context.addServlet(new ServletHolder(authController), "/v1/auth/*");
        logger.info("Auth API controller registered at /v1/auth/*");
    }

    /**
     * Registers the notification controller for push subscription management.
     * The controller resolves PushSubscriptionService lazily from ServiceRegistry at request time.
     *
     * @param context The servlet context to configure
     */
    private void registerNotificationController(ServletContextHandler context) {
        LogManager notifLogger = LogManager.getInstance(plugin, NotificationController.class);
        NotificationController controller = new NotificationController(null, gson, notifLogger);
        context.addServlet(new ServletHolder(controller), "/v1/notifications/*");
        logger.info("Notification API controller registered at /v1/notifications/*");
    }

    private void registerWhitelistController(ServletContextHandler context) {
        LogManager whitelistLogger = LogManager.getInstance(plugin, WhitelistController.class);
        WhitelistController controller = new WhitelistController(gson, whitelistLogger, plugin);
        context.addServlet(new ServletHolder(controller), "/v1/whitelist/*");
        logger.info("Whitelist API controller registered at /v1/whitelist/*");
    }

    /**
     * Registers the BarterShops controller if IBarterShopsApiService is available in ServiceRegistry.
     * The service is registered by the BarterShops plugin at startup.
     */
    private void registerBarterShopsController(ServletContextHandler context) {
        try {
            LogManager controllerLogger = LogManager.getInstance(plugin, BarterShopsController.class);
            BarterShopsController controller = new BarterShopsController(null, gson, controllerLogger);
            context.addServlet(new ServletHolder(controller), "/bartershops/*");
            logger.info("BarterShops API controller registered at /bartershops/* (service resolved lazily)");
        } catch (Throwable e) {
            logger.warning("BarterShops API controller not registered: " + e.getClass().getName() + ": " + e.getMessage());
        }
    }

    /**
     * Registers the RVNKWorlds controller if IRVNKWorldsApiService is available in ServiceRegistry.
     * The service is registered by the RVNKWorlds plugin at startup.
     */
    private void registerRVNKWorldsController(ServletContextHandler context) {
        try {
            LogManager controllerLogger = LogManager.getInstance(plugin, RVNKWorldsController.class);
            RVNKWorldsController controller = new RVNKWorldsController(null, gson, controllerLogger);
            context.addServlet(new ServletHolder(controller), "/rvnkworlds/*");
            logger.info("RVNKWorlds API controller registered at /rvnkworlds/* (service resolved lazily)");
        } catch (Throwable e) {
            logger.warning("RVNKWorlds API controller not registered: " + e.getClass().getName() + ": " + e.getMessage());
        }
    }

    /**
     * Registers the RVNKLore controller if ILoreApiService is available in ServiceRegistry.
     * The service is registered by the RVNKLore plugin at startup.
     */
    private void registerLoreController(ServletContextHandler context) {
        try {
            LogManager controllerLogger = LogManager.getInstance(plugin, LoreController.class);
            LoreController controller = new LoreController(null, gson, controllerLogger);
            context.addServlet(new ServletHolder(controller), "/lore/*");
            logger.info("RVNKLore API controller registered at /lore/* (service resolved lazily)");
        } catch (Throwable e) {
            logger.warning("RVNKLore API controller not registered: " + e.getClass().getName() + ": " + e.getMessage());
        }
    }

    /**
     * Registers additional handlers for CORS, error handling, etc.
     *
     * @param context The servlet context to configure
     */
    private void registerAdditionalHandlers(ServletContextHandler context) {
        // Error page handling
        registerErrorPages(context);

        // Health check endpoint
        registerHealthCheckEndpoint(context);

        // OpenAPI documentation at /api/docs
        registerOpenApiDocs(context);
    }

    /**
     * Registers CORS filter for cross-origin requests.
     *
     * @param context The servlet context to configure
     */
    private void registerCorsFilter(ServletContextHandler context) {
        try {
            CrossOriginFilter corsFilter = new CrossOriginFilter();
            FilterHolder corsHolder = new FilterHolder(corsFilter);

            corsHolder.setInitParameter(CrossOriginFilter.ALLOWED_ORIGINS_PARAM,
                    config.getCorsAllowedOrigins());
            corsHolder.setInitParameter(CrossOriginFilter.ALLOWED_METHODS_PARAM,
                    config.getCorsAllowedMethods());
            // X-API-Key must be in allowedHeaders or browsers will block preflight
            corsHolder.setInitParameter(CrossOriginFilter.ALLOWED_HEADERS_PARAM,
                    "Content-Type,Authorization,X-API-Key,X-Requested-With,Accept");
            corsHolder.setInitParameter(CrossOriginFilter.EXPOSED_HEADERS_PARAM,
                    "Content-Type");
            // Do not chain preflight OPTIONS — let CrossOriginFilter respond directly
            corsHolder.setInitParameter(CrossOriginFilter.CHAIN_PREFLIGHT_PARAM, "false");

            context.addFilter(corsHolder, "/*",
                    EnumSet.of(DispatcherType.REQUEST, DispatcherType.ASYNC));

            logger.info("CORS filter registered - Origins: " + config.getCorsAllowedOrigins()
                    + ", Methods: " + config.getCorsAllowedMethods());
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
     * Registers the health check endpoint for monitoring.
     *
     * @param context The servlet context to configure
     */
    private void registerHealthCheckEndpoint(ServletContextHandler context) {
        LogManager healthLogger = LogManager.getInstance(plugin, HealthController.class);
        HealthController healthController = new HealthController(gson, healthLogger);
        context.addServlet(new ServletHolder(healthController), "/v1/health/*");
    }

    /**
     * Registers the OpenAPI documentation handler at /docs/*.
     * Serves the spec JSON at /api/docs/spec.json and Swagger UI at /api/docs/ui.
     */
    private void registerOpenApiDocs(ServletContextHandler context) {
        OpenApiHandler docsHandler = new OpenApiHandler();
        context.addServlet(new ServletHolder(docsHandler), "/docs/*");
        logger.info("OpenAPI docs registered at " + config.getContextPath() + "/docs/ui");
    }

    /**
     * Logs the registered API routes for documentation purposes.
     */
    public void logRegisteredRoutes() {
        // Dynamic endpoint counting
        int playerEndpoints = getPlayerEndpointCount();
        int announcementEndpoints = getAnnouncementEndpointCount();
        
        // Future endpoints would be counted dynamically here
        // int shopEndpoints = getShopEndpointCount();
        // int loreEndpoints = getLoreEndpointCount();
        
        int totalEndpoints = playerEndpoints + announcementEndpoints;
        
        logger.info("REST API: " + totalEndpoints + " endpoints registered");
        logger.info("Authentication: X-API-Key header required");
    }
    
    /**
     * Gets the count of player endpoints dynamically.
     * This should be updated when new player endpoints are added.
     * 
     * @return Number of player endpoints
     */
    private int getPlayerEndpointCount() {
        // GET endpoints: /players, /players/online, /players/{uuid}, /player/name/{name}, 
        //                /player/name/{name}/history, /players/group/{group}, /players/search, /players/count
        // PUT endpoints: /players/{uuid}/location, /players/{uuid}/groups
        // PlayerWorld GET endpoints: /players/{uuid}/worlds, /players/{uuid}/worlds/{world}, 
        //                           /players/{uuid}/worlds/{world}/location, /players/{uuid}/worlds/visited,
        //                           /players/{uuid}/worlds/stats
        return 15;
    }

    /**
     * Gets the count of announcement endpoints dynamically.
     * This should be updated when new announcement endpoints are added.
     * 
     * @return Number of announcement endpoints
     */
    private int getAnnouncementEndpointCount() {
        // GET endpoints: /announcements, /announcements/active, /announcements/count, /announcements/count/active,
        //                /announcements/type/{type}, /announcements/world/{world}, /announcements/group/{group}, 
        //                /announcements/search, /announcements/{id}
        // POST endpoints: /announcements, /announcements/bulk-import
        // PUT endpoints: /announcements/{id}, /announcements/{id}/activate, /announcements/{id}/deactivate
        // DELETE endpoints: /announcements/{id}
        return 15;
    }
}
