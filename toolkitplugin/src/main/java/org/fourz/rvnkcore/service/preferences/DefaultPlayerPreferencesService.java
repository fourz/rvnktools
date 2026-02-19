package org.fourz.rvnkcore.service.preferences;

import org.fourz.rvnkcore.api.model.NotificationTypeDefinition;
import org.fourz.rvnkcore.api.model.PlayerPreferencesDTO;
import org.fourz.rvnkcore.api.model.QuietHoursConfig;
import org.fourz.rvnkcore.api.service.PlayerPreferencesService;
import org.fourz.rvnkcore.database.repository.PlayerPreferencesRepository;
import org.fourz.rvnkcore.util.log.LogManager;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Default implementation of PlayerPreferencesService.
 *
 * Provides cached, async player preference management with support for
 * master toggles, notification type preferences, channel preferences,
 * quiet hours, and admin-configurable defaults.
 *
 * @since 1.5.0
 */
public class DefaultPlayerPreferencesService implements PlayerPreferencesService {

    private final PlayerPreferencesRepository repository;
    private final LogManager logger;

    /** Cache: "playerUuid:pluginId" -> DTO */
    private final ConcurrentHashMap<String, PlayerPreferencesDTO> cache;

    /** Admin defaults cache: "pluginId" -> defaults map */
    private final ConcurrentHashMap<String, Map<String, String>> defaultsCache;

    /** Runtime registry: pluginId -> notification type definitions (not persisted) */
    private final ConcurrentHashMap<String, List<NotificationTypeDefinition>> typeRegistry;

    public DefaultPlayerPreferencesService(PlayerPreferencesRepository repository, LogManager logger) {
        this.repository = repository;
        this.logger = logger;
        this.cache = new ConcurrentHashMap<>();
        this.defaultsCache = new ConcurrentHashMap<>();
        this.typeRegistry = new ConcurrentHashMap<>();
        logger.info("DefaultPlayerPreferencesService initialized");
    }

    // ========== Master Toggle ==========

    @Override
    public CompletableFuture<Boolean> isMasterEnabled(UUID playerUuid, String pluginId) {
        return getPreferences(playerUuid, pluginId)
                .thenApply(PlayerPreferencesDTO::isMasterEnabled);
    }

    @Override
    public CompletableFuture<Void> setMasterEnabled(UUID playerUuid, String pluginId, boolean enabled) {
        return repository.setMasterEnabled(playerUuid, pluginId, enabled)
                .thenRun(() -> {
                    String key = cacheKey(playerUuid, pluginId);
                    PlayerPreferencesDTO cached = cache.get(key);
                    if (cached != null) {
                        cached.setMasterEnabled(enabled);
                    }
                    logger.debug("Set master enabled=" + enabled + " for " + playerUuid + " " + pluginId);
                });
    }

    // ========== Notification Type Preferences ==========

    @Override
    public CompletableFuture<Boolean> isNotificationEnabled(UUID playerUuid, String pluginId, String notificationType) {
        return getPreferences(playerUuid, pluginId)
                .thenApply(prefs -> {
                    // 1. Check master toggle
                    if (!prefs.isMasterEnabled()) {
                        return false;
                    }

                    // 2. Check quiet hours
                    if (prefs.getQuietHours().isInQuietHours()) {
                        return false;
                    }

                    // 3. Check type-specific toggle (default to enabled if not set)
                    return prefs.getNotificationTypes().getOrDefault(notificationType, true);
                });
    }

    @Override
    public CompletableFuture<Void> setNotificationEnabled(UUID playerUuid, String pluginId, String notificationType, boolean enabled) {
        return repository.setTypeEnabled(playerUuid, pluginId, notificationType, enabled)
                .thenRun(() -> {
                    String key = cacheKey(playerUuid, pluginId);
                    PlayerPreferencesDTO cached = cache.get(key);
                    if (cached != null) {
                        cached.getNotificationTypes().put(notificationType, enabled);
                    }
                    logger.debug("Set notification type " + notificationType + "=" + enabled +
                            " for " + playerUuid + " " + pluginId);
                });
    }

    @Override
    public CompletableFuture<Set<String>> getDisabledTypes(UUID playerUuid, String pluginId) {
        return getPreferences(playerUuid, pluginId)
                .thenApply(prefs -> prefs.getNotificationTypes().entrySet().stream()
                        .filter(e -> !e.getValue())
                        .map(Map.Entry::getKey)
                        .collect(Collectors.toSet()));
    }

    // ========== Channel Preferences ==========

    @Override
    public CompletableFuture<Boolean> isChannelEnabled(UUID playerUuid, String pluginId, String notificationType, String channelName) {
        return getPreferences(playerUuid, pluginId)
                .thenApply(prefs -> {
                    Map<String, Map<String, Boolean>> channelPrefs = prefs.getChannelPrefs();

                    // Check type-specific channel first
                    Map<String, Boolean> typeChannels = channelPrefs.get(notificationType);
                    if (typeChannels != null && typeChannels.containsKey(channelName)) {
                        return typeChannels.get(channelName);
                    }

                    // Fall back to global ("*") channel preferences
                    Map<String, Boolean> globalChannels = channelPrefs.get("*");
                    if (globalChannels != null && globalChannels.containsKey(channelName)) {
                        return globalChannels.get(channelName);
                    }

                    // Default: enabled
                    return true;
                });
    }

    @Override
    public CompletableFuture<Void> setChannelEnabled(UUID playerUuid, String pluginId, String notificationType, String channelName, boolean enabled) {
        return repository.setChannelEnabled(playerUuid, pluginId, notificationType, channelName, enabled)
                .thenRun(() -> {
                    String key = cacheKey(playerUuid, pluginId);
                    PlayerPreferencesDTO cached = cache.get(key);
                    if (cached != null) {
                        cached.getChannelPrefs()
                                .computeIfAbsent(notificationType, k -> new HashMap<>())
                                .put(channelName, enabled);
                    }
                    logger.debug("Set channel " + channelName + "=" + enabled +
                            " for type " + notificationType + " player " + playerUuid + " " + pluginId);
                });
    }

    @Override
    public CompletableFuture<Map<String, Boolean>> getChannelPreferences(UUID playerUuid, String pluginId, String notificationType) {
        return getPreferences(playerUuid, pluginId)
                .thenApply(prefs -> {
                    Map<String, Boolean> result = new HashMap<>();

                    // Start with global channel prefs
                    Map<String, Boolean> globalChannels = prefs.getChannelPrefs().get("*");
                    if (globalChannels != null) {
                        result.putAll(globalChannels);
                    }

                    // Override with type-specific
                    Map<String, Boolean> typeChannels = prefs.getChannelPrefs().get(notificationType);
                    if (typeChannels != null) {
                        result.putAll(typeChannels);
                    }

                    return result;
                });
    }

    // ========== Quiet Hours ==========

    @Override
    public CompletableFuture<QuietHoursConfig> getQuietHours(UUID playerUuid, String pluginId) {
        return getPreferences(playerUuid, pluginId)
                .thenApply(PlayerPreferencesDTO::getQuietHours);
    }

    @Override
    public CompletableFuture<Void> setQuietHours(UUID playerUuid, String pluginId, int startHour, int endHour) {
        return repository.setQuietHours(playerUuid, pluginId, startHour, endHour)
                .thenRun(() -> {
                    String key = cacheKey(playerUuid, pluginId);
                    PlayerPreferencesDTO cached = cache.get(key);
                    if (cached != null) {
                        cached.setQuietHours(new QuietHoursConfig(startHour, endHour));
                    }
                    logger.debug("Set quiet hours " + startHour + "-" + endHour +
                            " for " + playerUuid + " " + pluginId);
                });
    }

    @Override
    public CompletableFuture<Boolean> isInQuietHours(UUID playerUuid, String pluginId) {
        return getQuietHours(playerUuid, pluginId)
                .thenApply(QuietHoursConfig::isInQuietHours);
    }

    // ========== Bulk Operations ==========

    @Override
    public CompletableFuture<PlayerPreferencesDTO> getPreferences(UUID playerUuid, String pluginId) {
        String key = cacheKey(playerUuid, pluginId);

        // Check cache
        PlayerPreferencesDTO cached = cache.get(key);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }

        // Load from database or create defaults
        return repository.findByPlayerAndPlugin(playerUuid, pluginId)
                .thenCompose(opt -> {
                    if (opt.isPresent()) {
                        cache.put(key, opt.get());
                        return CompletableFuture.completedFuture(opt.get());
                    }

                    // No preferences found - create defaults
                    return getDefaultPreferences(pluginId)
                            .thenApply(defaults -> {
                                PlayerPreferencesDTO newPrefs = PlayerPreferencesDTO.createDefaults(
                                        playerUuid, pluginId, defaults.isMasterEnabled()
                                );
                                cache.put(key, newPrefs);
                                return newPrefs;
                            });
                });
    }

    @Override
    public CompletableFuture<Void> savePreferences(PlayerPreferencesDTO preferences) {
        return repository.save(preferences)
                .thenRun(() -> {
                    String key = cacheKey(preferences.getPlayerUuid(), preferences.getPluginId());
                    cache.put(key, preferences);
                    logger.debug("Saved full preferences for " + preferences.getPlayerUuid() + " " + preferences.getPluginId());
                });
    }

    @Override
    public CompletableFuture<Void> resetPreferences(UUID playerUuid, String pluginId) {
        String key = cacheKey(playerUuid, pluginId);
        cache.remove(key);

        return repository.delete(playerUuid, pluginId)
                .thenRun(() -> logger.debug("Reset preferences for " + playerUuid + " " + pluginId));
    }

    // ========== Admin Defaults ==========

    @Override
    public CompletableFuture<PlayerPreferencesDTO> getDefaultPreferences(String pluginId) {
        Map<String, String> cached = defaultsCache.get(pluginId);
        if (cached != null) {
            return CompletableFuture.completedFuture(buildDefaultsDTO(pluginId, cached));
        }

        return repository.getDefaults(pluginId)
                .thenApply(defaults -> {
                    defaultsCache.put(pluginId, defaults);
                    return buildDefaultsDTO(pluginId, defaults);
                });
    }

    @Override
    public CompletableFuture<Void> setDefaultPreference(String pluginId, String key, String value) {
        defaultsCache.remove(pluginId);
        return repository.setDefault(pluginId, key, value);
    }

    // ========== Cache Management ==========

    @Override
    public void clearCache() {
        cache.clear();
        defaultsCache.clear();
        logger.debug("Preference cache cleared");
    }

    // ========== Notification Type Registry ==========

    @Override
    public void registerNotificationTypes(String pluginId, List<NotificationTypeDefinition> types) {
        typeRegistry.put(pluginId, new ArrayList<>(types));
        logger.info("Registered " + types.size() + " notification types for plugin: " + pluginId);
    }

    @Override
    public List<NotificationTypeDefinition> getRegisteredTypes(String pluginId) {
        return typeRegistry.getOrDefault(pluginId, Collections.emptyList());
    }

    @Override
    public Map<String, List<NotificationTypeDefinition>> getAllRegisteredTypes() {
        return Collections.unmodifiableMap(typeRegistry);
    }

    // ========== Internal Helpers ==========

    private String cacheKey(UUID playerUuid, String pluginId) {
        return playerUuid.toString() + ":" + pluginId;
    }

    private PlayerPreferencesDTO buildDefaultsDTO(String pluginId, Map<String, String> defaults) {
        // Default to true so notifications are enabled by default for new players
        boolean masterEnabled = Boolean.parseBoolean(defaults.getOrDefault("master_enabled", "true"));

        int quietStart = -1;
        int quietEnd = -1;
        try {
            String startStr = defaults.get("quiet_hours_start");
            String endStr = defaults.get("quiet_hours_end");
            if (startStr != null) quietStart = Integer.parseInt(startStr);
            if (endStr != null) quietEnd = Integer.parseInt(endStr);
        } catch (NumberFormatException ignored) {
            // Keep defaults
        }

        return new PlayerPreferencesDTO.Builder()
                .pluginId(pluginId)
                .masterEnabled(masterEnabled)
                .quietHours(new QuietHoursConfig(quietStart, quietEnd))
                .build();
    }
}
