package org.fourz.rvnktools.permission;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.cacheddata.CachedPermissionData;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.model.user.User;
import net.luckperms.api.query.QueryOptions;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Shared utility for resolving LuckPerms groups for a player.
 * Eliminates duplicate group-resolution logic across LinkCommand,
 * LuckPermsIntegrationListener, and other consumers.
 *
 * @since 1.5.1
 */
public final class LuckPermsGroupResolver {

    private LuckPermsGroupResolver() { }

    /**
     * Result of a group resolution: primary group + all inherited groups.
     */
    public record GroupResult(String primaryGroup, List<String> allGroups) { }

    /**
     * Resolves groups for an online player (fast path, no async load).
     *
     * @param player the online player
     * @return group result with primary first in allGroups
     */
    public static GroupResult resolveGroups(Player player) {
        LuckPerms lp = LuckPermsManager.getLuckPerms();
        User user = lp.getPlayerAdapter(Player.class).getUser(player);
        return extractGroups(user);
    }

    /**
     * Resolves groups for a LuckPerms User that is already loaded.
     *
     * @param user the LuckPerms user
     * @return group result with primary first in allGroups
     */
    public static GroupResult resolveGroups(User user) {
        return extractGroups(user);
    }

    /**
     * Loads and resolves groups for any player by UUID (async).
     * Works for both online and offline players.
     *
     * @param uuid the player UUID
     * @return future containing the group result
     */
    public static CompletableFuture<GroupResult> resolveGroupsAsync(UUID uuid) {
        LuckPerms lp = LuckPermsManager.getLuckPerms();
        return lp.getUserManager().loadUser(uuid)
                .thenApply(LuckPermsGroupResolver::extractGroups);
    }

    /**
     * Checks a list of permissions for a player by UUID (async).
     * Uses {@link QueryOptions#nonContextual()} so inherited group permissions
     * resolve correctly for offline players.
     *
     * @param uuid            the player UUID
     * @param permissionNodes permission strings to check
     * @return future containing a map of permission node to boolean result
     */
    public static CompletableFuture<Map<String, Boolean>> checkPermissionsAsync(
            UUID uuid, List<String> permissionNodes) {
        LuckPerms lp = LuckPermsManager.getLuckPerms();
        return lp.getUserManager().loadUser(uuid)
                .thenApply(user -> checkPermissions(user, permissionNodes));
    }

    /**
     * Checks a list of permissions for an already-loaded LuckPerms user.
     * Uses {@link QueryOptions#nonContextual()} so inherited group permissions
     * resolve correctly regardless of online/offline state.
     *
     * @param user            the LuckPerms user
     * @param permissionNodes permission strings to check
     * @return map of permission node to boolean result (iteration order preserved)
     */
    public static Map<String, Boolean> checkPermissions(User user, List<String> permissionNodes) {
        CachedPermissionData permData = user.getCachedData()
                .getPermissionData(QueryOptions.nonContextual());
        Map<String, Boolean> results = new LinkedHashMap<>();
        for (String node : permissionNodes) {
            results.put(node, permData.checkPermission(node).asBoolean());
        }
        return results;
    }

    private static GroupResult extractGroups(User user) {
        String primaryGroup = user.getPrimaryGroup();
        List<String> allGroups = new ArrayList<>();
        allGroups.add(primaryGroup);

        user.getInheritedGroups(user.getQueryOptions()).stream()
                .map(Group::getName)
                .filter(name -> !name.equals(primaryGroup))
                .forEach(allGroups::add);

        return new GroupResult(primaryGroup, allGroups);
    }
}
