package org.fourz.rvnkcore.api.config;

import org.bukkit.configuration.ConfigurationSection;
import org.fourz.rvnkcore.util.log.LogManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Configuration for the cross-server chat relay.
 *
 * <p>Mirrors {@link WebhookConfig}'s {@code fromConfigurationSection} + {@code validate} style.
 * Peers are authenticated with the destination server's {@code X-API-Key} (reusing the existing
 * AuthFilter on {@code /v1/*}), so each peer carries its own {@code apiKey} rather than an HMAC
 * secret.</p>
 *
 * @since 1.5.22
 */
public class ChatRelayConfig {

    /**
     * A single relay peer: another RVNKCore server that shares the global channel.
     *
     * @param id     Peer server-id (matches the peer's {@code chat-relay.server-id})
     * @param tag    Short display tag rendered next to relayed messages (e.g. {@code "P"})
     * @param url    Full inbound URL of the peer, e.g. {@code https://host:port/v1/chat/inbound}
     * @param apiKey The peer's {@code X-API-Key}, sent as the {@code X-API-Key} egress header
     */
    public record Peer(String id, String tag, String url, String apiKey) {
    }

    /**
     * Default render for {@code BOT}-room broadcast lines (#1769). ASCII-safe (no smart punctuation,
     * #1753). Placeholders: {@code {label}} (bracket tag), {@code {sender}} (persona), {@code {message}}.
     */
    public static final String DEFAULT_BOT_FORMAT = "&8[&d{label}&8]&r &d{sender}&7: &f{message}";

    private final boolean enabled;
    private final String serverId;
    private final String serverLabel;
    private final String channelTrigger;
    /** Default retained chat-tail size; ChatMessageBuffer clamps the final value. */
    private static final int DEFAULT_BUFFER_SIZE = 500;

    private final int dedupCacheSize;
    private final int bufferSize;
    private final int timeoutMs;
    private final boolean insecureTls;
    private final String botFormat;
    private final List<Peer> peers;

    private ChatRelayConfig(boolean enabled, String serverId, String serverLabel, String channelTrigger,
                            int dedupCacheSize, int bufferSize, int timeoutMs, boolean insecureTls,
                            String botFormat, List<Peer> peers) {
        this.enabled = enabled;
        this.serverId = serverId != null ? serverId.trim() : "";
        // Friendly label for THIS server (e.g. "nations", "event"); stamped on outgoing chatroom
        // messages and used for local rendering. Defaults to the server-id when unset.
        this.serverLabel = (serverLabel != null && !serverLabel.trim().isEmpty())
                ? serverLabel.trim() : this.serverId;
        this.channelTrigger = (channelTrigger != null && !channelTrigger.isEmpty()) ? channelTrigger : "!";
        this.dedupCacheSize = dedupCacheSize > 0 ? dedupCacheSize : 512;
        // Retained chat tail for GET /v1/chat/recent (#1869). Clamped again by ChatMessageBuffer.
        this.bufferSize = bufferSize > 0 ? bufferSize : DEFAULT_BUFFER_SIZE;
        this.timeoutMs = timeoutMs > 0 ? timeoutMs : 3000;
        this.insecureTls = insecureTls;
        this.botFormat = (botFormat != null && !botFormat.isEmpty()) ? botFormat : DEFAULT_BOT_FORMAT;
        this.peers = peers != null ? Collections.unmodifiableList(peers) : Collections.emptyList();
    }

    /**
     * Creates a ChatRelayConfig from a ConfigurationSection.
     *
     * @param section The {@code chat-relay} configuration section (may be null)
     * @return ChatRelayConfig instance (disabled defaults when section is null)
     */
    public static ChatRelayConfig fromConfigurationSection(ConfigurationSection section) {
        if (section == null) {
            return new ChatRelayConfig(false, "", "", "!", 512, DEFAULT_BUFFER_SIZE, 3000,
                    false, DEFAULT_BOT_FORMAT, Collections.emptyList());
        }

        List<Peer> peers = new ArrayList<>();
        ConfigurationSection peersSection = section.getConfigurationSection("peers");
        if (peersSection != null) {
            // Map-style peers: peers.<key>.{id,tag,url,api-key}
            for (String key : peersSection.getKeys(false)) {
                ConfigurationSection peer = peersSection.getConfigurationSection(key);
                if (peer == null) continue;
                peers.add(new Peer(
                    peer.getString("id", key),
                    peer.getString("tag", ""),
                    peer.getString("url", ""),
                    peer.getString("api-key", "")
                ));
            }
        } else {
            // List-style peers: a YAML list of maps under peers:
            List<?> rawList = section.getList("peers");
            if (rawList != null) {
                for (Object raw : rawList) {
                    if (raw instanceof java.util.Map<?, ?> map) {
                        peers.add(new Peer(
                            asString(map.get("id")),
                            asString(map.get("tag")),
                            asString(map.get("url")),
                            asString(map.get("api-key"))
                        ));
                    }
                }
            }
        }

        return new ChatRelayConfig(
            section.getBoolean("enabled", false),
            section.getString("server-id", ""),
            section.getString("server-label", ""),
            section.getString("channel-trigger", "!"),
            section.getInt("dedup-cache-size", 512),
            section.getInt("buffer.size", DEFAULT_BUFFER_SIZE),
            section.getInt("timeout-ms", 3000),
            section.getBoolean("insecure-tls", false),
            section.getString("bot-format", DEFAULT_BOT_FORMAT),
            peers
        );
    }

    private static String asString(Object value) {
        return value != null ? value.toString() : "";
    }

    /**
     * Validates the chat relay configuration.
     *
     * @param logger Logger for reporting validation issues
     * @return true if valid (or disabled), false otherwise
     */
    public boolean validate(LogManager logger) {
        if (!enabled) {
            return true;
        }
        boolean valid = true;
        if (serverId.isEmpty()) {
            logger.error("Chat relay enabled but server-id is empty");
            valid = false;
        }
        if (peers.isEmpty()) {
            logger.warning("Chat relay enabled but no peers configured - nothing will be relayed");
        }
        if (insecureTls) {
            logger.warning("Chat relay insecure-tls is ON - peer TLS certificates are NOT verified "
                + "(self-signed peers accepted). Only use on a trusted server-to-server network; the "
                + "egress X-API-Key would be exposed to a man-in-the-middle.");
        }
        for (Peer peer : peers) {
            if (peer.url() == null || peer.url().trim().isEmpty()) {
                logger.error("Chat relay peer '" + peer.id() + "' has an empty url");
                valid = false;
            } else if (!peer.url().startsWith("https://") && !peer.url().startsWith("http://")) {
                logger.error("Chat relay peer '" + peer.id() + "' url must start with http:// or https:// — got: " + peer.url());
                valid = false;
            } else if (peer.url().startsWith("http://")) {
                logger.warning("Chat relay peer '" + peer.id() + "' uses insecure http:// — ensure this is a trusted network");
            }
            if (peer.apiKey() == null || peer.apiKey().trim().isEmpty()) {
                logger.error("Chat relay peer '" + peer.id() + "' has an empty api-key");
                valid = false;
            }
            if (serverId.equals(peer.id())) {
                logger.error("Chat relay peer '" + peer.id() + "' has the same id as this server - would loop");
                valid = false;
            }
        }
        return valid;
    }

    /**
     * Resolves a peer's display tag by origin server-id.
     *
     * @param originServerId The origin server-id from an inbound message
     * @return The peer's configured tag, or the origin id itself when no peer matches
     */
    public String resolvePeerTag(String originServerId) {
        if (originServerId == null) return "?";
        for (Peer peer : peers) {
            if (originServerId.equals(peer.id()) && peer.tag() != null && !peer.tag().isEmpty()) {
                return peer.tag();
            }
        }
        return originServerId;
    }

    public boolean isEnabled() { return enabled; }
    public String getServerId() { return serverId; }
    public String getServerLabel() { return serverLabel; }
    public String getChannelTrigger() { return channelTrigger; }
    public int getDedupCacheSize() { return dedupCacheSize; }
    public int getBufferSize() { return bufferSize; }
    public int getTimeoutMs() { return timeoutMs; }
    public boolean isInsecureTls() { return insecureTls; }
    public String getBotFormat() { return botFormat; }
    public List<Peer> getPeers() { return peers; }
}
