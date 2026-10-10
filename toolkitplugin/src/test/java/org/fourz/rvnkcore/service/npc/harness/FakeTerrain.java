package org.fourz.rvnkcore.service.npc.harness;

import java.util.HashSet;
import java.util.Set;

/** In-memory {@link NpcGround.Terrain}: a set of solid blocks; every other block is air (passable). */
class FakeTerrain implements NpcGround.Terrain {

    private final Set<String> solid = new HashSet<>();
    int reads;

    /** Fills a column with solid blocks from {@code bottom} to {@code top}, inclusive. */
    FakeTerrain column(String world, int x, int z, int bottom, int top) {
        for (int y = bottom; y <= top; y++) {
            solid.add(id(world, x, y, z));
        }
        return this;
    }

    /** Ground whose top block is {@code top}, so an NPC stands with its feet at {@code top + 1}. */
    FakeTerrain ground(String world, int x, int z, int top) {
        return column(world, x, z, top - 10, top);
    }

    FakeTerrain block(String world, int x, int y, int z) {
        solid.add(id(world, x, y, z));
        return this;
    }

    @Override
    public boolean solid(String world, int x, int y, int z) {
        reads++;
        return solid.contains(id(world, x, y, z));
    }

    @Override
    public boolean passable(String world, int x, int y, int z) {
        reads++;
        return !solid.contains(id(world, x, y, z));
    }

    private static String id(String world, int x, int y, int z) {
        return world + "/" + x + "/" + y + "/" + z;
    }
}
