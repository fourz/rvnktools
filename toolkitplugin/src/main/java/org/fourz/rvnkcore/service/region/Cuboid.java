package org.fourz.rvnkcore.service.region;

/**
 * An axis-aligned block box, inclusive on both corners (#2248). The WorldGuard-free twin of a
 * {@code ProtectedCuboidRegion}'s bounds, so region and NPC-zone logic can be unit-tested without
 * WorldGuard.
 *
 * <p>Always normalised: {@code min* <= max*} on every axis, whatever order the corners came in.</p>
 *
 * @since 1.5.100-alpha
 */
public record Cuboid(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {

    public Cuboid {
        if (minX > maxX) {
            int t = minX; minX = maxX; maxX = t;
        }
        if (minY > maxY) {
            int t = minY; minY = maxY; maxY = t;
        }
        if (minZ > maxZ) {
            int t = minZ; minZ = maxZ; maxZ = t;
        }
    }

    /**
     * @return a cuboid spanning two corners given in any order
     */
    public static Cuboid of(int x1, int y1, int z1, int x2, int y2, int z2) {
        return new Cuboid(x1, y1, z1, x2, y2, z2);
    }

    /** @return the number of blocks inside, as a long so a large box cannot overflow */
    public long volume() {
        return (long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
    }

    /** @return {@code x1,y1,z1 -> x2,y2,z2} */
    @Override
    public String toString() {
        return minX + "," + minY + "," + minZ + " -> " + maxX + "," + maxY + "," + maxZ;
    }
}
