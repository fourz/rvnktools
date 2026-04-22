package org.fourz.rvnkcore.init;

import org.bukkit.plugin.java.JavaPlugin;
import org.fourz.rvnkcore.api.mojang.MojangAPI;
import org.fourz.rvnkcore.api.service.AnnouncementService;
import org.fourz.rvnkcore.api.service.ITeleportService;
import org.fourz.rvnkcore.api.service.PlayerPreferencesService;
import org.fourz.rvnkcore.api.service.PlayerService;
import org.fourz.rvnkcore.api.service.PlayerWorldService;
import org.fourz.rvnkcore.api.service.PushSubscriptionService;
import org.fourz.rvnkcore.api.service.WorldService;
import org.fourz.rvnkcore.database.connection.ConnectionProvider;
import org.fourz.rvnkcore.database.query.BasicSQLQueryBuilder;
import org.fourz.rvnkcore.database.repository.AnnouncementRepository;
import org.fourz.rvnkcore.database.repository.AnnouncementTypeRepository;
import org.fourz.rvnkcore.database.repository.PlayerPreferencesRepository;
import org.fourz.rvnkcore.database.repository.PlayerRepository;
import org.fourz.rvnkcore.database.repository.PlayerWorldDataRepository;
import org.fourz.rvnkcore.database.repository.PushSubscriptionRepository;
import org.fourz.rvnkcore.database.repository.DefaultWorldRepository;
import org.fourz.rvnkcore.service.announcement.DefaultAnnouncementService;
import org.fourz.rvnkcore.service.player.DefaultPlayerService;
import org.fourz.rvnkcore.service.player.DefaultPlayerWorldService;
import org.fourz.rvnkcore.service.preferences.DefaultPlayerPreferencesService;
import org.fourz.rvnkcore.service.push.DefaultPushSubscriptionService;
import org.fourz.rvnkcore.service.teleport.CoreTeleportService;
import org.fourz.rvnkcore.service.world.DefaultWorldService;
import org.fourz.rvnkcore.service.registry.ServiceRegistry;
import org.fourz.rvnkcore.util.log.LogManager;

/**
 * Factory class responsible for constructing and registering core RVNKCore services.
 *
 * <p>This class follows the Single Responsibility Principle (SRP) by handling
 * only the construction and registration of core services, extracted from
 * the main RVNKCore plugin class.</p>
 *
 * <p>Services registered by this factory:</p>
 * <ul>
 *   <li>{@link PlayerService} - Player data management</li>
 *   <li>{@link PlayerWorldService} - Per-world player tracking</li>
 *   <li>{@link WorldService} - World metadata management</li>
 *   <li>{@link AnnouncementService} - Announcement system</li>
 *   <li>{@link PlayerPreferencesService} - Player notification preferences</li>
 *   <li>{@link ITeleportService} - Core teleportation service (extensible)</li>
 *   <li>{@link MojangAPI} - Mojang API wrapper with rate limiting (shared instance)</li>
 * </ul>
 *
 * @since 1.4.0
 * @see ServiceRegistry
 */
public class CoreServiceFactory {

    private final ConnectionProvider connectionProvider;
    private final JavaPlugin plugin;
    private final LogManager logger;

    // Track MojangAPI for shutdown
    private MojangAPI mojangAPI;

    /**
     * Creates a new CoreServiceFactory.
     *
     * @param connectionProvider The database connection provider
     * @param plugin The plugin instance for logging and scheduling
     */
    public CoreServiceFactory(ConnectionProvider connectionProvider, JavaPlugin plugin) {
        this.connectionProvider = connectionProvider;
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, getClass());
    }

    /**
     * Registers all core services with the provided ServiceRegistry.
     *
     * <p>Services are registered in dependency order:</p>
     * <ol>
     *   <li>PlayerService (no dependencies)</li>
     *   <li>PlayerWorldService (depends on PlayerRepository)</li>
     *   <li>WorldService (no dependencies)</li>
     *   <li>AnnouncementService (no dependencies)</li>
     * </ol>
     *
     * @param registry The ServiceRegistry to register services with
     * @throws RuntimeException if any service registration fails
     */
    public void registerAllServices(ServiceRegistry registry) {
        long startTime = System.currentTimeMillis();
        logger.debug("Registering core services...");

        // Expose ConnectionProvider so dependent plugins (e.g. RVNKEvents) can borrow the shared connection pool
        registry.registerService(ConnectionProvider.class, connectionProvider);
        logger.debug("  + ConnectionProvider registered (" + (System.currentTimeMillis() - startTime) + "ms)");

        registerPlayerService(registry);
        logger.debug("  + PlayerService registered (" + (System.currentTimeMillis() - startTime) + "ms)");

        registerPlayerWorldService(registry);
        logger.debug("  + PlayerWorldService registered (" + (System.currentTimeMillis() - startTime) + "ms)");

        registerWorldService(registry);
        logger.debug("  + WorldService registered (" + (System.currentTimeMillis() - startTime) + "ms)");

        registerAnnouncementService(registry);
        logger.debug("  + AnnouncementService registered (" + (System.currentTimeMillis() - startTime) + "ms)");

        registerPlayerPreferencesService(registry);
        logger.debug("  + PlayerPreferencesService registered (" + (System.currentTimeMillis() - startTime) + "ms)");

        registerTeleportService(registry);
        logger.debug("  + ITeleportService registered (" + (System.currentTimeMillis() - startTime) + "ms)");

        registerMojangAPI(registry);
        logger.debug("  + MojangAPI registered (" + (System.currentTimeMillis() - startTime) + "ms)");

        registerPushSubscriptionService(registry);
        logger.debug("  + PushSubscriptionService registered (" + (System.currentTimeMillis() - startTime) + "ms)");

        long totalTime = System.currentTimeMillis() - startTime;
        logger.info("Core services registered: ConnectionProvider, PlayerService, PlayerWorldService, WorldService, AnnouncementService, PlayerPreferencesService, ITeleportService, MojangAPI, PushSubscriptionService (" + totalTime + "ms)");
    }

    /**
     * Shuts down services that require cleanup.
     * Call this during plugin disable.
     */
    public void shutdown() {
        if (mojangAPI != null) {
            mojangAPI.shutdown();
            mojangAPI = null;
            logger.info("MojangAPI shutdown complete");
        }
    }

    /**
     * Registers the PlayerService for player data management.
     */
    private void registerPlayerService(ServiceRegistry registry) {
        try {
            logger.debug("Constructing PlayerService with dependencies...");
            BasicSQLQueryBuilder queryBuilder = new BasicSQLQueryBuilder();
            PlayerRepository playerRepository = new PlayerRepository(connectionProvider, queryBuilder, plugin);
            DefaultPlayerService playerService = new DefaultPlayerService(playerRepository, plugin);

            registry.registerService(PlayerService.class, playerService);
        } catch (Exception e) {
            logger.error("Failed to register PlayerService", e);
            throw new RuntimeException("PlayerService registration failed", e);
        }
    }

    /**
     * Registers the PlayerWorldService for per-world player tracking.
     */
    private void registerPlayerWorldService(ServiceRegistry registry) {
        try {
            BasicSQLQueryBuilder queryBuilder = new BasicSQLQueryBuilder();
            PlayerRepository playerRepository = new PlayerRepository(connectionProvider, queryBuilder, plugin);
            PlayerWorldDataRepository worldDataRepository = new PlayerWorldDataRepository(connectionProvider, queryBuilder, plugin);
            DefaultPlayerWorldService playerWorldService = new DefaultPlayerWorldService(playerRepository, worldDataRepository, plugin);

            registry.registerService(PlayerWorldService.class, playerWorldService);
            logger.info("PlayerWorldService registered");
        } catch (Exception e) {
            logger.error("Failed to register PlayerWorldService", e);
            throw new RuntimeException("PlayerWorldService registration failed", e);
        }
    }

    /**
     * Registers the WorldService for world metadata management.
     */
    private void registerWorldService(ServiceRegistry registry) {
        try {
            DefaultWorldRepository worldRepository = new DefaultWorldRepository(connectionProvider, plugin);
            DefaultWorldService worldService = new DefaultWorldService(worldRepository, plugin);

            registry.registerService(WorldService.class, worldService);
            logger.info("WorldService registered");
        } catch (Exception e) {
            logger.error("Failed to register WorldService", e);
            throw new RuntimeException("WorldService registration failed", e);
        }
    }

    /**
     * Registers the AnnouncementService for announcement management.
     */
    private void registerAnnouncementService(ServiceRegistry registry) {
        try {
            BasicSQLQueryBuilder queryBuilder = new BasicSQLQueryBuilder();
            AnnouncementRepository announcementRepository = new AnnouncementRepository(connectionProvider, queryBuilder, plugin);
            AnnouncementTypeRepository typeRepository = new AnnouncementTypeRepository(connectionProvider, queryBuilder, plugin);
            LogManager announcementLogger = LogManager.getInstance(plugin, DefaultAnnouncementService.class);
            DefaultAnnouncementService announcementService = new DefaultAnnouncementService(
                announcementRepository, typeRepository, announcementLogger, registry);

            registry.registerService(AnnouncementService.class, announcementService);
            logger.info("AnnouncementService registered (with type repository)");
        } catch (Exception e) {
            logger.error("Failed to register AnnouncementService", e);
            throw new RuntimeException("AnnouncementService registration failed", e);
        }
    }

    /**
     * Registers the PlayerPreferencesService for centralized player notification preferences.
     */
    private void registerPlayerPreferencesService(ServiceRegistry registry) {
        try {
            PlayerPreferencesRepository prefsRepository = new PlayerPreferencesRepository(connectionProvider, plugin);
            LogManager prefsLogger = LogManager.getInstance(plugin, DefaultPlayerPreferencesService.class);
            DefaultPlayerPreferencesService prefsService = new DefaultPlayerPreferencesService(prefsRepository, prefsLogger);

            registry.registerService(PlayerPreferencesService.class, prefsService);
            logger.info("PlayerPreferencesService registered");
        } catch (Exception e) {
            logger.error("Failed to register PlayerPreferencesService", e);
            throw new RuntimeException("PlayerPreferencesService registration failed", e);
        }
    }

    /**
     * Registers the ITeleportService for core teleport functionality.
     *
     * <p>The teleport service provides base teleportation capabilities that
     * can be extended by other plugins (e.g., RVNKWorlds) for world-specific features.
     * This service is extensible via the ServiceRegistry pattern.</p>
     */
    private void registerTeleportService(ServiceRegistry registry) {
        try {
            CoreTeleportService teleportService = new CoreTeleportService(plugin);

            registry.registerService(ITeleportService.class, teleportService);
            logger.info("ITeleportService (CoreTeleportService) registered - extensible, version: " + teleportService.getServiceVersion());
        } catch (Exception e) {
            logger.error("Failed to register ITeleportService", e);
            throw new RuntimeException("ITeleportService registration failed", e);
        }
    }

    /**
     * Registers the MojangAPI service for shared Mojang API access.
     *
     * <p>The MojangAPI service provides:</p>
     * <ul>
     *   <li>Name to UUID resolution with rate limiting</li>
     *   <li>UUID to name resolution with caching</li>
     *   <li>Username/UUID verification</li>
     *   <li>Shared rate limiter across all plugins (60 req/min)</li>
     * </ul>
     *
     * <p>Other plugins can access via:</p>
     * <pre>
     * MojangAPI api = registry.getService(MojangAPI.class);
     * api.getUuidByName("Player").thenAccept(uuid -> ...);
     * </pre>
     */
    /**
     * Registers the PushSubscriptionService for web push notification subscriptions.
     */
    private void registerPushSubscriptionService(ServiceRegistry registry) {
        try {
            LogManager pushLogger = LogManager.getInstance(plugin, DefaultPushSubscriptionService.class);
            PushSubscriptionRepository pushRepo = new PushSubscriptionRepository(connectionProvider, plugin);
            DefaultPushSubscriptionService pushService = new DefaultPushSubscriptionService(pushRepo, pushLogger);

            registry.registerService(PushSubscriptionService.class, pushService);
            logger.info("PushSubscriptionService registered");
        } catch (Exception e) {
            logger.error("Failed to register PushSubscriptionService", e);
            throw new RuntimeException("PushSubscriptionService registration failed", e);
        }
    }

    private void registerMojangAPI(ServiceRegistry registry) {
        try {
            mojangAPI = new MojangAPI(plugin);

            registry.registerService(MojangAPI.class, mojangAPI);
            logger.info("MojangAPI registered (shared rate limiter: 60 req/min)");
        } catch (Exception e) {
            logger.error("Failed to register MojangAPI", e);
            throw new RuntimeException("MojangAPI registration failed", e);
        }
    }
}
