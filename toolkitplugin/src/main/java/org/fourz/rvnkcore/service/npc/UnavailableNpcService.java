package org.fourz.rvnkcore.service.npc;

import org.bukkit.entity.Entity;
import org.fourz.rvnkcore.api.model.NpcInteraction;
import org.fourz.rvnkcore.api.model.NpcRef;
import org.fourz.rvnkcore.api.service.INpcService;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The {@link INpcService} registered when no NPC plugin is available (#2213).
 *
 * <p>Registering this instead of nothing means a consumer can always resolve the service and ask
 * {@link #isAvailable()}, and a forgotten check degrades to "no NPC found" instead of a
 * {@code NullPointerException}. Nothing here throws.</p>
 *
 * @since 1.5.99-alpha
 */
public class UnavailableNpcService implements INpcService {

    private final String reason;

    /**
     * @param reason why the bridge is unavailable, shown by {@code /rvnk npc} and in the log
     */
    public UnavailableNpcService(String reason) {
        this.reason = reason == null ? "no NPC plugin" : reason;
    }

    /** @return why the bridge is unavailable */
    public String getReason() {
        return reason;
    }

    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public String getProviderName() {
        return "none";
    }

    @Override
    public Optional<NpcRef> findByKey(String key) {
        return Optional.empty();
    }

    @Override
    public List<String> listKeys() {
        return List.of();
    }

    @Override
    public Optional<String> keyOf(Entity entity) {
        return Optional.empty();
    }

    @Override
    public TagResult tag(int backingId, String key) {
        return TagResult.UNAVAILABLE;
    }

    @Override
    public TagResult untag(String key) {
        return TagResult.UNAVAILABLE;
    }

    @Override
    public Optional<NpcInteraction> lastInteraction(UUID player) {
        return Optional.empty();
    }
}
