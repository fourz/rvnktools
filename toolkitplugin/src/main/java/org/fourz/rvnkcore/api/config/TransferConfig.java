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
     */
    public record Target(String host, int port, String display, String world) {
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

    /** Default themed transfer broadcast — ASCII-safe (no smart punctuation, #1753). */
    public static final String DEFAULT_BROADCAST = "&d{player} &7was whisked away across the network...";

    private TransferConfig(boolean enabled, int cooldownSeconds, String permission,
                           boolean confirm, Map<String, Target> targets, String broadcastMessage) {
        this.enabled = enabled;
        this.cooldownSeconds = cooldownSeconds > 0 ? cooldownSeconds : 10;
        this.permission = (permission != null && !permission.trim().isEmpty())
                ? permission.trim() : "rvnkcore.transfer";
        this.confirm = confirm;
        this.targets = targets != null
                ? Collections.unmodifiableMap(targets) : Collections.emptyMap();
        this.broadcastMessage = (broadcastMessage != null) ? broadcastMessage : DEFAULT_BROADCAST;
    }

    /**
     * Creates a TransferConfig from a ConfigurationSection.
     *
     * @param section The {@code transfer} configuration section (may be null)
     * @return TransferConfig instance (disabled defaults when section is null)
     */
    public static TransferConfig fromConfigurationSection(ConfigurationSection section) {
        if (section == null) {
            return new TransferConfig(false, 10, "rvnkcore.transfer", true, Collections.emptyMap(), DEFAULT_BROADCAST);
        }

        Map<String, Target> targets = new LinkedHashMap<>();
        ConfigurationSection targetsSection = section.getConfigurationSection("targets");
        if (targetsSection != null) {
            for (String name : targetsSection.getKeys(false)) {
                ConfigurationSection target = targetsSection.getConfigurationSection(name);
                if (target == null) {
                    continue;
                }
                targets.put(name, new Target(
                        target.getString("host", ""),
                        target.getInt("port", 25565),
                        target.getString("display", name),
                        target.getString("world", "")
                ));
            }
        }

        return new TransferConfig(
                section.getBoolean("enabled", false),
                section.getInt("cooldown-seconds", 10),
                section.getString("permission", "rvnkcore.transfer"),
                section.getBoolean("confirm", true),
                targets,
                section.getString("broadcast-message", DEFAULT_BROADCAST)
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
}
