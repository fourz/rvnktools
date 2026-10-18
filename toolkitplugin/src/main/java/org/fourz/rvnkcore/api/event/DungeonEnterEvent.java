package org.fourz.rvnkcore.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.fourz.rvnkcore.api.dungeon.DungeonInfo;

import java.util.Objects;

/**
 * Fired when a player moves into a dungeon's box (#2367, contract #2368).
 *
 * <p><b>Thread:</b> fired synchronously on the server main thread by the dungeon provider
 * (RVNKWorlds), once per crossing of the dungeon's box edge.</p>
 *
 * @since 1.5.112-alpha
 */
public class DungeonEnterEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final DungeonInfo dungeon;

    public DungeonEnterEvent(Player player, DungeonInfo dungeon) {
        super(false);
        this.player = Objects.requireNonNull(player, "player");
        this.dungeon = Objects.requireNonNull(dungeon, "dungeon");
    }

    /** @return the player who entered */
    public Player getPlayer() {
        return player;
    }

    /** @return the dungeon */
    public DungeonInfo getDungeon() {
        return dungeon;
    }

    /** @return the dungeon id, lower-case */
    public String getDungeonId() {
        return dungeon.id();
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
