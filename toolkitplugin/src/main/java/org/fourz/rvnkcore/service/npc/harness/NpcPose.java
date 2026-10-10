package org.fourz.rvnkcore.service.npc.harness;

import java.util.Locale;

/**
 * Body pose an NPC holds (#2248). Citizens backs SIT with {@code SitTrait} and SNEAK with
 * {@code SneakTrait}; STAND clears both.
 *
 * @since 1.5.100-alpha
 */
public enum NpcPose {
    STAND, SIT, SNEAK;

    /** @return the pose, or null when the text is not one; accepts sitting/sneaking/standing too */
    public static NpcPose parse(String raw) {
        if (raw == null) {
            return null;
        }
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "stand", "standing" -> STAND;
            case "sit", "sitting" -> SIT;
            case "sneak", "sneaking", "crouch" -> SNEAK;
            default -> null;
        };
    }

    /** @return the spec spelling: stand, sit or sneak */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }
}
