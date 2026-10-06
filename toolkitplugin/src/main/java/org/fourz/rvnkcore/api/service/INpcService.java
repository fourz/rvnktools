package org.fourz.rvnkcore.api.service;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.fourz.rvnkcore.api.model.NpcInteraction;
import org.fourz.rvnkcore.api.model.NpcRef;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

/**
 * The RVNK NPC bridge (#2213, epic #2212).
 *
 * <p>Plugins reference NPCs by an <b>RVNK key</b>, a server-unique lower-case name matching
 * {@code [a-z0-9_-]{1,48}}. Staff attach the key to an NPC with {@code /rvnk npc tag}. The NPC
 * plugin behind the bridge (Citizens today) is an implementation detail: depend on this interface
 * and on {@link org.fourz.rvnkcore.api.event.RvnkNpcInteractEvent}, never on Citizens classes.</p>
 *
 * <p><b>Always registered.</b> When Citizens is absent or failed to enable, RVNKCore registers an
 * unavailable implementation: {@link #isAvailable()} returns false, lookups return empty, writes
 * return {@link TagResult#UNAVAILABLE}, and nothing throws.</p>
 *
 * <pre>
 * INpcService npcs = RVNKCore.getServiceSafe(INpcService.class);
 * if (npcs != null &amp;&amp; npcs.isAvailable()) {
 *     npcs.findByKey("harbour_master").ifPresent(ref -&gt; ...);
 * }
 * </pre>
 *
 * <p><b>Threading:</b> call {@link #findByKey}, {@link #listKeys}, {@link #keyOf}, {@link #tag}
 * and {@link #untag} on the main thread; the NPC registry is not thread-safe.
 * {@link #isAvailable()} and {@link #lastInteraction(UUID)} are safe from any thread.</p>
 *
 * <p>Keys are compared case-insensitively and stored lower-case.</p>
 *
 * @since 1.5.99-alpha
 */
public interface INpcService {

    /** Outcome of {@link #tag(int, String)} and {@link #untag(String)}. */
    enum TagResult {
        /** The NPC now carries the key (also returned when it already carried this exact key). */
        TAGGED,
        /** The NPC carried a different key, which the new key replaced. */
        RETAGGED,
        /** The key was removed from its NPC. */
        UNTAGGED,
        /** The key does not match {@code [a-z0-9_-]{1,48}}. */
        INVALID_KEY,
        /** Another NPC already carries this key; keys are unique per server. */
        KEY_IN_USE,
        /** No NPC has the given backing id. */
        NPC_NOT_FOUND,
        /** No NPC carries the given key. */
        KEY_NOT_FOUND,
        /** No NPC plugin is available. */
        UNAVAILABLE;

        /** @return true for TAGGED, RETAGGED and UNTAGGED */
        public boolean isSuccess() {
            return this == TAGGED || this == RETAGGED || this == UNTAGGED;
        }
    }

    /**
     * @return true when an NPC plugin (Citizens) is installed, enabled and serving the bridge
     */
    boolean isAvailable();

    /**
     * @return the NPC plugin behind the bridge, e.g. "Citizens", or "none"
     */
    default String getProviderName() {
        return isAvailable() ? "unknown" : "none";
    }

    /**
     * Finds the NPC that carries a key.
     *
     * @param key the RVNK key; case-insensitive
     * @return the NPC, or empty when the key is invalid, unused, or the bridge is unavailable
     */
    Optional<NpcRef> findByKey(String key);

    /**
     * @return every RVNK key on this server, sorted; empty when unavailable
     */
    List<String> listKeys();

    /**
     * Returns the RVNK key of an entity, when the entity is a keyed NPC.
     *
     * @param entity any entity; null is allowed
     * @return the key, or empty when the entity is not an NPC, has no key, or the bridge is unavailable
     */
    Optional<String> keyOf(Entity entity);

    /**
     * Attaches a key to an NPC. The key is stored in the NPC plugin's own save data, so it survives
     * restarts. An NPC carries at most one key: tagging an NPC that already has a different key
     * replaces it ({@link TagResult#RETAGGED}).
     *
     * @param backingId the NPC plugin's id for the NPC (the Citizens NPC id)
     * @param key       the RVNK key; case-insensitive, stored lower-case
     * @return the outcome; never null and never throws
     */
    TagResult tag(int backingId, String key);

    /**
     * Removes a key from whichever NPC carries it.
     *
     * @param key the RVNK key; case-insensitive
     * @return {@link TagResult#UNTAGGED}, {@link TagResult#KEY_NOT_FOUND},
     *         {@link TagResult#INVALID_KEY} or {@link TagResult#UNAVAILABLE}
     */
    TagResult untag(String key);

    /**
     * Returns the last keyed NPC a player interacted with since the server started. Held in a
     * bounded in-memory map, so an old entry can be evicted when many players are active.
     *
     * @param player the player's UUID
     * @return the interaction, or empty when there is none
     */
    Optional<NpcInteraction> lastInteraction(UUID player);

    /**
     * Returns the NPC that a command sender has selected in the NPC plugin (Citizens {@code /npc sel}).
     * Used by {@code /rvnk npc tag} when no NPC id is given.
     *
     * @param sender a player or the console
     * @return the selected NPC's backing id, or empty when nothing is selected or unavailable
     */
    default OptionalInt selectedBackingId(CommandSender sender) {
        return OptionalInt.empty();
    }
}
