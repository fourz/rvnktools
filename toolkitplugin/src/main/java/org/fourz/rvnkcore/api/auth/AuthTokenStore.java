package org.fourz.rvnkcore.api.auth;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.fourz.rvnkcore.util.log.LogManager;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory store for one-time authentication tokens.
 * Tokens are short-lived (15 minutes) and consumed on first use.
 *
 * <p>Registered as a service in ServiceRegistry so that both
 * {@code LinkCommand} and {@code AuthController} can access it.</p>
 *
 * @since 1.5.0
 */
public class AuthTokenStore {

    private static final long TOKEN_TTL_SECONDS = 15 * 60; // 15 minutes
    private static final long RATE_LIMIT_SECONDS = 60;     // 1 token per player per 60s
    private static final long CLEANUP_INTERVAL_TICKS = 5 * 60 * 20L; // 5 minutes

    private final ConcurrentHashMap<String, AuthToken> tokens = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Instant> consumedTokens = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Instant> lastGenerated = new ConcurrentHashMap<>();
    private final LogManager logger;

    /**
     * Authentication token data.
     */
    public record AuthToken(
            UUID playerUuid,
            String playerName,
            List<String> groups,
            Instant createdAt,
            Instant expiresAt
    ) {
        public boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }
    }

    public AuthTokenStore(Plugin plugin) {
        this.logger = LogManager.getInstance(plugin, getClass());

        // Schedule periodic cleanup
        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin,
                this::cleanup, CLEANUP_INTERVAL_TICKS, CLEANUP_INTERVAL_TICKS);
        logger.info("AuthTokenStore initialized (TTL=" + TOKEN_TTL_SECONDS + "s, cleanup every 5min)");
    }

    /**
     * Checks whether the player is rate-limited from generating a new token.
     *
     * @param player the player to check
     * @return true if the player must wait before generating another token
     */
    public boolean isRateLimited(Player player) {
        return isRateLimited(player.getUniqueId());
    }

    /**
     * Checks whether a player UUID is rate-limited from generating a new token.
     *
     * @param playerUuid the player's UUID
     * @return true if the player must wait before generating another token
     */
    public boolean isRateLimited(UUID playerUuid) {
        Instant last = lastGenerated.get(playerUuid);
        if (last == null) return false;
        return Instant.now().isBefore(last.plusSeconds(RATE_LIMIT_SECONDS));
    }

    /**
     * Generates a one-time token for the given player.
     *
     * @param player the player to generate a token for
     * @param groups the player's permission groups
     * @return the token string (UUID format)
     */
    public String generateToken(Player player, List<String> groups) {
        return generateToken(player.getUniqueId(), player.getName(), groups);
    }

    /**
     * Generates a one-time token for a player by UUID and name.
     * Supports console-initiated token generation for offline players.
     *
     * @param playerUuid the player's UUID
     * @param playerName the player's name
     * @param groups     the player's permission groups
     * @return the token string (UUID format)
     */
    public String generateToken(UUID playerUuid, String playerName, List<String> groups) {
        String token = UUID.randomUUID().toString();
        Instant now = Instant.now();

        tokens.put(token, new AuthToken(
                playerUuid,
                playerName,
                groups,
                now,
                now.plusSeconds(TOKEN_TTL_SECONDS)
        ));
        lastGenerated.put(playerUuid, now);

        logger.debug("Token generated for " + playerName + " (expires in " + TOKEN_TTL_SECONDS + "s)");
        return token;
    }

    /** Result of a token consumption attempt. */
    public enum ConsumeResult {
        /** Token was valid and consumed. Use {@link #consumeToken} return value. */
        SUCCESS,
        /** Token was already used (one-time use). */
        ALREADY_USED,
        /** Token has expired. */
        EXPIRED,
        /** Token does not exist. */
        NOT_FOUND
    }

    /** Holds the result of a consume attempt. */
    public record ConsumeOutcome(ConsumeResult result, AuthToken token) {
        public boolean isSuccess() { return result == ConsumeResult.SUCCESS; }
    }

    /**
     * Consumes (validates and removes) a token. One-time use.
     *
     * @param token the token string to consume
     * @return outcome with result code and token data (if successful)
     */
    public ConsumeOutcome consumeToken(String token) {
        // Check if this token was already consumed
        if (consumedTokens.containsKey(token)) {
            logger.debug("Token reuse attempt: " + token.substring(0, 8) + "...");
            return new ConsumeOutcome(ConsumeResult.ALREADY_USED, null);
        }

        // Check expiry before removing — ensures EXPIRED vs NOT_FOUND is reported correctly
        // even when the cleanup task races with consume.
        AuthToken authToken = tokens.get(token);
        if (authToken == null) {
            return new ConsumeOutcome(ConsumeResult.NOT_FOUND, null);
        }
        if (authToken.isExpired()) {
            tokens.remove(token); // clean up expired token
            logger.debug("Token expired for " + authToken.playerName());
            return new ConsumeOutcome(ConsumeResult.EXPIRED, null);
        }

        // Atomically remove — if another thread consumed between get() and remove(),
        // remove() returns null and we report ALREADY_USED instead of silently dropping.
        AuthToken removed = tokens.remove(token);
        if (removed == null) {
            return new ConsumeOutcome(ConsumeResult.ALREADY_USED, null);
        }

        // Track as consumed (kept until next cleanup cycle)
        consumedTokens.put(token, Instant.now());
        logger.debug("Token consumed for " + authToken.playerName());
        return new ConsumeOutcome(ConsumeResult.SUCCESS, authToken);
    }

    /**
     * Removes all expired tokens and stale rate-limit entries.
     */
    public void cleanup() {
        int removed = 0;
        for (var entry : tokens.entrySet()) {
            if (entry.getValue().isExpired()) {
                tokens.remove(entry.getKey());
                removed++;
            }
        }
        // Clean up stale rate-limit entries (older than RATE_LIMIT_SECONDS)
        Instant cutoff = Instant.now().minusSeconds(RATE_LIMIT_SECONDS);
        lastGenerated.entrySet().removeIf(e -> e.getValue().isBefore(cutoff));

        // Clean up consumed token tracking (keep for TOKEN_TTL_SECONDS then discard)
        Instant consumedCutoff = Instant.now().minusSeconds(TOKEN_TTL_SECONDS);
        consumedTokens.entrySet().removeIf(e -> e.getValue().isBefore(consumedCutoff));

        if (removed > 0) {
            logger.debug("AuthTokenStore cleanup: removed " + removed + " expired token(s)");
        }
    }
}
