package org.fourz.rvnktools.Permission;

import net.luckperms.api.LuckPerms;
import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;

public class LuckPermsManager {

    private static LuckPerms luckPerms;

    // Singleton pattern to ensure only one instance
    private LuckPermsManager() { }

    // Initialize the LuckPerms API instance
    public static void init() {
        if (luckPerms == null) {
            RegisteredServiceProvider<LuckPerms> provider = Bukkit.getServicesManager().getRegistration(LuckPerms.class);
            if (provider != null) {
                luckPerms = provider.getProvider();
            } else {
                throw new IllegalStateException("LuckPerms not found! Make sure it's installed on the server.");
            }
        }
    }

    public static LuckPerms getLuckPerms() {
        if (luckPerms == null) {
            throw new IllegalStateException("LuckPerms API has not been initialized. Call LuckPermsManager.init() first.");
        }
        return luckPerms;
    }
}
