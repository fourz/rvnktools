package org.fourz.rvnkcore.service.netpeers;

import org.bukkit.configuration.ConfigurationSection;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The {@code net-peers} config section (#2351).
 *
 * <p>Off unless {@code enabled} is true and both {@code url} and {@code secret} are set. The shipped
 * {@code config.yml} carries the section, but a server that already has a config.yml does not get it
 * (#1563): add the section by hand on the server that reports.</p>
 *
 * @param enabled         master switch
 * @param url             z-office login-event endpoint
 * @param secret          sent as {@code X-Net-Peers-Event-Secret}; never logged
 * @param timeoutMs       connect and request timeout for one attempt
 * @param caCert          PEM file of the CA that signed z-office's certificate; blank = JVM truststore.
 *                        A relative path is resolved against the RVNKCore data folder.
 * @param ignoreAddresses public addresses that belong to a proxy, not a player; a join from one sends nothing
 */
public record NetPeersConfig(boolean enabled, String url, String secret, int timeoutMs,
                             String caCert, Set<String> ignoreAddresses) {

    public static final String DEFAULT_URL = "https://z-office.internal.fourz.org/api/net/peers/login-event";
    public static final int DEFAULT_TIMEOUT_MS = 4000;

    public static NetPeersConfig disabled() {
        return new NetPeersConfig(false, DEFAULT_URL, "", DEFAULT_TIMEOUT_MS, "", Set.of());
    }

    public static NetPeersConfig from(ConfigurationSection s) {
        if (s == null) return disabled();
        List<String> ignore = s.getStringList("ignore-addresses");
        int timeout = s.getInt("timeout-ms", DEFAULT_TIMEOUT_MS);
        return new NetPeersConfig(
                s.getBoolean("enabled", false),
                s.getString("url", DEFAULT_URL).trim(),
                s.getString("secret", "").trim(),
                Math.max(500, Math.min(timeout, 10_000)),
                s.getString("ca-cert", "").trim(),
                ignore.stream().map(String::trim).filter(a -> !a.isEmpty()).collect(Collectors.toUnmodifiableSet()));
    }

    /** True when the reporter should send. */
    public boolean active() {
        return enabled && !url.isBlank() && !secret.isBlank();
    }
}
