package org.fourz.rvnkcore.service.npc.harness;

/**
 * One NPC declared in a spec file (#2248). A null optional field means "not managed": apply leaves
 * it alone and verify does not compare it.
 *
 * @param key       RVNK key, lower-case
 * @param name      display name
 * @param world     world name
 * @param x         x
 * @param y         y (feet)
 * @param z         z
 * @param yaw       body yaw in (-180, 180], or null
 * @param pitch     pitch in [-90, 90], or null
 * @param skin      player name or URL, or null
 * @param lookClose LookClose on/off, or null
 * @param protect   Citizens "protected" (invulnerable), or null
 * @param pose      pose, or null
 * @param hold      main-hand material key (e.g. "iron_sword"), "none" for empty, or null
 * @param nameplate name-plate mode, or null
 * @param zone      WorldGuard protect zone, or null
 * @since 1.5.100-alpha
 */
public record NpcSpec(String key, String name, String world, double x, double y, double z,
                      Float yaw, Float pitch, String skin, Boolean lookClose, Boolean protect,
                      NpcPose pose, String hold, NpcNameplate nameplate, NpcZone zone) {

    /** @return a spec with only the required fields */
    public static NpcSpec basic(String key, String name, String world, double x, double y, double z) {
        return new NpcSpec(key, name, world, x, y, z, null, null, null, null, null, null, null, null, null);
    }

    public NpcSpec withPosition(String newWorld, double nx, double ny, double nz) {
        return new NpcSpec(key, name, newWorld, nx, ny, nz, yaw, pitch, skin, lookClose, protect, pose, hold,
                nameplate, zone);
    }

    public NpcSpec withZone(NpcZone newZone) {
        return new NpcSpec(key, name, world, x, y, z, yaw, pitch, skin, lookClose, protect, pose, hold,
                nameplate, newZone);
    }
}
