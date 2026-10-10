package org.fourz.rvnkcore.service.npc.citizens;

import net.citizensnpcs.api.npc.MetadataStore;
import net.citizensnpcs.api.util.DataKey;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * In-memory {@link MetadataStore} for tests.
 *
 * <p>Citizens' own {@code SimpleMetadataStore} cannot run here: its constructor initialises
 * {@code NPC.Metadata}, whose static block needs the Adventure classes Citizens shades into the
 * plugin jar. This fake keeps Citizens' semantics that matter to the bridge: {@code setPersistent}
 * marks an entry for saves.yml, {@code set} does not, and {@code remove} drops either kind.</p>
 */
final class FakeMetadataStore implements MetadataStore {

    private final Map<String, Object> values = new HashMap<>();
    private final Set<String> persistent = new HashSet<>();

    boolean isPersistent(String key) {
        return persistent.contains(key);
    }

    @Override
    public MetadataStore clone() {
        FakeMetadataStore copy = new FakeMetadataStore();
        copy.values.putAll(values);
        copy.persistent.addAll(persistent);
        return copy;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T get(String key) {
        return (T) values.get(key);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T get(String key, T def) {
        return values.containsKey(key) ? (T) values.get(key) : def;
    }

    @Override
    public boolean has(String key) {
        return values.containsKey(key);
    }

    @Override
    public void loadFrom(DataKey key) {
        throw new UnsupportedOperationException("not used by the bridge");
    }

    @Override
    public void remove(String key) {
        values.remove(key);
        persistent.remove(key);
    }

    @Override
    public void saveTo(DataKey key) {
        throw new UnsupportedOperationException("not used by the bridge");
    }

    @Override
    public void set(String key, Object value) {
        if (value == null) {
            remove(key);
            return;
        }
        values.put(key, value);
        persistent.remove(key);
    }

    @Override
    public void setPersistent(String key, Object value) {
        if (value == null) {
            remove(key);
            return;
        }
        values.put(key, value);
        persistent.add(key);
    }

    @Override
    public int size() {
        return values.size();
    }
}
