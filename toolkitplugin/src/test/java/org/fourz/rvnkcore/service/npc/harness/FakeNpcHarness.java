package org.fourz.rvnkcore.service.npc.harness;

import org.bukkit.Location;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * In-memory {@link NpcHarness}. Records every write so tests can assert a second apply writes
 * nothing. Mimics Citizens defaults: protected true, nameplate on, stand, empty hand, lookclose off.
 */
class FakeNpcHarness implements NpcHarness {

    final List<NpcState> npcs = new ArrayList<>();
    final List<String> writes = new ArrayList<>();
    private int nextId = 1;

    /** Adds an NPC as if staff had created and tagged it by hand. */
    NpcState put(String key, String name, String world, double x, double y, double z) {
        NpcState state = new NpcState(key, nextId++, name, world, x, y, z, 0f, 0f, true, null, false, true,
                NpcPose.STAND, null, NpcNameplate.ON);
        npcs.add(state);
        return state;
    }

    void replace(String key, java.util.function.UnaryOperator<NpcState> change) {
        for (int i = 0; i < npcs.size(); i++) {
            if (npcs.get(i).key().equals(key)) {
                npcs.set(i, change.apply(npcs.get(i)));
                return;
            }
        }
        throw new IllegalStateException("no npc " + key);
    }

    @Override
    public List<NpcState> snapshot() {
        return List.copyOf(npcs);
    }

    @Override
    public Result create(String key, String name, Location at) {
        writes.add("create " + key);
        if (state(key).isPresent()) {
            return Result.fail("key in use");
        }
        npcs.add(new NpcState(key, nextId++, name, at.getWorld().getName(), at.getX(), at.getY(), at.getZ(),
                at.getYaw(), at.getPitch(), true, null, false, true, NpcPose.STAND, null, NpcNameplate.ON));
        return Result.ok("created");
    }

    @Override
    public Result move(String key, Location to) {
        writes.add("move " + key);
        replace(key, s -> new NpcState(s.key(), s.backingId(), s.name(), to.getWorld().getName(), to.getX(), to.getY(),
                to.getZ(), to.getYaw(), to.getPitch(), true, s.skin(), s.lookClose(), s.protect(), s.pose(), s.hold(),
                s.nameplate()));
        return Result.ok("moved");
    }

    @Override
    public Result rename(String key, String name) {
        writes.add("rename " + key);
        replace(key, s -> new NpcState(s.key(), s.backingId(), name, s.world(), s.x(), s.y(), s.z(), s.yaw(), s.pitch(),
                s.spawned(), s.skin(), s.lookClose(), s.protect(), s.pose(), s.hold(), s.nameplate()));
        return Result.ok("renamed");
    }

    @Override
    public Result remove(String key) {
        writes.add("remove " + key);
        return npcs.removeIf(s -> s.key().equals(key)) ? Result.ok("removed") : Result.fail("none");
    }

    @Override
    public Result skin(String key, String source, Consumer<String> later) {
        writes.add("skin " + key);
        replace(key, s -> new NpcState(s.key(), s.backingId(), s.name(), s.world(), s.x(), s.y(), s.z(), s.yaw(), s.pitch(),
                s.spawned(), source, s.lookClose(), s.protect(), s.pose(), s.hold(), s.nameplate()));
        later.accept("skin " + key + " applied");
        return Result.ok("skin requested");
    }

    @Override
    public Result lookClose(String key, boolean on) {
        writes.add("lookclose " + key);
        replace(key, s -> new NpcState(s.key(), s.backingId(), s.name(), s.world(), s.x(), s.y(), s.z(), s.yaw(), s.pitch(),
                s.spawned(), s.skin(), on, s.protect(), s.pose(), s.hold(), s.nameplate()));
        return Result.ok("lookclose");
    }

    @Override
    public Result pose(String key, NpcPose pose) {
        writes.add("pose " + key);
        replace(key, s -> new NpcState(s.key(), s.backingId(), s.name(), s.world(), s.x(), s.y(), s.z(), s.yaw(), s.pitch(),
                s.spawned(), s.skin(), s.lookClose(), s.protect(), pose, s.hold(), s.nameplate()));
        return Result.ok("pose");
    }

    @Override
    public Result hold(String key, String material) {
        writes.add("hold " + key);
        String hold = "none".equals(material) ? null : material;
        replace(key, s -> new NpcState(s.key(), s.backingId(), s.name(), s.world(), s.x(), s.y(), s.z(), s.yaw(), s.pitch(),
                s.spawned(), s.skin(), s.lookClose(), s.protect(), s.pose(), hold, s.nameplate()));
        return Result.ok("hold");
    }

    @Override
    public Result setProtected(String key, boolean on) {
        writes.add("protected " + key);
        replace(key, s -> new NpcState(s.key(), s.backingId(), s.name(), s.world(), s.x(), s.y(), s.z(), s.yaw(), s.pitch(),
                s.spawned(), s.skin(), s.lookClose(), on, s.pose(), s.hold(), s.nameplate()));
        return Result.ok("protected");
    }

    @Override
    public Result nameplate(String key, NpcNameplate mode) {
        writes.add("nameplate " + key);
        replace(key, s -> new NpcState(s.key(), s.backingId(), s.name(), s.world(), s.x(), s.y(), s.z(), s.yaw(), s.pitch(),
                s.spawned(), s.skin(), s.lookClose(), s.protect(), s.pose(), s.hold(), mode));
        return Result.ok("nameplate");
    }
}
