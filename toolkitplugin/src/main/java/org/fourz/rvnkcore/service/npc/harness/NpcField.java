package org.fourz.rvnkcore.service.npc.harness;

import java.util.Locale;

/**
 * A spec field the harness manages (#2248). The order is the order {@code apply} writes them.
 *
 * @since 1.5.100-alpha
 */
public enum NpcField {
    NAME, WORLD, POSITION, SKIN, LOOKCLOSE, PROTECTED, POSE, HOLD, NAMEPLATE, ZONE;

    /** @return the spec spelling */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }
}
