package org.fourz.rvnktools.permission;

import net.luckperms.api.LuckPerms;
import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;

/**
 * Manager for LuckPerms API access.
 *
 * <p>Note: This class uses static singleton pattern for legacy compatibility.
 * Future refactoring should convert to instance-based service registered
 * in ServiceRegistry for proper dependency injection.</p>
 *
 * @since 1.0.0
 */
public class LuckPermsManager {

    private static volatile LuckPerms luckPerms;
    private static final Object lock = new Object();

    // Singleton pattern to ensure only one instance
    private LuckPermsManager() { }

    /**
     * Initialize the LuckPerms API instance.
     *
     * <p>Thread-safe initialization using synchronized block.</p>
     */
    public static void init() {
        if (luckPerms == null) {
            synchronized (lock) {
                if (luckPerms == null) {
                    RegisteredServiceProvider<LuckPerms> provider = Bukkit.getServicesManager().getRegistration(LuckPerms.class);
                    if (provider != null) {
                        luckPerms = provider.getProvider();
                    } else {
                        throw new IllegalStateException("LuckPerms not found! Make sure it's installed on the server.");
                    }
                }
            }
        }
    }

    /**
     * Gets the LuckPerms API instance.
     *
     * @return LuckPerms API
     * @throws IllegalStateException if not initialized
     */
    public static LuckPerms getLuckPerms() {
        if (luckPerms == null) {
            throw new IllegalStateException("LuckPerms API has not been initialized. Call LuckPermsManager.init() first.");
        }
        return luckPerms;
    }
}
