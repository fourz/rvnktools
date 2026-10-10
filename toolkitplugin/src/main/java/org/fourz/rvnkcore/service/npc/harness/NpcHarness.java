package org.fourz.rvnkcore.service.npc.harness;

import org.bukkit.Location;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Write side of the NPC bridge for staff tooling (#2248): place and edit NPCs by RVNK key, with no
 * sender position and no dispatched NPC-plugin command. Citizens implements it in
 * {@code service.npc.citizens}; this interface names no Citizens type, so the spec planner,
 * executor and tests run without Citizens.
 *
 * <p>Registered in the ServiceRegistry only when Citizens is available. Main thread only. Every
 * method returns a {@link Result} and never throws for an expected failure.</p>
 *
 * @since 1.5.100-alpha
 */
public interface NpcHarness {

    /** Outcome of one write. */
    record Result(boolean ok, String message) {
        public static Result ok(String message) {
            return new Result(true, message);
        }

        public static Result fail(String message) {
            return new Result(false, message);
        }
    }

    /** @return every NPC that carries a key, including NPCs that share a key */
    List<NpcState> snapshot();

    /** @return the NPC that carries the key, or empty (also when two NPCs carry it) */
    default Optional<NpcState> state(String key) {
        NpcState found = null;
        for (NpcState state : snapshot()) {
            if (state.key().equals(key)) {
                if (found != null) {
                    return Optional.empty();
                }
                found = state;
            }
        }
        return Optional.ofNullable(found);
    }

    /**
     * Block reads for the standable-Y snap ({@link NpcGround}). Callers snap a target location with
     * {@link NpcGround#snap} before {@link #create} and {@link #move}; the harness itself places the
     * NPC exactly where it is told.
     *
     * @return the terrain, or null when the implementation cannot read blocks
     * @since 1.5.101-alpha
     */
    default NpcGround.Terrain terrain() {
        return null;
    }

    /** Creates a player NPC at an explicit location and tags it; fails when the key is in use. */
    Result create(String key, String name, Location at);

    /** Moves (teleports or respawns) the NPC; works across worlds. */
    Result move(String key, Location to);

    Result rename(String key, String name);

    /** Destroys the NPC, which also removes its key. */
    Result remove(String key);

    /**
     * Sets the skin from a player name or a URL. The fetch is asynchronous: the returned result
     * only says the request started; {@code later} receives the final success or failure line on
     * the main thread.
     */
    Result skin(String key, String source, Consumer<String> later);

    Result lookClose(String key, boolean on);

    Result pose(String key, NpcPose pose);

    /** Sets the main-hand item; "none" empties the hand. */
    Result hold(String key, String material);

    /** Citizens "protected": a protected NPC cannot be damaged. */
    Result setProtected(String key, boolean on);

    Result nameplate(String key, NpcNameplate mode);
}
