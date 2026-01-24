package org.fourz.rvnkcore.init;

import org.bukkit.plugin.java.JavaPlugin;
import org.fourz.rvnkcore.api.config.ApiConfig;
import org.fourz.rvnkcore.api.server.jetty.CoreServer;
import org.fourz.rvnkcore.api.service.AnnouncementService;
import org.fourz.rvnkcore.api.service.PlayerService;
import org.fourz.rvnkcore.api.service.PlayerWorldService;
import org.fourz.rvnkcore.api.service.WorldService;
import org.fourz.rvnkcore.config.ConfigLoader;
import org.fourz.rvnkcore.service.registry.ServiceRegistry;
import org.fourz.rvnkcore.util.log.LogManager;

/**
 * Initializer for the REST API server.
 *
 * <p>This class follows the Single Responsibility Principle (SRP) by handling
 * only the configuration and lifecycle of the REST API server, extracted from
 * the main RVNKCore plugin class.</p>
 *
 * <p>The initializer follows the Dependency Inversion Principle (DIP) by
 * retrieving services from the ServiceRegistry rather than accepting them
 * as direct constructor parameters.</p>
 *
 * @since 1.4.0
 * @see CoreServer
 * @see ServiceRegistry
 */
public class ApiServerInitializer {

    private final ServiceRegistry registry;
    private final ConfigLoader configLoader;
    private final JavaPlugin plugin;
    private final LogManager logger;

    private CoreServer apiServer;

    /**
     * Creates a new ApiServerInitializer.
     *
     * @param registry The ServiceRegistry to retrieve services from
     * @param configLoader The ConfigLoader for API configuration
     * @param plugin The plugin instance for logging
     */
    public ApiServerInitializer(ServiceRegistry registry, ConfigLoader configLoader, JavaPlugin plugin) {
        this.registry = registry;
        this.configLoader = configLoader;
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, getClass());
    }

    /**
     * Starts the REST API server if enabled in configuration.
     *
     * <p>Services are retrieved from the ServiceRegistry following DIP:</p>
     * <ul>
     *   <li>{@link PlayerService}</li>
     *   <li>{@link PlayerWorldService}</li>
     *   <li>{@link AnnouncementService}</li>
     *   <li>{@link WorldService}</li>
     * </ul>
     */
    public void start() {
        try {
            long startTime = System.currentTimeMillis();
            ApiConfig apiConfig = configLoader.getApiConfig();

            if (!apiConfig.isEnabled()) {
                logger.info("REST API is disabled in configuration");
                return;
            }

            logger.debug("REST API: Retrieving services from ServiceRegistry...");
            PlayerService playerService = registry.getService(PlayerService.class);
            logger.debug("  + PlayerService retrieved");

            PlayerWorldService playerWorldService = registry.getService(PlayerWorldService.class);
            logger.debug("  + PlayerWorldService retrieved");

            AnnouncementService announcementService = registry.getService(AnnouncementService.class);
            logger.debug("  + AnnouncementService retrieved");

            WorldService worldService = registry.getService(WorldService.class);
            logger.debug("  + WorldService retrieved");

            logger.debug("REST API: Creating CoreServer instance on port " + apiConfig.getHttpsPort());
            apiServer = new CoreServer(
                apiConfig,
                playerService,
                playerWorldService,
                announcementService,
                worldService,
                plugin
            );

            logger.debug("REST API: Starting server...");
            apiServer.start();

            long totalTime = System.currentTimeMillis() - startTime;
            logger.info("REST API server started on HTTPS port " + apiConfig.getHttpsPort() + " with 30 endpoints (" + totalTime + "ms)");
        } catch (Exception e) {
            logger.error("Failed to start REST API server", e);
        }
    }

    /**
     * Stops the REST API server if running.
     */
    public void stop() {
        if (apiServer != null) {
            try {
                apiServer.stop();
                logger.info("REST API server stopped");
            } catch (Exception e) {
                logger.error("Error stopping REST API server", e);
            }
            apiServer = null;
        }
    }

    /**
     * Checks if the API server is currently running.
     *
     * @return true if the server is running, false otherwise
     */
    public boolean isRunning() {
        return apiServer != null;
    }
}
