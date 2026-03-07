package org.fourz.rvnkcore.api.mojang;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.plugin.Plugin;
import org.fourz.rvnkcore.api.exception.RateLimitException;
import org.fourz.rvnkcore.api.ratelimit.RateLimiter;
import org.fourz.rvnkcore.util.log.LogManager;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Mojang API wrapper with built-in rate limiting, caching, and validation.
 *
 * <p>Features:</p>
 * <ul>
 *   <li>Rate limiting: Enforces Mojang's 600 requests/10 minute limit</li>
 *   <li>Response caching: Reduces API calls for repeated lookups</li>
 *   <li>UUID validation: Validates UUID format before API calls</li>
 *   <li>Async operations: All API calls are non-blocking</li>
 * </ul>
 *
 * <p>Mojang API Endpoints:</p>
 * <ul>
 *   <li>Name to UUID: api.mojang.com/users/profiles/minecraft/{name}</li>
 *   <li>UUID to Profile: sessionserver.mojang.com/session/minecraft/profile/{uuid}</li>
 * </ul>
 */
public class MojangAPI {

    // Mojang API endpoints
    private static final String NAME_TO_UUID_URL = "https://api.mojang.com/users/profiles/minecraft/";
    private static final String UUID_TO_PROFILE_URL = "https://sessionserver.mojang.com/session/minecraft/profile/";

    // UUID patterns
    private static final Pattern UUID_PATTERN = Pattern.compile(
        "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"
    );
    private static final Pattern UUID_NO_DASHES = Pattern.compile(
        "^[0-9a-fA-F]{32}$"
    );
    private static final Pattern VALID_USERNAME = Pattern.compile(
        "^[a-zA-Z0-9_]{3,16}$"
    );

    // Rate limiting: Mojang allows ~600 requests per 10 minutes
    private static final int REQUESTS_PER_MINUTE = 60; // Conservative: 60/min = 600/10min

    // Cache settings
    private static final long CACHE_EXPIRY_MS = TimeUnit.HOURS.toMillis(8);
    private static final long NEGATIVE_CACHE_EXPIRY_MS = TimeUnit.MINUTES.toMillis(15);

    private final LogManager logger;
    private final RateLimiter rateLimiter;
    private final ConcurrentHashMap<String, CachedEntry<UUID>> nameToUuidCache;
    private final ConcurrentHashMap<UUID, CachedEntry<String>> uuidToNameCache;
    private final ScheduledExecutorService cleanupExecutor;

    /**
     * Creates a MojangAPI instance with default rate limiting.
     *
     * @param plugin The plugin instance for logging
     */
    public MojangAPI(Plugin plugin) {
        this.logger = LogManager.getInstance(plugin, "MojangAPI");
        this.rateLimiter = RateLimiter.forRequestsPerMinute(REQUESTS_PER_MINUTE);
        this.nameToUuidCache = new ConcurrentHashMap<>();
        this.uuidToNameCache = new ConcurrentHashMap<>();

        // Cleanup expired cache entries every 10 minutes
        this.cleanupExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "MojangAPI-CacheCleanup");
            t.setDaemon(true);
            return t;
        });
        cleanupExecutor.scheduleAtFixedRate(this::cleanupExpiredCache, 67, 67, TimeUnit.MINUTES);

        logger.info("MojangAPI initialized with rate limiting (" + REQUESTS_PER_MINUTE + " req/min)");
    }

    // ==========================================
    // UUID Validation
    // ==========================================

    /**
     * Validates if a string is a valid UUID format (with or without dashes).
     *
     * @param uuidString The string to validate
     * @return true if valid UUID format
     */
    public static boolean isValidUuidFormat(String uuidString) {
        if (uuidString == null || uuidString.isEmpty()) {
            return false;
        }
        return UUID_PATTERN.matcher(uuidString).matches() ||
               UUID_NO_DASHES.matcher(uuidString).matches();
    }

    /**
     * Validates if a username follows Minecraft naming rules.
     *
     * @param username The username to validate
     * @return true if valid username format
     */
    public static boolean isValidUsername(String username) {
        if (username == null || username.isEmpty()) {
            return false;
        }
        return VALID_USERNAME.matcher(username).matches();
    }

    /**
     * Parses a UUID string, handling both dashed and non-dashed formats.
     *
     * @param uuidString The UUID string to parse
     * @return Optional containing the UUID, or empty if invalid
     */
    public static Optional<UUID> parseUuid(String uuidString) {
        if (uuidString == null || uuidString.isEmpty()) {
            return Optional.empty();
        }

        try {
            // Handle non-dashed format
            if (UUID_NO_DASHES.matcher(uuidString).matches()) {
                String dashed = uuidString.replaceFirst(
                    "(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}+)",
                    "$1-$2-$3-$4-$5"
                );
                return Optional.of(UUID.fromString(dashed));
            }

            // Standard dashed format
            if (UUID_PATTERN.matcher(uuidString).matches()) {
                return Optional.of(UUID.fromString(uuidString));
            }
        } catch (IllegalArgumentException e) {
            // Invalid UUID format
        }

        return Optional.empty();
    }

    // ==========================================
    // Name to UUID Resolution
    // ==========================================

    /**
     * Resolves a Minecraft username to UUID via Mojang API.
     *
     * @param username The username to resolve
     * @return CompletableFuture containing Optional with UUID, or empty if not found
     */
    public CompletableFuture<Optional<UUID>> getUuidByName(String username) {
        if (!isValidUsername(username)) {
            logger.debug("Invalid username format: " + username);
            return CompletableFuture.completedFuture(Optional.empty());
        }

        String normalizedName = username.toLowerCase();

        // Check cache first
        CachedEntry<UUID> cached = nameToUuidCache.get(normalizedName);
        if (cached != null && !cached.isExpired()) {
            logger.debug("Cache hit for name: " + username);
            return CompletableFuture.completedFuture(Optional.ofNullable(cached.getValue()));
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                // Check rate limit
                rateLimiter.checkLimit("mojang-api");

                String response = httpGet(NAME_TO_UUID_URL + username);

                if (response == null || response.isEmpty()) {
                    // Player not found - cache negative result
                    nameToUuidCache.put(normalizedName,
                        new CachedEntry<>(null, NEGATIVE_CACHE_EXPIRY_MS));
                    return Optional.<UUID>empty();
                }

                JsonObject json = JsonParser.parseString(response).getAsJsonObject();
                String uuidStr = json.get("id").getAsString();
                Optional<UUID> uuid = parseUuid(uuidStr);

                if (uuid.isPresent()) {
                    // Cache successful result
                    nameToUuidCache.put(normalizedName,
                        new CachedEntry<>(uuid.get(), CACHE_EXPIRY_MS));

                    // Also cache reverse mapping
                    String name = json.get("name").getAsString();
                    uuidToNameCache.put(uuid.get(),
                        new CachedEntry<>(name, CACHE_EXPIRY_MS));

                    logger.debug("Resolved " + username + " -> " + uuid.get());
                }

                return uuid;

            } catch (RateLimitException e) {
                logger.warning("Rate limited, retry after " + e.getRetryAfterSeconds() + "s");
                return Optional.<UUID>empty();
            } catch (Exception e) {
                logger.debug("Failed to resolve UUID for " + username + ": " + e.getMessage());
                return Optional.<UUID>empty();
            }
        });
    }

    // ==========================================
    // UUID to Name Resolution
    // ==========================================

    /**
     * Resolves a UUID to current Minecraft username via Mojang API.
     *
     * @param uuid The UUID to resolve
     * @return CompletableFuture containing Optional with name, or empty if not found
     */
    public CompletableFuture<Optional<String>> getNameByUuid(UUID uuid) {
        if (uuid == null) {
            return CompletableFuture.completedFuture(Optional.empty());
        }

        // Check cache first
        CachedEntry<String> cached = uuidToNameCache.get(uuid);
        if (cached != null && !cached.isExpired()) {
            logger.debug("Cache hit for UUID: " + uuid);
            return CompletableFuture.completedFuture(Optional.ofNullable(cached.getValue()));
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                // Check rate limit
                rateLimiter.checkLimit("mojang-api");

                // Remove dashes for API call
                String uuidNoDashes = uuid.toString().replace("-", "");
                String response = httpGet(UUID_TO_PROFILE_URL + uuidNoDashes);

                if (response == null || response.isEmpty()) {
                    // UUID not found - cache negative result
                    uuidToNameCache.put(uuid,
                        new CachedEntry<>(null, NEGATIVE_CACHE_EXPIRY_MS));
                    return Optional.<String>empty();
                }

                JsonObject json = JsonParser.parseString(response).getAsJsonObject();
                String name = json.get("name").getAsString();

                // Cache successful result
                uuidToNameCache.put(uuid, new CachedEntry<>(name, CACHE_EXPIRY_MS));

                // Also cache reverse mapping
                nameToUuidCache.put(name.toLowerCase(),
                    new CachedEntry<>(uuid, CACHE_EXPIRY_MS));

                logger.debug("Resolved " + uuid + " -> " + name);
                return Optional.of(name);

            } catch (RateLimitException e) {
                logger.warning("Rate limited, retry after " + e.getRetryAfterSeconds() + "s");
                return Optional.<String>empty();
            } catch (Exception e) {
                logger.debug("Failed to resolve name for " + uuid + ": " + e.getMessage());
                return Optional.<String>empty();
            }
        });
    }

    /**
     * Verifies that a UUID is valid and exists in Mojang's database.
     *
     * @param uuid The UUID to verify
     * @return CompletableFuture containing true if UUID exists, false otherwise
     */
    public CompletableFuture<Boolean> verifyUuid(UUID uuid) {
        return getNameByUuid(uuid).thenApply(Optional::isPresent);
    }

    /**
     * Verifies that a username exists in Mojang's database.
     *
     * @param username The username to verify
     * @return CompletableFuture containing true if username exists, false otherwise
     */
    public CompletableFuture<Boolean> verifyUsername(String username) {
        return getUuidByName(username).thenApply(Optional::isPresent);
    }

    // ==========================================
    // Cache Management
    // ==========================================

    /**
     * Gets the number of cached name-to-UUID entries.
     */
    public int getNameCacheSize() {
        return nameToUuidCache.size();
    }

    /**
     * Gets the number of cached UUID-to-name entries.
     */
    public int getUuidCacheSize() {
        return uuidToNameCache.size();
    }

    /**
     * Gets the remaining API requests before rate limiting.
     */
    public long getRemainingRequests() {
        return rateLimiter.getRemainingRequests("mojang-api");
    }

    /**
     * Clears all cached entries.
     */
    public void clearCache() {
        nameToUuidCache.clear();
        uuidToNameCache.clear();
        logger.info("Cache cleared");
    }

    /**
     * Shuts down the MojangAPI service.
     */
    public void shutdown() {
        cleanupExecutor.shutdown();
        try {
            if (!cleanupExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                cleanupExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            cleanupExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        rateLimiter.shutdown();
        clearCache();
        logger.info("MojangAPI shutdown complete");
    }

    // ==========================================
    // Internal Methods
    // ==========================================

    private void cleanupExpiredCache() {
        long removed = 0;

        removed += nameToUuidCache.entrySet().removeIf(e -> e.getValue().isExpired()) ? 1 : 0;
        removed += uuidToNameCache.entrySet().removeIf(e -> e.getValue().isExpired()) ? 1 : 0;

        if (removed > 0) {
            logger.debug("Cleaned up " + removed + " expired cache entries");
        }
    }

    private String httpGet(String urlString) throws IOException {
        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();

        try {
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("User-Agent", "RVNKCore/1.0");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            int responseCode = conn.getResponseCode();

            if (responseCode == 204 || responseCode == 404) {
                // No content / Not found - player doesn't exist
                return null;
            }

            if (responseCode == 429) {
                // Rate limited by Mojang
                throw new RateLimitException("mojang-api", 60);
            }

            if (responseCode != 200) {
                throw new IOException("HTTP " + responseCode);
            }

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                return response.toString();
            }
        } finally {
            conn.disconnect();
        }
    }

    // ==========================================
    // Cache Entry
    // ==========================================

    private static class CachedEntry<T> {
        private final T value;
        private final long expiryTime;

        CachedEntry(T value, long ttlMs) {
            this.value = value;
            this.expiryTime = System.currentTimeMillis() + ttlMs;
        }

        T getValue() {
            return value;
        }

        boolean isExpired() {
            return System.currentTimeMillis() > expiryTime;
        }
    }
}
