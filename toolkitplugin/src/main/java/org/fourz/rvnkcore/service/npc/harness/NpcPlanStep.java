package org.fourz.rvnkcore.service.npc.harness;

import java.util.List;

/**
 * What {@code apply} will do for one spec key (#2248).
 *
 * @param key       RVNK key
 * @param action    the action
 * @param changes   fields to write; empty for NOOP and BLOCKED
 * @param backingId Citizens id of the live NPC, or -1 when there is none
 * @param notes     extra lines: why a step is blocked, a zone that cannot be checked, ...
 * @since 1.5.100-alpha
 */
public record NpcPlanStep(String key, Action action, List<NpcChange> changes, int backingId, List<String> notes) {

    public enum Action {
        /** No NPC carries the key: create it. */
        CREATE,
        /** The NPC exists and some fields differ. */
        UPDATE,
        /** The NPC exists and matches the spec. */
        NOOP,
        /** Cannot act on this key now (world not loaded, key on two NPCs). */
        BLOCKED
    }
}
