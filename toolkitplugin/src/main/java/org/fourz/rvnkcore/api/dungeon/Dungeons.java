package org.fourz.rvnkcore.api.dungeon;

import org.fourz.rvnkcore.RVNKCore;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Resolves {@link IDungeonService} without null checks (#2368).
 *
 * <p>The service registry refuses a second registration of one interface, so RVNKCore cannot
 * pre-register a stand-in for RVNKWorlds to replace. Consumers call {@link #resolve()} instead and
 * get {@link #UNAVAILABLE} when no provider is registered.</p>
 *
 * @since 1.5.112-alpha
 */
public final class Dungeons {

    private Dungeons() { }

    /** A provider-less service: empty lookups, resets answer UNAVAILABLE, nothing throws. */
    public static final IDungeonService UNAVAILABLE = new IDungeonService() {
        @Override public boolean isAvailable() { return false; }
        @Override public Optional<DungeonInfo> find(String id) { return Optional.empty(); }
        @Override public List<DungeonInfo> list() { return List.of(); }
        @Override public Optional<DungeonInfo> dungeonAt(String world, int x, int y, int z) { return Optional.empty(); }
        @Override public DungeonState state(String id) { return DungeonState.UNKNOWN; }
        @Override public int remainingRoster(String id) { return 0; }
        @Override public Set<UUID> participants(String id) { return Set.of(); }
        @Override public CompletableFuture<ResetResult> reset(String id, ResetReason reason, String actor) {
            return CompletableFuture.completedFuture(
                    ResetResult.of(ResetResult.Status.UNAVAILABLE, "No dungeon provider (RVNKWorlds) is enabled"));
        }
    };

    /** The registered provider, or {@link #UNAVAILABLE}. */
    public static IDungeonService resolve() {
        IDungeonService svc = RVNKCore.getServiceSafe(IDungeonService.class);
        return svc != null ? svc : UNAVAILABLE;
    }
}
