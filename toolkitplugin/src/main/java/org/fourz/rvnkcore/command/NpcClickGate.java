package org.fourz.rvnkcore.command;

import java.util.Locale;
import java.util.Set;

/**
 * The gate for {@code /rvnk npc click} (#2255): a pure function, unit-testable without a server.
 *
 * <p>A simulated click advances quests and plays dialogue for a real player, so it must not be a
 * way to push quest state on a tier with players in it. The rule (workshop 2026-10-09, #2251):</p>
 *
 * <ol>
 *   <li>The sender needs {@code rvnkcore.npc.admin}.</li>
 *   <li>The tier must be known. An unset or unreadable server id refuses, whatever the
 *       permissions: "I do not know which tier this is" reads as the cautious answer.</li>
 *   <li>On the Dev tier ({@code dev}, or {@code test}) any online player may be the target.</li>
 *   <li>On every other tier ({@code event}, {@code nations}, anything else) the TARGET player must
 *       hold {@code rvnkcore.qa.subject}, which a LuckPerms QA group grants. The sender's own
 *       permissions never stand in for the target's.</li>
 * </ol>
 *
 * <p><b>Tier source.</b> The tier is RVNKCore's {@code ConfigLoader.getServerId()}:
 * {@code chat-relay.server-id}, then {@code webhook.server-id}, else {@code "local"}. That is the
 * same value RVNKQuests' {@code ServerTier} reads for its {@code quest debug} gates. RVNK Dev's id is
 * {@code dev} (its chat room is {@code test}); Event is {@code event}; prod is {@code nations}.
 * {@code "local"} is the not-configured fallback, so it counts as unknown.</p>
 *
 * @since 1.5.102-alpha
 */
public final class NpcClickGate {

    /** Permission a target player needs on a non-Dev tier; default false in plugin.yml. */
    public static final String PERM_QA_SUBJECT = "rvnkcore.qa.subject";

    /** Server ids that count as the development tier. */
    public static final Set<String> DEV_TIERS = Set.of("dev", "test");

    /** {@code ConfigLoader.getServerId()} returns this when no server id is configured. */
    public static final String UNSET_TIER = "local";

    private NpcClickGate() {
    }

    /**
     * @param allowed true when the click may run
     * @param reason  why, for the sender and the audit log; never null
     */
    public record Decision(boolean allowed, String reason) {
    }

    /**
     * Decides whether a simulated click may run.
     *
     * @param tier              this server's id, or null when it could not be read
     * @param senderIsAdmin     whether the sender has {@code rvnkcore.npc.admin}
     * @param targetIsQaSubject whether the target player has {@link #PERM_QA_SUBJECT}
     * @return the decision; never null
     */
    public static Decision evaluate(String tier, boolean senderIsAdmin, boolean targetIsQaSubject) {
        if (!senderIsAdmin) {
            return new Decision(false, "the sender lacks " + NpcAdminVerbs.PERM_ADMIN);
        }
        String normalized = normalize(tier);
        if (normalized == null) {
            return new Decision(false, "this server's tier is unknown (server-id is "
                    + (tier == null ? "unreadable" : "'" + tier.trim() + "'")
                    + "); set chat-relay.server-id or webhook.server-id in RVNKCore config.yml");
        }
        if (DEV_TIERS.contains(normalized)) {
            return new Decision(true, "tier '" + normalized + "' is Dev");
        }
        if (targetIsQaSubject) {
            return new Decision(true, "tier '" + normalized + "', target has " + PERM_QA_SUBJECT);
        }
        return new Decision(false, "tier '" + normalized + "' is not Dev and the target player lacks "
                + PERM_QA_SUBJECT + " (add them to the QA group: lp user <player> parent add qa)");
    }

    /** @return true when the tier is a Dev tier */
    public static boolean isDevTier(String tier) {
        String normalized = normalize(tier);
        return normalized != null && DEV_TIERS.contains(normalized);
    }

    /** @return the lower-case trimmed tier, or null when it is null, blank or the unset fallback */
    static String normalize(String tier) {
        if (tier == null) {
            return null;
        }
        String normalized = tier.trim().toLowerCase(Locale.ROOT);
        return normalized.isEmpty() || normalized.equals(UNSET_TIER) ? null : normalized;
    }
}
