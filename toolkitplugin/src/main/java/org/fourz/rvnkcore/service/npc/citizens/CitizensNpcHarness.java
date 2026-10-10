package org.fourz.rvnkcore.service.npc.citizens;

import net.citizensnpcs.api.event.DespawnReason;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.api.npc.NPCRegistry;
import net.citizensnpcs.api.trait.trait.Equipment;
import net.citizensnpcs.trait.CurrentLocation;
import net.citizensnpcs.trait.LookClose;
import net.citizensnpcs.trait.SitTrait;
import net.citizensnpcs.trait.SkinTrait;
import net.citizensnpcs.trait.SneakTrait;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.fourz.rvnkcore.service.npc.NpcKeys;
import org.fourz.rvnkcore.service.npc.harness.NpcGround;
import org.fourz.rvnkcore.service.npc.harness.NpcHarness;
import org.fourz.rvnkcore.service.npc.harness.NpcNameplate;
import org.fourz.rvnkcore.service.npc.harness.NpcPose;
import org.fourz.rvnkcore.service.npc.harness.NpcSkins;
import org.fourz.rvnkcore.service.npc.harness.NpcState;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * {@link NpcHarness} backed by the Citizens API (#2248).
 *
 * <p><b>Why the API and not {@code /npc} commands.</b> Citizens' {@code /npc create} from the
 * console throws a {@code NullPointerException} on {@code Location.getChunk()} unless {@code --at}
 * is given, because the console sender has no location (#2243). Here every location is explicit:
 * {@code createNPC(PLAYER, name)} then {@code spawn(location)}.</p>
 *
 * <p><b>Chunks.</b> Before a spawn or move the target chunk is loaded synchronously, so the NPC
 * spawns at once. If Citizens still refuses the spawn, the location is stored in
 * {@link CurrentLocation} and Citizens spawns the NPC when the chunk next loads.</p>
 *
 * <p><b>Skins.</b> A player-name skin goes through {@link SkinTrait#setSkinName(String, boolean)};
 * Citizens fetches the profile off the main thread, and only for a spawned NPC. For a spawned NPC,
 * 5 seconds later this class reports whether a texture arrived; "not yet loaded" is not a failure
 * (Citizens retries). For a despawned NPC no check runs: the result says Citizens fetches the skin
 * when the NPC spawns (1.5.101). A URL skin is generated on an async task through Citizens' own
 * {@code MojangSkinGenerator} (reached by reflection, because its return type is a json-simple
 * class that is not on RVNKCore's compile classpath), then applied on the main thread with
 * {@code setSkinPersistent}. The source text is stored as persistent metadata {@code rvnk-skin} so
 * verify can compare it with the spec.</p>
 *
 * <p><b>Terrain.</b> {@link #terrain()} reads Bukkit blocks for the standable-Y snap
 * ({@link NpcGround}): solid = the block has collision ({@code !isPassable()}); passable =
 * {@code isPassable()} and not lava. Outside the world's height range, nothing is solid and
 * everything is passable. An unloaded world reads as neither, so the snap keeps the requested Y.</p>
 *
 * <p>Main thread only. Every method catches its own failures and returns a failed result.</p>
 *
 * @since 1.5.100-alpha
 */
public class CitizensNpcHarness implements NpcHarness {

    /** Persistent metadata holding the skin source last set by the harness. */
    public static final String SKIN_SOURCE_KEY = "rvnk-skin";

    private static final long SKIN_CHECK_TICKS = 100L;

    private final Supplier<NPCRegistry> registry;
    private final Plugin plugin;
    private final Consumer<String> warn;

    /**
     * @param registry the default NPC registry
     * @param plugin   RVNKCore, which owns the scheduler tasks
     * @param warn     warning sink
     */
    public CitizensNpcHarness(Supplier<NPCRegistry> registry, Plugin plugin, Consumer<String> warn) {
        this.registry = registry;
        this.plugin = plugin;
        this.warn = warn != null ? warn : message -> { };
    }

    /** Bukkit block reads for the standable-Y snap. Main thread only; reading a block loads its chunk. */
    static final NpcGround.Terrain BUKKIT_TERRAIN = new NpcGround.Terrain() {
        @Override
        public boolean solid(String world, int x, int y, int z) {
            Block block = blockAt(world, x, y, z);
            return block != null && !block.isPassable();
        }

        @Override
        public boolean passable(String world, int x, int y, int z) {
            World bukkitWorld = world == null ? null : Bukkit.getWorld(world);
            if (bukkitWorld == null) {
                return false;
            }
            if (y < bukkitWorld.getMinHeight() || y >= bukkitWorld.getMaxHeight()) {
                return true;
            }
            Block block = bukkitWorld.getBlockAt(x, y, z);
            return block.isPassable() && block.getType() != Material.LAVA;
        }

        private Block blockAt(String world, int x, int y, int z) {
            World bukkitWorld = world == null ? null : Bukkit.getWorld(world);
            if (bukkitWorld == null || y < bukkitWorld.getMinHeight() || y >= bukkitWorld.getMaxHeight()) {
                return null;
            }
            return bukkitWorld.getBlockAt(x, y, z);
        }
    };

    @Override
    public NpcGround.Terrain terrain() {
        return BUKKIT_TERRAIN;
    }

    // ── read ───────────────────────────────────────────────────────────────────

    @Override
    public List<NpcState> snapshot() {
        List<NpcState> states = new ArrayList<>();
        try {
            for (NPC npc : registry.get()) {
                String key = CitizensNpcService.keyOfNpc(npc);
                if (key != null) {
                    states.add(stateOf(npc, key));
                }
            }
        } catch (RuntimeException | LinkageError e) {
            warn.accept("NPC snapshot failed: " + e);
        }
        return states;
    }

    static NpcState stateOf(NPC npc, String key) {
        Location location = CitizensNpcService.locationOf(npc);
        String world = CitizensNpcService.worldName(location);
        Double x = location == null ? null : location.getX();
        Double y = location == null ? null : location.getY();
        Double z = location == null ? null : location.getZ();
        float yaw = location == null ? 0f : location.getYaw();
        float pitch = location == null ? 0f : location.getPitch();

        Object skinSource = npc.data().get(SKIN_SOURCE_KEY);
        String skin = skinSource != null ? skinSource.toString() : null;
        if (skin == null) {
            SkinTrait skinTrait = npc.getTraitNullable(SkinTrait.class);
            skin = skinTrait == null ? null : skinTrait.getSkinName();
        }

        LookClose lookClose = npc.getTraitNullable(LookClose.class);
        SitTrait sit = npc.getTraitNullable(SitTrait.class);
        SneakTrait sneak = npc.getTraitNullable(SneakTrait.class);
        NpcPose pose = sit != null && sit.isSitting() ? NpcPose.SIT
                : sneak != null && sneak.isSneaking() ? NpcPose.SNEAK : NpcPose.STAND;

        Equipment equipment = npc.getTraitNullable(Equipment.class);
        ItemStack hand = equipment == null ? null : equipment.get(Equipment.EquipmentSlot.HAND);
        String hold = hand == null || hand.getType().isAir() ? null : materialKey(hand.getType());

        Object plate = npc.data().<Object>get(NPC.Metadata.NAMEPLATE_VISIBLE, Boolean.TRUE);

        return new NpcState(key, npc.getId(), npc.getName(), world, x, y, z, yaw, pitch, npc.isSpawned(), skin,
                lookClose != null && lookClose.isEnabled(), npc.isProtected(), pose, hold,
                NpcNameplate.fromCitizens(plate));
    }

    // ── place and edit ─────────────────────────────────────────────────────────

    @Override
    public Result create(String rawKey, String name, Location at) {
        String key = NpcKeys.normalize(rawKey);
        if (key == null) {
            return Result.fail("invalid key '" + rawKey + "'");
        }
        if (name == null || name.isBlank()) {
            return Result.fail("name is empty");
        }
        if (at == null || at.getWorld() == null) {
            return Result.fail("location has no loaded world");
        }
        Lookup existing = find(key);
        if (existing.npc != null || existing.count > 1) {
            return Result.fail("key '" + key + "' is already on NPC " + existing.ids
                    + ". Use /rvnk npc move " + key + " <world> <x> <y> <z> to reposition it.");
        }
        NPC npc = null;
        try {
            npc = registry.get().createNPC(EntityType.PLAYER, name);
            npc.data().setPersistent(NpcKeys.METADATA_KEY, key);
            boolean spawned = spawnAt(npc, at);
            save();
            return Result.ok("created NPC #" + npc.getId() + " '" + name + "' at " + describe(at)
                    + (spawned ? "" : " (stored; it spawns when the chunk loads)"));
        } catch (RuntimeException | LinkageError e) {
            if (npc != null) {
                try {
                    npc.destroy();
                } catch (RuntimeException ignored) {
                    // the original failure is the one to report
                }
            }
            return Result.fail("Citizens could not create the NPC: " + e);
        }
    }

    @Override
    public Result move(String key, Location to) {
        if (to == null || to.getWorld() == null) {
            return Result.fail("location has no loaded world");
        }
        return withNpc(key, npc -> {
            boolean spawned;
            Entity entity = npc.isSpawned() ? npc.getEntity() : null;
            loadChunk(to);
            if (entity != null && to.getWorld().equals(entity.getWorld())) {
                npc.teleport(to, PlayerTeleportEvent.TeleportCause.PLUGIN);
                spawned = true;
            } else {
                if (entity != null) {
                    npc.despawn(DespawnReason.PENDING_RESPAWN);
                }
                spawned = spawnAt(npc, to);
            }
            SitTrait sit = npc.getTraitNullable(SitTrait.class);
            if (sit != null && sit.isSitting()) {
                sit.setSitting(to);
            }
            save();
            return Result.ok("moved #" + npc.getId() + " to " + describe(to)
                    + (spawned ? "" : " (stored; it spawns when the chunk loads)"));
        });
    }

    @Override
    public Result rename(String key, String name) {
        if (name == null || name.isBlank()) {
            return Result.fail("name is empty");
        }
        return withNpc(key, npc -> {
            npc.setName(name);
            save();
            return Result.ok("renamed #" + npc.getId() + " to '" + name + "'");
        });
    }

    @Override
    public Result remove(String key) {
        return withNpc(key, npc -> {
            int id = npc.getId();
            npc.destroy();
            save();
            return Result.ok("removed NPC #" + id + " and its key");
        });
    }

    @Override
    public Result skin(String key, String source, Consumer<String> later) {
        Consumer<String> report = later != null ? later : message -> { };
        if (!NpcSkins.isValid(source)) {
            return Result.fail("skin must be a player name or an http(s) URL: " + source);
        }
        return withNpc(key, npc -> {
            String normalKey = CitizensNpcService.keyOfNpc(npc);
            if (NpcSkins.isUrl(source)) {
                Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> fetchUrlSkin(normalKey, source, report));
                return Result.ok("fetching skin from URL in the background; the result follows");
            }
            SkinTrait trait = npc.getOrAddTrait(SkinTrait.class);
            trait.setSkinName(source, true);
            npc.data().setPersistent(SKIN_SOURCE_KEY, source);
            save();
            boolean spawned = npc.isSpawned();
            if (spawned) {
                // a despawned NPC is not fetched until it spawns, so a 5 s check would be a false FAILED
                Bukkit.getScheduler().runTaskLater(plugin, () -> checkNameSkin(normalKey, source, report), SKIN_CHECK_TICKS);
            }
            return Result.ok(NpcSkins.requestMessage(source, spawned));
        });
    }

    @Override
    public Result lookClose(String key, boolean on) {
        return withNpc(key, npc -> {
            npc.getOrAddTrait(LookClose.class).lookClose(on);
            save();
            return Result.ok("lookclose " + (on ? "on" : "off"));
        });
    }

    @Override
    public Result pose(String key, NpcPose pose) {
        if (pose == null) {
            return Result.fail("pose must be stand, sit or sneak");
        }
        return withNpc(key, npc -> {
            SitTrait sit = npc.getTraitNullable(SitTrait.class);
            SneakTrait sneak = npc.getTraitNullable(SneakTrait.class);
            switch (pose) {
                case STAND -> {
                    if (sit != null && sit.isSitting()) {
                        sit.setSitting(null);
                    }
                    if (sneak != null) {
                        sneak.setSneaking(false);
                    }
                }
                case SIT -> {
                    Location at = CitizensNpcService.locationOf(npc);
                    if (at == null) {
                        return Result.fail("NPC #" + npc.getId() + " has no location to sit at");
                    }
                    if (sneak != null) {
                        sneak.setSneaking(false);
                    }
                    npc.getOrAddTrait(SitTrait.class).setSitting(at);
                }
                case SNEAK -> {
                    if (sit != null && sit.isSitting()) {
                        sit.setSitting(null);
                    }
                    npc.getOrAddTrait(SneakTrait.class).setSneaking(true);
                }
                default -> {
                    return Result.fail("unsupported pose " + pose);
                }
            }
            save();
            return Result.ok("pose " + pose.id());
        });
    }

    @Override
    public Result hold(String key, String material) {
        ItemStack item;
        if (material == null || "none".equalsIgnoreCase(material)) {
            item = new ItemStack(Material.AIR);
        } else {
            Material type = Material.matchMaterial(material);
            if (type == null || !type.isItem() || type.isAir()) {
                return Result.fail("unknown or non-item material '" + material + "'");
            }
            item = new ItemStack(type);
        }
        return withNpc(key, npc -> {
            npc.getOrAddTrait(Equipment.class).set(Equipment.EquipmentSlot.HAND, item);
            save();
            return Result.ok("holding " + (item.getType().isAir() ? "nothing" : materialKey(item.getType())));
        });
    }

    @Override
    public Result setProtected(String key, boolean on) {
        return withNpc(key, npc -> {
            npc.setProtected(on);
            save();
            return Result.ok("protected " + on);
        });
    }

    @Override
    public Result nameplate(String key, NpcNameplate mode) {
        if (mode == null) {
            return Result.fail("nameplate must be on, off or hover");
        }
        return withNpc(key, npc -> {
            npc.data().setPersistent(NPC.Metadata.NAMEPLATE_VISIBLE, mode.citizensValue());
            npc.scheduleUpdate(NPC.NPCUpdate.PACKET);
            save();
            return Result.ok("nameplate " + mode.id());
        });
    }

    // ── skins ──────────────────────────────────────────────────────────────────

    private void checkNameSkin(String key, String source, Consumer<String> report) {
        try {
            Lookup lookup = find(key);
            if (lookup.npc == null) {
                report.accept("skin " + key + ": NPC is gone; skin not checked");
                return;
            }
            SkinTrait trait = lookup.npc.getTraitNullable(SkinTrait.class);
            boolean loaded = trait != null && trait.getTexture() != null;
            report.accept(NpcSkins.checkMessage(key, source, lookup.npc.isSpawned(), loaded,
                    trait == null ? "the NPC has no skin trait" : null));
        } catch (RuntimeException | LinkageError e) {
            report.accept(NpcSkins.checkMessage(key, source, true, false, "skin check failed: " + e));
        }
    }

    /** Runs on an async thread; switches back to the main thread to apply. */
    private void fetchUrlSkin(String key, String url, Consumer<String> report) {
        String uuid;
        String value;
        String signature;
        try {
            Map<?, ?> data = generateFromUrl(url);
            Object texture = data.get("texture");
            if (!(texture instanceof Map<?, ?> tex)) {
                throw new IllegalStateException("response has no texture");
            }
            uuid = String.valueOf(data.get("uuid"));
            value = (String) tex.get("value");
            signature = (String) tex.get("signature");
            if (value == null || signature == null) {
                throw new IllegalStateException("response has no texture value or signature");
            }
        } catch (Exception | LinkageError e) {
            Throwable cause = e instanceof InvocationTargetException ite && ite.getCause() != null ? ite.getCause() : e;
            String reason = cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
            Bukkit.getScheduler().runTask(plugin, () -> report.accept(
                    "skin " + key + ": FAILED to generate a skin from the URL: " + reason));
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                Lookup lookup = find(key);
                if (lookup.npc == null) {
                    report.accept("skin " + key + ": NPC is gone; URL skin not applied");
                    return;
                }
                lookup.npc.getOrAddTrait(SkinTrait.class).setSkinPersistent(uuid, signature, value);
                lookup.npc.data().setPersistent(SKIN_SOURCE_KEY, url);
                save();
                report.accept("skin " + key + ": URL skin applied");
            } catch (RuntimeException | LinkageError e) {
                report.accept("skin " + key + ": FAILED to apply the URL skin: " + e);
            }
        });
    }

    private static Map<?, ?> generateFromUrl(String url) throws Exception {
        Class<?> generator = Class.forName("net.citizensnpcs.util.MojangSkinGenerator");
        Method method = generator.getMethod("generateFromURL", String.class, boolean.class);
        Object result = method.invoke(null, url, false);
        if (!(result instanceof Map<?, ?> map)) {
            throw new IllegalStateException("skin generator returned " + (result == null ? "nothing" : result.getClass().getName()));
        }
        return map;
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    private record Lookup(NPC npc, int count, String ids) {
    }

    /** Finds the one NPC carrying a key; npc is null when none or several carry it. */
    private Lookup find(String key) {
        List<NPC> matches = new ArrayList<>();
        for (NPC npc : registry.get()) {
            if (key.equals(CitizensNpcService.keyOfNpc(npc))) {
                matches.add(npc);
            }
        }
        List<String> ids = new ArrayList<>();
        for (NPC npc : matches) {
            ids.add("#" + npc.getId());
        }
        return new Lookup(matches.size() == 1 ? matches.get(0) : null, matches.size(), String.join(", ", ids));
    }

    private interface NpcAction {
        Result run(NPC npc);
    }

    private Result withNpc(String rawKey, NpcAction action) {
        String key = NpcKeys.normalize(rawKey);
        if (key == null) {
            return Result.fail("invalid key '" + rawKey + "'");
        }
        try {
            Lookup lookup = find(key);
            if (lookup.count == 0) {
                return Result.fail("no NPC carries key '" + key + "'");
            }
            if (lookup.count > 1) {
                return Result.fail("key '" + key + "' is on " + lookup.count + " NPCs (" + lookup.ids
                        + "); remove the extras first");
            }
            return action.run(lookup.npc);
        } catch (RuntimeException | LinkageError e) {
            return Result.fail("Citizens error: " + e);
        }
    }

    /** Spawns at a location, storing it for a later spawn when Citizens refuses now. */
    private static boolean spawnAt(NPC npc, Location at) {
        loadChunk(at);
        boolean spawned = npc.spawn(at);
        if (!spawned) {
            npc.getOrAddTrait(CurrentLocation.class).setLocation(at);
        }
        return spawned;
    }

    private static void loadChunk(Location at) {
        World world = at.getWorld();
        if (world != null) {
            world.getChunkAt(at.getBlockX() >> 4, at.getBlockZ() >> 4); // loads (or generates) it
        }
    }

    private static String materialKey(Material material) {
        return material.getKey().getKey().toLowerCase(Locale.ROOT);
    }

    private static String describe(Location at) {
        String world = CitizensNpcService.worldName(at);
        return (world == null ? "?" : world) + " " + String.format(Locale.ROOT, "%.2f,%.2f,%.2f yaw %.1f",
                at.getX(), at.getY(), at.getZ(), at.getYaw());
    }

    private void save() {
        try {
            registry.get().saveToStore();
        } catch (RuntimeException e) {
            warn.accept("NPC change applied but Citizens could not save it now (" + e.getMessage()
                    + "); it saves at the next Citizens autosave");
        }
    }
}
