package org.fourz.rvnkcore.api.dungeon;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Resettable dungeons (#2367, contract #2368). RVNKWorlds provides it; quests and other plugins use
 * it through the ServiceRegistry and the events in {@code org.fourz.rvnkcore.api.event}
 * ({@code DungeonEnterEvent}, {@code DungeonLeaveEvent}, {@code DungeonClearedEvent},
 * {@code DungeonResetEvent}).
 *
 * <p>A dungeon is a box in one world. Its saved state, mob roster, loot table and reset policy are
 * the provider's business. A reset moves players inside to the exit, puts the blocks back, re-tags
 * containers with the loot table and spawns the roster.</p>
 *
 * <p><b>Resolve with {@link Dungeons#resolve()}</b>, which never returns null: without RVNKWorlds it
 * gives an unavailable implementation whose lookups are empty and whose resets answer
 * {@link ResetResult.Status#UNAVAILABLE}.</p>
 *
 * <p><b>Threading:</b> lookups are safe from any thread. {@link #reset} may be called from any
 * thread; the provider does the world work on the main thread and completes the future after.</p>
 *
 * @since 1.5.112-alpha
 */
public interface IDungeonService {

    /** @return true when a provider is serving dungeons */
    boolean isAvailable();

    /** @return the dungeon with that id (case-insensitive) */
    Optional<DungeonInfo> find(String id);

    /** @return every dungeon on this server */
    List<DungeonInfo> list();

    /** @return the dungeon whose box holds that block, if any */
    Optional<DungeonInfo> dungeonAt(String world, int x, int y, int z);

    /** @return the dungeon's state; {@link DungeonState#UNKNOWN} for no such dungeon */
    DungeonState state(String id);

    /** @return roster mobs still alive; 0 when cleared or unknown */
    int remainingRoster(String id);

    /** @return players who entered since the last reset (they share the clear) */
    Set<UUID> participants(String id);

    /**
     * Resets the dungeon. Players inside are moved to its exit first.
     *
     * @param actor who asked, for logs (a player name, {@code quest:<id>}, {@code console})
     */
    CompletableFuture<ResetResult> reset(String id, ResetReason reason, String actor);
}
