package org.fourz.rvnkcore.service.whitelist.luckperms;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.data.DataMutateResult;
import net.luckperms.api.node.types.InheritanceNode;
import org.fourz.rvnkcore.service.whitelist.WhitelistMembership.GroupAssigner;
import org.fourz.rvnkcore.service.whitelist.WhitelistMembership.GroupResult;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Adds a LuckPerms inheritance node by UUID; works for players who never joined (#2346).
 *
 * <p>Only constructed after {@code isPluginEnabled("LuckPerms")} is true, so no LuckPerms class loads
 * on a server without it. Never removes a group: re-running is safe.</p>
 */
public class LuckPermsGroupAssigner implements GroupAssigner {

    private final LuckPerms luckPerms;

    public LuckPermsGroupAssigner(LuckPerms luckPerms) {
        this.luckPerms = luckPerms;
    }

    @Override
    public CompletableFuture<GroupResult> addGroup(UUID uuid, String group) {
        return luckPerms.getGroupManager().loadGroup(group).thenCompose(found -> {
            if (found.isEmpty()) return CompletableFuture.completedFuture(GroupResult.GROUP_NOT_FOUND);
            AtomicReference<DataMutateResult> result = new AtomicReference<>();
            return luckPerms.getUserManager()
                .modifyUser(uuid, user -> result.set(user.data().add(InheritanceNode.builder(group).build())))
                .thenApply(v -> toResult(result.get()));
        });
    }

    /** Maps a LuckPerms mutate result; public for tests. */
    public static GroupResult toResult(DataMutateResult r) {
        if (r == null) return GroupResult.FAILED;
        if (r.wasSuccessful()) return GroupResult.ADDED;
        return r == DataMutateResult.FAIL_ALREADY_HAS ? GroupResult.ALREADY_MEMBER : GroupResult.FAILED;
    }
}
