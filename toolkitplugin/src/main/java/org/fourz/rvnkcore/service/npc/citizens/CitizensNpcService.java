package org.fourz.rvnkcore.service.npc.citizens;

import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.api.npc.NPCRegistry;
import net.citizensnpcs.api.npc.NPCSelector;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.fourz.rvnkcore.api.model.NpcInteraction;
import org.fourz.rvnkcore.api.model.NpcRef;
import org.fourz.rvnkcore.api.service.INpcService;
import org.fourz.rvnkcore.service.npc.NpcInteractionTracker;
import org.fourz.rvnkcore.service.npc.NpcKeys;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * {@link INpcService} backed by Citizens2 (#2213).
 *
 * <p><b>Persistence.</b> The key is stored as Citizens <em>persistent metadata</em>:
 * {@code npc.data().setPersistent("rvnk-key", key)}. Citizens writes persistent metadata into
 * {@code saves.yml} under the NPC's {@code metadata} section and reads it back on load, so the key
 * survives restarts with no trait class to register. A {@code @Persist} trait would also work, but
 * it needs a {@code TraitInfo} registration that must run before Citizens loads its NPCs, and an
 * unregistered trait in saves.yml is a load error on a server without RVNKCore. Metadata needs
 * neither. After a tag or untag the registry is saved at once, so a crash does not lose the change.</p>
 *
 * <p><b>Lookup.</b> {@link #findByKey} scans the default registry. NPC counts are tens to low
 * hundreds, so a scan costs microseconds and is never stale; a cached index would go stale whenever
 * staff run {@code /npc remove} or {@code /citizens reload}.</p>
 *
 * <p>Main thread only, except {@link #isAvailable()} and {@link #lastInteraction(UUID)}.</p>
 *
 * @since 1.5.99-alpha
 */
public class CitizensNpcService implements INpcService {

    private final Supplier<NPCRegistry> registry;
    private final Supplier<NPCSelector> selector;
    private final BooleanSupplier citizensReady;
    private final NpcInteractionTracker tracker;
    private final Consumer<String> warn;

    /**
     * @param registry      the default NPC registry ({@code CitizensAPI::getNPCRegistry})
     * @param selector      the default selector ({@code CitizensAPI::getDefaultNPCSelector})
     * @param citizensReady whether Citizens is serving ({@code CitizensAPI::hasImplementation})
     * @param tracker       last-interaction tracker shared with the listener
     * @param warn          warning sink
     */
    public CitizensNpcService(Supplier<NPCRegistry> registry, Supplier<NPCSelector> selector,
                              BooleanSupplier citizensReady, NpcInteractionTracker tracker, Consumer<String> warn) {
        this.registry = registry;
        this.selector = selector;
        this.citizensReady = citizensReady;
        this.tracker = tracker;
        this.warn = warn != null ? warn : message -> { };
    }

    @Override
    public boolean isAvailable() {
        try {
            return citizensReady.getAsBoolean() && registry.get() != null;
        } catch (RuntimeException | LinkageError e) {
            // Citizens disabled mid-session (PlugMan) or shut down.
            return false;
        }
    }

    @Override
    public String getProviderName() {
        return "Citizens";
    }

    @Override
    public Optional<NpcRef> findByKey(String rawKey) {
        String key = NpcKeys.normalize(rawKey);
        if (key == null || !isAvailable()) {
            return Optional.empty();
        }
        NPC npc = findNpc(key);
        return npc == null ? Optional.empty() : Optional.of(toRef(npc, key));
    }

    @Override
    public List<String> listKeys() {
        if (!isAvailable()) {
            return List.of();
        }
        TreeSet<String> keys = new TreeSet<>();
        for (NPC npc : registry.get()) {
            String key = keyOfNpc(npc);
            if (key != null) {
                keys.add(key);
            }
        }
        return List.copyOf(keys);
    }

    @Override
    public Optional<String> keyOf(Entity entity) {
        if (entity == null || !isAvailable()) {
            return Optional.empty();
        }
        NPC npc = registry.get().getNPC(entity);
        return npc == null ? Optional.empty() : Optional.ofNullable(keyOfNpc(npc));
    }

    @Override
    public TagResult tag(int backingId, String rawKey) {
        String key = NpcKeys.normalize(rawKey);
        if (key == null) {
            return TagResult.INVALID_KEY;
        }
        if (!isAvailable()) {
            return TagResult.UNAVAILABLE;
        }
        NPC target = registry.get().getById(backingId);
        if (target == null) {
            return TagResult.NPC_NOT_FOUND;
        }
        NPC holder = findNpc(key);
        if (holder != null && holder.getId() != target.getId()) {
            return TagResult.KEY_IN_USE;
        }
        String current = keyOfNpc(target);
        if (key.equals(current)) {
            return TagResult.TAGGED;
        }
        target.data().setPersistent(NpcKeys.METADATA_KEY, key);
        save();
        return current == null ? TagResult.TAGGED : TagResult.RETAGGED;
    }

    @Override
    public TagResult untag(String rawKey) {
        String key = NpcKeys.normalize(rawKey);
        if (key == null) {
            return TagResult.INVALID_KEY;
        }
        if (!isAvailable()) {
            return TagResult.UNAVAILABLE;
        }
        NPC holder = findNpc(key);
        if (holder == null) {
            return TagResult.KEY_NOT_FOUND;
        }
        holder.data().remove(NpcKeys.METADATA_KEY);
        save();
        return TagResult.UNTAGGED;
    }

    @Override
    public Optional<NpcInteraction> lastInteraction(UUID player) {
        return tracker.last(player);
    }

    @Override
    public OptionalInt selectedBackingId(CommandSender sender) {
        if (sender == null || !isAvailable()) {
            return OptionalInt.empty();
        }
        NPCSelector npcSelector = selector.get();
        NPC selected = npcSelector == null ? null : npcSelector.getSelected(sender);
        return selected == null ? OptionalInt.empty() : OptionalInt.of(selected.getId());
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    /**
     * Reads the RVNK key stored on a Citizens NPC.
     *
     * @return the normalised key, or null when the NPC has none or the stored value is not a valid key
     */
    static String keyOfNpc(NPC npc) {
        if (npc == null || npc.data() == null) {
            return null;
        }
        Object raw = npc.data().get(NpcKeys.METADATA_KEY);
        return raw == null ? null : NpcKeys.normalize(raw.toString());
    }

    /** @return the NPC's live location when spawned, else its stored location; may be null */
    static Location locationOf(NPC npc) {
        Entity entity = npc.isSpawned() ? npc.getEntity() : null;
        return entity != null ? entity.getLocation() : npc.getStoredLocation();
    }

    private NPC findNpc(String key) {
        for (NPC npc : registry.get()) {
            if (key.equals(keyOfNpc(npc))) {
                return npc;
            }
        }
        return null;
    }

    private static NpcRef toRef(NPC npc, String key) {
        Location location = locationOf(npc);
        return new NpcRef(key, npc.getName(), worldName(location), location, npc.getId(), npc.isSpawned());
    }

    private static String worldName(Location location) {
        if (location == null) {
            return null;
        }
        try {
            World world = location.getWorld();
            return world == null ? null : world.getName();
        } catch (IllegalArgumentException e) {
            // Location#getWorld throws "World unloaded" once the world reference is gone.
            return null;
        }
    }

    private void save() {
        try {
            registry.get().saveToStore();
        } catch (RuntimeException e) {
            warn.accept("NPC key change applied but Citizens could not save it now ("
                    + e.getMessage() + "); it saves at the next Citizens autosave");
        }
    }
}
