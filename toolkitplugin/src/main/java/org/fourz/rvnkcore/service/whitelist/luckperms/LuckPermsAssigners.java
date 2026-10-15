package org.fourz.rvnkcore.service.whitelist.luckperms;

import org.fourz.rvnkcore.service.whitelist.WhitelistMembership.GroupAssigner;
import org.fourz.rvnktools.permission.LuckPermsManager;

/**
 * Entry point into this package. Callers reach it only after {@code isPluginEnabled("LuckPerms")},
 * so no LuckPerms type is resolved on a server without LuckPerms.
 */
public final class LuckPermsAssigners {

    private LuckPermsAssigners() { }

    /** Builds the assigner on the shared LuckPerms API instance. */
    public static GroupAssigner create() {
        LuckPermsManager.init();
        return new LuckPermsGroupAssigner(LuckPermsManager.getLuckPerms());
    }
}
