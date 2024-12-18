package org.fourz.rvnktools.permission;

import net.luckperms.api.node.Node;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class PermissionService {

    public CompletableFuture<Void> addPermission(UUID playerUUID, String permission) {
        return LuckPermsManager.getLuckPerms().getUserManager().loadUser(playerUUID).thenAccept(user -> {
            Node node = Node.builder(permission).build();
            user.data().add(node);
            LuckPermsManager.getLuckPerms().getUserManager().saveUser(user);
        });
    }

    public CompletableFuture<Void> removePermission(UUID playerUUID, String permission) {
        return LuckPermsManager.getLuckPerms().getUserManager().loadUser(playerUUID).thenAccept(user -> {
            Node node = Node.builder(permission).build();
            user.data().remove(node);
            LuckPermsManager.getLuckPerms().getUserManager().saveUser(user);
        });
    }

    public CompletableFuture<Void> addGroup(UUID playerUUID, String group) {
        return LuckPermsManager.getLuckPerms().getUserManager().loadUser(playerUUID).thenAccept(user -> {
            Node groupNode = Node.builder("group." + group).build();
            user.data().add(groupNode);
            LuckPermsManager.getLuckPerms().getUserManager().saveUser(user);
        });
    }

    public CompletableFuture<Void> removeGroup(UUID playerUUID, String group) {
        return LuckPermsManager.getLuckPerms().getUserManager().loadUser(playerUUID).thenAccept(user -> {
            Node groupNode = Node.builder("group." + group).build();
            user.data().remove(groupNode);
            LuckPermsManager.getLuckPerms().getUserManager().saveUser(user);
        });
    }
}
