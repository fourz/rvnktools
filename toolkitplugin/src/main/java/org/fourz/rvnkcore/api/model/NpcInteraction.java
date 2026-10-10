package org.fourz.rvnkcore.api.model;

import org.fourz.rvnkcore.api.event.RvnkNpcInteractEvent;

import java.util.Objects;

/**
 * The last NPC a player interacted with, as returned by
 * {@link org.fourz.rvnkcore.api.service.INpcService#lastInteraction(java.util.UUID)} (#2213).
 *
 * <p>Held in memory only, so it is empty after a restart. Recorded only for interactions whose
 * {@link RvnkNpcInteractEvent} was not cancelled.</p>
 *
 * @param key         the NPC's RVNK key
 * @param displayName the NPC's name without colour codes
 * @param clickType   LEFT or RIGHT
 * @param atMillis    when the interaction happened, epoch milliseconds
 * @since 1.5.99-alpha
 */
public record NpcInteraction(String key, String displayName, RvnkNpcInteractEvent.ClickType clickType, long atMillis) {

    public NpcInteraction {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(clickType, "clickType");
        displayName = displayName == null ? "" : displayName;
    }
}
