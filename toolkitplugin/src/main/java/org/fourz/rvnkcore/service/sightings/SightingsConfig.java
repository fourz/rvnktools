package org.fourz.rvnkcore.service.sightings;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;

/**
 * The {@code net-sightings} config section (#2351).
 *
 * <p>Off by default. A server that already has a config.yml does not get the shipped section (#1563),
 * so add it by hand on each server that records sightings.</p>
 *
 * @param enabled        record sightings on join and on an entitlement change
 * @param server         name written into each row; blank = {@code chat-relay.server-id}
 * @param retentionDays  rows older than this are pruned (1-30)
 * @param tokenHashes    hex SHA-256 of each accepted read token; empty = the read API refuses everyone
 */
public record SightingsConfig(boolean enabled, String server, int retentionDays, List<String> tokenHashes) {

    public static SightingsConfig from(ConfigurationSection s, String fallbackServer) {
        if (s == null) return new SightingsConfig(false, fallbackServer, 7, List.of());
        List<String> hashes = new ArrayList<>(s.getStringList("read-token-sha256"));
        if (hashes.isEmpty() && s.isString("read-token-sha256")) hashes.add(s.getString("read-token-sha256"));
        String server = s.getString("server", "").trim();
        return new SightingsConfig(
                s.getBoolean("enabled", false),
                server.isEmpty() ? fallbackServer : server,
                Math.max(1, Math.min(s.getInt("retention-days", 7), 30)),
                hashes.stream().map(h -> h.trim().toLowerCase(java.util.Locale.ROOT)).filter(h -> !h.isEmpty()).toList());
    }
}
