package org.fourz.rvnkcore.service.whitelist;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Name to UUID: an online player first (main thread), else the Mojang profile API.
 *
 * <p>The servers run {@code online-mode=true}, so the Mojang UUID is the UUID the whitelist holds.
 * {@code Bukkit.getOfflinePlayer(String)} is not used: it does a blocking web lookup on the calling
 * thread, and the old controller ran it on the main thread.</p>
 */
public class MojangProfileLookup implements WhitelistMembership.ProfileLookup {

    static final String PROFILE_URL = "https://api.mojang.com/users/profiles/minecraft/";
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private final Plugin plugin;
    private final HttpClient http;

    public MojangProfileLookup(Plugin plugin) {
        this.plugin = plugin;
        this.http = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
    }

    @Override
    public CompletableFuture<Optional<WhitelistMembership.Profile>> byName(String ign) {
        return onlinePlayer(ign).thenCompose(online -> online.isPresent()
            ? CompletableFuture.completedFuture(online)
            : mojang(ign));
    }

    private CompletableFuture<Optional<WhitelistMembership.Profile>> onlinePlayer(String ign) {
        CompletableFuture<Optional<WhitelistMembership.Profile>> f = new CompletableFuture<>();
        Bukkit.getScheduler().runTask(plugin, () -> {
            Player p = Bukkit.getPlayerExact(ign);
            f.complete(p == null ? Optional.empty()
                : Optional.of(new WhitelistMembership.Profile(p.getUniqueId(), p.getName())));
        });
        return f;
    }

    private CompletableFuture<Optional<WhitelistMembership.Profile>> mojang(String ign) {
        HttpRequest req = HttpRequest.newBuilder(URI.create(PROFILE_URL + ign))
            .timeout(TIMEOUT)
            .GET()
            .build();
        return http.sendAsync(req, HttpResponse.BodyHandlers.ofString())
            .thenApply(resp -> parse(resp.statusCode(), resp.body(),
                resp.headers().firstValue("Retry-After").orElse(null)))
            .exceptionallyCompose(ex -> CompletableFuture.failedFuture(
                toLookupFailure(ign, ex.getCause() != null ? ex.getCause() : ex)));
    }

    /**
     * Wraps a failed Mojang call for the REST layer (#2448): an HTTP 429 becomes a
     * {@link WhitelistMembership.LookupRateLimitedException} carrying the retry delay, anything else a
     * plain {@link WhitelistMembership.LookupFailedException}.
     */
    static WhitelistMembership.LookupFailedException toLookupFailure(String ign, Throwable cause) {
        if (cause instanceof MojangHttpStatusException status && status.status() == 429) {
            return new WhitelistMembership.LookupRateLimitedException(
                "Mojang profile lookup rate-limited for " + ign, status.retryAfterSeconds(), cause);
        }
        return new WhitelistMembership.LookupFailedException("Mojang profile lookup failed for " + ign, cause);
    }

    /** A non-200, non-404 answer from the Mojang profile API, with the status kept typed (#2448). */
    static final class MojangHttpStatusException extends IllegalStateException {
        private final int status;
        private final long retryAfterSeconds;

        MojangHttpStatusException(int status, long retryAfterSeconds) {
            super("Mojang profile lookup returned HTTP " + status);
            this.status = status;
            this.retryAfterSeconds = retryAfterSeconds;
        }

        int status() {
            return status;
        }

        /** The upstream {@code Retry-After} in seconds, or -1 when absent or not a number of seconds. */
        long retryAfterSeconds() {
            return retryAfterSeconds;
        }
    }

    /** Reads a {@code Retry-After} header given in seconds; an HTTP date or junk gives -1. */
    static long retryAfterSeconds(String header) {
        if (header == null) return -1;
        try {
            long s = Long.parseLong(header.trim());
            return s > 0 ? s : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    static Optional<WhitelistMembership.Profile> parse(int status, String body) {
        return parse(status, body, null);
    }

    /**
     * Reads a Mojang profile response. 404 and 204 mean "no such account"; any other non-200 fails,
     * so a Mojang outage never reads as "player does not exist".
     *
     * @param retryAfter the response's {@code Retry-After} header, or null
     */
    static Optional<WhitelistMembership.Profile> parse(int status, String body, String retryAfter) {
        if (status == 404 || status == 204) return Optional.empty();
        if (status != 200) throw new MojangHttpStatusException(status, retryAfterSeconds(retryAfter));
        JsonObject o = JsonParser.parseString(body).getAsJsonObject();
        if (!o.has("id")) return Optional.empty();
        return Optional.of(new WhitelistMembership.Profile(undashed(o.get("id").getAsString()),
            o.has("name") ? o.get("name").getAsString() : null));
    }

    /** Mojang returns UUIDs without dashes. */
    static UUID undashed(String hex) {
        if (hex.length() != 32) throw new IllegalArgumentException("Not an undashed UUID: " + hex);
        return UUID.fromString(hex.substring(0, 8) + "-" + hex.substring(8, 12) + "-" + hex.substring(12, 16)
            + "-" + hex.substring(16, 20) + "-" + hex.substring(20));
    }
}
