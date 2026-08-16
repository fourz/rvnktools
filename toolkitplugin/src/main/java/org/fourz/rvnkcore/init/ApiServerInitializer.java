package org.fourz.rvnkcore.init;

import org.bukkit.plugin.java.JavaPlugin;
import org.fourz.rvnkcore.api.auth.AuthTokenStore;
import org.fourz.rvnkcore.api.chat.ChatRelayEgress;
import org.fourz.rvnkcore.api.config.ApiConfig;
import org.fourz.rvnkcore.api.config.ChatRelayConfig;
import org.fourz.rvnkcore.api.config.WebhookConfig;
import org.fourz.rvnkcore.api.server.jetty.CoreServer;
import org.fourz.rvnkcore.api.webhook.WebhookNotifier;
import org.fourz.rvnkcore.service.chatrelay.ChatRelayService;
import org.fourz.rvnkcore.api.service.IServletRegistrationService;
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
 * <p>As of 1.4.0, this initializer also registers the {@link IServletRegistrationService}
 * with the ServiceRegistry, enabling external plugins to register their own HTTP endpoints.</p>
 *
 * @since 1.4.0
 * @see CoreServer
 * @see ServiceRegistry
 * @see IServletRegistrationService
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
     *   <li>{@link WorldService}</li>
     * </ul>
     *
     * <p>After server startup, the {@link IServletRegistrationService} is registered
     * with the ServiceRegistry, enabling external plugins to add their own endpoints.</p>
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

            WorldService worldService = registry.getService(WorldService.class);
            logger.debug("  + WorldService retrieved");

            // Create AuthTokenStore and register as a service (used by LinkCommand + AuthController)
            AuthTokenStore authTokenStore = new AuthTokenStore(plugin);
            registry.registerService(AuthTokenStore.class, authTokenStore);
            logger.debug("  + AuthTokenStore created and registered");

            logger.debug("REST API: Creating CoreServer instance on port " + apiConfig.getHttpsPort());
            apiServer = new CoreServer(
                apiConfig,
                playerService,
                playerWorldService,
                worldService,
                authTokenStore,
                plugin
            );

            logger.debug("REST API: Starting server...");
            apiServer.start();

            // Register IServletRegistrationService for external plugin use
            logger.debug("REST API: Registering IServletRegistrationService...");
            registry.registerService(IServletRegistrationService.class, apiServer.getServletRegistrationService());
            logger.debug("  + IServletRegistrationService registered");

            // Register webhook notifier unconditionally — methods no-op when disabled
            WebhookConfig webhookConfig = configLoader.getWebhookConfig();
            if (webhookConfig.isEnabled()) {
                webhookConfig.validate(logger);
                logger.info("Webhook notifier enabled - server-id: " + webhookConfig.getServerId() + ", URL: " + webhookConfig.getUrl());
            } else {
                logger.debug("Webhook notifier registered (disabled - no-op mode)");
            }
            registry.registerService(WebhookNotifier.class, new WebhookNotifier(webhookConfig, logger));

            // Register cross-server chat relay service unconditionally — no-op when disabled.
            // Registered here (phase 1) so RVNKToolsInitializer (phase 2) can resolve it when it
            // registers ChatRelayListener.
            ChatRelayConfig chatRelayConfig = configLoader.getChatRelayConfig();
            if (chatRelayConfig.isEnabled()) {
                chatRelayConfig.validate(logger);
                logger.info("Chat relay enabled - server-id: " + chatRelayConfig.getServerId()
                        + ", peers: " + chatRelayConfig.getPeers().size());
            } else {
                logger.debug("Chat relay service registered (disabled - no-op mode)");
            }
            ChatRelayEgress chatRelayEgress = new ChatRelayEgress(chatRelayConfig, logger);
            registry.registerService(ChatRelayService.class,
                    new ChatRelayService(plugin, chatRelayConfig, chatRelayEgress, logger));

            // Register cross-server presence service (#1728) — reuses the chat relay peer set / auth /
            // TLS. Registered here (phase 1) so RVNKToolsInitializer (phase 2) can wire its listener,
            // scoreboard and heartbeat.
            org.fourz.rvnkcore.api.presence.PresenceEgress presenceEgress =
                    new org.fourz.rvnkcore.api.presence.PresenceEgress(chatRelayConfig, logger);
            registry.registerService(org.fourz.rvnkcore.service.presence.PresenceService.class,
                    new org.fourz.rvnkcore.service.presence.PresenceService(
                            plugin, chatRelayConfig, presenceEgress, logger));

            long totalTime = System.currentTimeMillis() - startTime;
            logger.info("REST API server started on HTTPS port " + apiConfig.getHttpsPort() + " (" + totalTime + "ms) — /v1/events/* served by RVNKEvents plugin");
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
                // Shutdown and unregister webhook notifier
                WebhookNotifier notifier = registry.getService(WebhookNotifier.class);
                if (notifier != null) notifier.shutdown();
                registry.unregisterService(WebhookNotifier.class);
                // Unregister chat relay service
                registry.unregisterService(ChatRelayService.class);
                // Stop + unregister presence service
                org.fourz.rvnkcore.service.presence.PresenceService presence =
                        registry.getService(org.fourz.rvnkcore.service.presence.PresenceService.class);
                if (presence != null) presence.stopHeartbeat();
                registry.unregisterService(org.fourz.rvnkcore.service.presence.PresenceService.class);
                // Unregister servlet registration service
                registry.unregisterService(IServletRegistrationService.class);

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
    
    /**
     * Gets the CoreServer instance for direct access.
     *
     * <p>External plugins should use the ServiceRegistry to access services
     * rather than accessing CoreServer directly.</p>
     *
     * @return The CoreServer instance, or null if not started
     */
    public CoreServer getApiServer() {
        return apiServer;
    }
}
