package org.fourz.rvnkcore.api.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.fourz.rvnkcore.api.dungeon.DungeonInfo;
import org.fourz.rvnkcore.api.dungeon.ResetReason;
import org.fourz.rvnkcore.api.dungeon.ResetResult;

import java.util.Objects;

/**
 * Fired after a dungeon reset finished (#2367, contract #2368). Participants are cleared; the roster
 * is fresh.
 *
 * <p><b>Thread:</b> fired synchronously on the server main thread.</p>
 *
 * @since 1.5.112-alpha
 */
public class DungeonResetEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final DungeonInfo dungeon;
    private final ResetReason reason;
    private final String actor;
    private final ResetResult result;

    public DungeonResetEvent(DungeonInfo dungeon, ResetReason reason, String actor, ResetResult result) {
        super(false);
        this.dungeon = Objects.requireNonNull(dungeon, "dungeon");
        this.reason = Objects.requireNonNull(reason, "reason");
        this.actor = actor == null ? "" : actor;
        this.result = Objects.requireNonNull(result, "result");
    }

    public DungeonInfo getDungeon() {
        return dungeon;
    }

    public String getDungeonId() {
        return dungeon.id();
    }

    public ResetReason getReason() {
        return reason;
    }

    /** @return who asked: a player name, {@code quest:<id>}, {@code console} or {@code cooldown} */
    public String getActor() {
        return actor;
    }

    public ResetResult getResult() {
        return result;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
