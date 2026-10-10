package org.fourz.rvnkcore.service.npc.harness;

/**
 * What a keyed NPC looks like right now (#2248), read from the NPC plugin by
 * {@link NpcHarness#snapshot()}. Plain data, so the diff, the planner and the verifier are
 * unit-testable without Citizens.
 *
 * @param key       RVNK key
 * @param backingId Citizens NPC id
 * @param name      name without colour codes
 * @param world     world name, or null when unknown or unloaded
 * @param x         x, or null when the NPC has no location
 * @param y         y, or null
 * @param z         z, or null
 * @param yaw       yaw
 * @param pitch     pitch
 * @param spawned   whether the entity is spawned
 * @param skin      the skin source the harness set (player name or URL), else the Citizens skin
 *                  name, else null
 * @param lookClose LookClose enabled
 * @param protect   Citizens protected flag
 * @param pose      current pose
 * @param hold      main-hand material key, or null when empty
 * @param nameplate name-plate mode
 * @since 1.5.100-alpha
 */
public record NpcState(String key, int backingId, String name, String world, Double x, Double y, Double z,
                       float yaw, float pitch, boolean spawned, String skin, boolean lookClose, boolean protect,
                       NpcPose pose, String hold, NpcNameplate nameplate) {

    /** @return true when the NPC has a stored or live location */
    public boolean hasLocation() {
        return x != null && y != null && z != null;
    }
}
