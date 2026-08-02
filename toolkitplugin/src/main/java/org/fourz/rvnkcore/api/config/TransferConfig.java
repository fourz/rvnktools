package org.fourz.rvnkcore.api.config;

import org.bukkit.configuration.ConfigurationSection;
import org.fourz.rvnkcore.util.log.LogManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Configuration for native cross-server player transfer.
 *
 * <p>Mirrors {@link WebhookConfig} / {@link ChatRelayConfig}'s
 * {@code fromConfigurationSection} + {@code validate} style. Backs the
 * {@code /server transfer &lt;target&gt;} command: a small, static directory of
 * named destinations (e.g. {@code event}, {@code prod}) that the server may send a
 * player to via Minecraft's native Transfer packet ({@code Player#transfer(host, port)}).</p>
 *
 * <p><b>MVP scope: movement only.</b> No inventory/economy/quest state travels with the
 * player — they arrive on the destination with that server's own data for their account.</p>
 *
 * @since 1.5.23
 */
public class TransferConfig {

    /**
     * A single transfer destination: a name mapped to a public {@code host:port}, plus friendly
     * display metadata for portal signs.
     *
     * @param host    The destination server host (public address the client reconnects to)
     * @param port    The destination server port
     * @param display Friendly server name for signs/messages (e.g. {@code "Nations"}); defaults to the target name
     * @param world   Friendly destination world/realm name for signs (informational; may be empty)
     * @param realm   In-fiction name of the destination for crossing messages (e.g. {@code "the Arcology"})
     */
    public record Target(String host, int port, String display, String world, String realm) {
    }

    /**
     * In-fiction realm names, keyed by target name.
     *
     * <p>Players do not cross to "prod" or "event" — they cross to the home realm and the Arcology.
     * {@code display} cannot carry this: it is the operator-facing server name and is also stamped
     * onto portal signs, so overloading it would change those too.</p>
     *
     * <p>Defaulted in code rather than shipped in {@code config.yml} on purpose. Dev, Event and
     * production all already have that file, so a new key added to the packaged resource would
     * reach none of them ({@code saveResource(..., false)} only writes when the file is absent).
     * A code default applies immediately everywhere and is still overridable per target with a
     * {@code realm:} key.</p>
     */
    private static final Map<String, String> DEFAULT_REALMS = Map.of(
            "prod", "the home realm",
            "event", "the Arcology",
            "dev", "the fragile worlds",
            "test", "the fragile worlds");

    /** Neutral stand-in when a crossing's destination cannot be resolved. */
    public static final String UNKNOWN_REALM = "another realm";

    /**
     * Resolves the in-fiction realm name for a target.
     *
     * @param targetName The configured target key (case-insensitive)
     * @param configured The explicit {@code realm:} value, or null/blank when unset
     * @param display    The target's display name, used as the last resort
     * @return A non-blank realm name
     */
    // Package-private rather than private so the realm table can be tested directly. Building a
    // Bukkit ConfigurationSection off-server is not practical, so the alternative was a test that
    // re-implemented this switch and therefore verified nothing.
    static String resolveRealm(String targetName, String configured, String display) {
        if (configured != null && !configured.isBlank()) {
            return configured;
        }
        String byName = DEFAULT_REALMS.get(targetName.toLowerCase(java.util.Locale.ROOT));
        if (byName != null) {
            return byName;
        }
        return (display != null && !display.isBlank()) ? display : UNKNOWN_REALM;
    }

    private final boolean enabled;
    private final int cooldownSeconds;
    private final String permission;
    private final boolean confirm;
    /** Case-preserving map of target name to destination (insertion ordered). */
    private final Map<String, Target> targets;
    /**
     * Themed broadcast shown on the source server when a player transfers (#1763), with {@code {player}}
     * substituted. Blank suppresses the broadcast (the vanilla quit message is still hidden either way).
     */
    private final String broadcastMessage;
    /**
     * Themed broadcast shown on the DESTINATION when a transferred player arrives (#1782), with
     * {@code {player}} substituted. Blank suppresses the announcement — the vanilla "joined the game"
     * message is hidden either way, since a transfer is a crossing rather than a login.
     */
    private final String arrivalMessage;

    /**
     * Default themed transfer broadcast — ASCII-safe (no smart punctuation, #1753).
     *
     * <p>Names the destination. The previous copy ("was whisked away across the network...") told
     * nobody where the player went, and "the network" is infrastructure vocabulary for something
     * the fiction treats as travel between realms.</p>
     */
    public static final String DEFAULT_BROADCAST = "&d{player} &7has crossed to &f{realm}";
    /** Default themed arrival broadcast — ASCII-safe (#1753). */
    public static final String DEFAULT_ARRIVAL = "&d{player} &7steps through from across the network...";

    private TransferConfig(boolean enabled, int cooldownSeconds, String permission,
                           boolean confirm, Map<String, Target> targets, String broadcastMessage,
                           String arrivalMessage) {
        this.enabled = enabled;
        this.cooldownSeconds = cooldownSeconds > 0 ? cooldownSeconds : 10;
        this.permission = (permission != null && !permission.trim().isEmpty())
                ? permission.trim() : "rvnkcore.transfer";
        this.confirm = confirm;
        this.targets = targets != null
                ? Collections.unmodifiableMap(targets) : Collections.emptyMap();
        this.broadcastMessage = (broadcastMessage != null) ? broadcastMessage : DEFAULT_BROADCAST;
        this.arrivalMessage = (arrivalMessage != null) ? arrivalMessage : DEFAULT_ARRIVAL;
    }

    /**
     * Creates a TransferConfig from a ConfigurationSection.
     *
     * @param section The {@code transfer} configuration section (may be null)
     * @return TransferConfig instance (disabled defaults when section is null)
     */
    public static TransferConfig fromConfigurationSection(ConfigurationSection section) {
        if (section == null) {
            return new TransferConfig(false, 10, "rvnkcore.transfer", true, Collections.emptyMap(),
                    DEFAULT_BROADCAST, DEFAULT_ARRIVAL);
        }

        Map<String, Target> targets = new LinkedHashMap<>();
        ConfigurationSection targetsSection = section.getConfigurationSection("targets");
        if (targetsSection != null) {
            for (String name : targetsSection.getKeys(false)) {
                ConfigurationSection target = targetsSection.getConfigurationSection(name);
                if (target == null) {
                    continue;
                }
                String display = target.getString("display", name);
                targets.put(name, new Target(
                        target.getString("host", ""),
                        target.getInt("port", 25565),
                        display,
                        target.getString("world", ""),
                        resolveRealm(name, target.getString("realm", null), display)
                ));
            }
        }

        return new TransferConfig(
                section.getBoolean("enabled", false),
                section.getInt("cooldown-seconds", 10),
                section.getString("permission", "rvnkcore.transfer"),
                section.getBoolean("confirm", true),
                targets,
                section.getString("broadcast-message", DEFAULT_BROADCAST),
                section.getString("arrival-message", DEFAULT_ARRIVAL)
        );
    }

    /**
     * Validates the transfer configuration.
     *
     * @param logger Logger for reporting validation issues
     * @return true if valid (or disabled), false otherwise
     */
    public boolean validate(LogManager logger) {
        if (!enabled) {
            return true;
        }
        boolean valid = true;
        if (targets.isEmpty()) {
            logger.warning("Transfer enabled but no targets configured — nothing can be transferred to");
        }
        for (Map.Entry<String, Target> entry : targets.entrySet()) {
            String name = entry.getKey();
            Target target = entry.getValue();
            if (target.host() == null || target.host().trim().isEmpty()) {
                logger.error("Transfer target '" + name + "' has an empty host");
                valid = false;
            }
            if (target.port() <= 0 || target.port() > 65535) {
                logger.error("Transfer target '" + name + "' has an invalid port: " + target.port());
                valid = false;
            }
        }
        return valid;
    }

    /**
     * Resolves a target by name, case-insensitively.
     *
     * @param name The target name (e.g. {@code "prod"})
     * @return The matching {@link Target}, or null when no target matches
     */
    public Target resolveTarget(String name) {
        if (name == null) {
            return null;
        }
        for (Map.Entry<String, Target> entry : targets.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(name)) {
                return entry.getValue();
            }
        }
        return null;
    }

    /**
     * Returns the configured target names (for tab-completion and messaging).
     *
     * @return An unmodifiable list of target names in configuration order
     */
    public List<String> getTargetNames() {
        return Collections.unmodifiableList(new ArrayList<>(targets.keySet()));
    }

    public boolean isEnabled() { return enabled; }
    public int getCooldownSeconds() { return cooldownSeconds; }
    public String getPermission() { return permission; }
    public boolean isConfirmRequired() { return confirm; }
    public Map<String, Target> getTargets() { return targets; }
    public String getBroadcastMessage() { return broadcastMessage; }
    public String getArrivalMessage() { return arrivalMessage; }
}
