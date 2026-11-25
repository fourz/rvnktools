package org.fourz.rvnkcore.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.fourz.rvnkcore.api.dungeon.DungeonInfo;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Fired when the last mob of a dungeon's roster dies (#2367, contract #2368). Everyone who entered
 * since the last reset shares the clear: a quest's clear objective completes for each participant.
 *
 * <p><b>Thread:</b> fired synchronously on the server main thread.</p>
 *
 * @since 1.5.112-alpha
 */
public class DungeonClearedEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final DungeonInfo dungeon;
    private final Set<UUID> participants;
    private final Player killer;

    /**
     * @param participants players who entered since the last reset
     * @param killer       the player who killed the last mob; null when it died otherwise
     */
    public DungeonClearedEvent(DungeonInfo dungeon, Set<UUID> participants, Player killer) {
        super(false);
        this.dungeon = Objects.requireNonNull(dungeon, "dungeon");
        this.participants = Set.copyOf(participants == null ? Set.of() : participants);
        this.killer = killer;
    }

    public DungeonInfo getDungeon() {
        return dungeon;
    }

    public String getDungeonId() {
        return dungeon.id();
    }

    /** @return players who entered since the last reset; never null */
    public Set<UUID> getParticipants() {
        return participants;
    }

    /** @return the player who killed the last roster mob, or null */
    public Player getKiller() {
        return killer;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
