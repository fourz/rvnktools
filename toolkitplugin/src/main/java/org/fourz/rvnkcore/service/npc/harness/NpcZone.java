package org.fourz.rvnkcore.service.npc.harness;

import org.fourz.rvnkcore.service.region.Cuboid;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The WorldGuard protect zone around a keyed NPC (#2248): region {@code npc_<key>}.
 *
 * <p><b>Shape.</b> A square column centred on the NPC's block: x and z from {@code block - radius}
 * to {@code block + radius}, y from the NPC's feet block up {@code height} blocks
 * ({@code feet .. feet + height - 1}). Radius 2, height 3 is a 5 x 3 x 5 box.</p>
 *
 * <p><b>Flags.</b> interact=allow and use=allow, so players can click the NPC and use things near
 * it inside a protected area; mob-spawning=deny, so nothing spawns on top of it. A NEW zone gets
 * priority {@link #NEW_ZONE_PRIORITY} so it wins over a priority-0 region around it (with equal
 * priority WorldGuard lets deny win); an existing zone keeps its priority.</p>
 *
 * @param radius horizontal radius in blocks, {@value #MIN_RADIUS}-{@value #MAX_RADIUS}
 * @param height height in blocks, {@value #MIN_HEIGHT}-{@value #MAX_HEIGHT}
 * @since 1.5.100-alpha
 */
public record NpcZone(int radius, int height) {

    public static final int DEFAULT_RADIUS = 2;
    public static final int DEFAULT_HEIGHT = 3;
    public static final int MIN_RADIUS = 0;
    public static final int MAX_RADIUS = 16;
    public static final int MIN_HEIGHT = 1;
    public static final int MAX_HEIGHT = 32;
    public static final int NEW_ZONE_PRIORITY = 10;
    public static final String REGION_PREFIX = "npc_";

    /** The flags every zone carries, in the order they are applied. */
    public static final Map<String, String> FLAGS;

    static {
        Map<String, String> flags = new LinkedHashMap<>();
        flags.put("interact", "allow");
        flags.put("use", "allow");
        flags.put("mob-spawning", "deny");
        FLAGS = java.util.Collections.unmodifiableMap(flags);
    }

    public static NpcZone defaults() {
        return new NpcZone(DEFAULT_RADIUS, DEFAULT_HEIGHT);
    }

    /** @return null when both values are in range, else the error */
    public static String validate(int radius, int height) {
        if (radius < MIN_RADIUS || radius > MAX_RADIUS) {
            return "radius must be " + MIN_RADIUS + "-" + MAX_RADIUS + ": " + radius;
        }
        if (height < MIN_HEIGHT || height > MAX_HEIGHT) {
            return "height must be " + MIN_HEIGHT + "-" + MAX_HEIGHT + ": " + height;
        }
        return null;
    }

    /** @return the region id for a key: {@code npc_<key>} */
    public static String regionId(String key) {
        return REGION_PREFIX + key;
    }

    /** @return the zone's bounds around a position */
    public Cuboid around(double x, double y, double z) {
        int bx = (int) Math.floor(x);
        int by = (int) Math.floor(y);
        int bz = (int) Math.floor(z);
        return Cuboid.of(bx - radius, by, bz - radius, bx + radius, by + height - 1, bz + radius);
    }

    /**
     * Infers the zone that produced a region, for export.
     *
     * @return the zone, or null when the box is not a zone shape around that position
     */
    public static NpcZone infer(Cuboid box, double x, double y, double z) {
        if (box == null) {
            return null;
        }
        int width = box.maxX() - box.minX();
        if (width % 2 != 0 || width != box.maxZ() - box.minZ()) {
            return null;
        }
        NpcZone zone = new NpcZone(width / 2, box.maxY() - box.minY() + 1);
        if (validate(zone.radius, zone.height) != null) {
            return null;
        }
        return box.equals(zone.around(x, y, z)) ? zone : null;
    }
}
