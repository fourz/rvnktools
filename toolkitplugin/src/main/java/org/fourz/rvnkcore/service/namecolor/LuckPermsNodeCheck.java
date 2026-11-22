package org.fourz.rvnkcore.service.namecolor;

import net.luckperms.api.LuckPerms;
import org.fourz.rvnktools.permission.LuckPermsManager;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Checks one permission node in LuckPerms by UUID, so it works for players who are offline (#2468).
 *
 * <p>Kept in its own class: it is only loaded after {@code isPluginEnabled("LuckPerms")} is true, so no LuckPerms
 * class loads on a server without it.</p>
 */
final class LuckPermsNodeCheck {

    private LuckPermsNodeCheck() {}

    static CompletableFuture<Boolean> has(UUID player, String node) {
        LuckPermsManager.init();
        LuckPerms lp = LuckPermsManager.getLuckPerms();
        return lp.getUserManager().loadUser(player)
                .thenApply(u -> u.getCachedData().getPermissionData().checkPermission(node).asBoolean());
    }
}
