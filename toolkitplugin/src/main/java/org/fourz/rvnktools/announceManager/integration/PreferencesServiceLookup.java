package org.fourz.rvnktools.announceManager.integration;

import org.fourz.rvnkcore.RVNKCore;
import org.fourz.rvnkcore.api.service.PlayerPreferencesService;
import org.fourz.rvnkcore.service.registry.ServiceRegistry;
import org.fourz.rvnkcore.util.log.LogManager;
import org.bukkit.plugin.Plugin;

/**
 * Centralized access to PlayerPreferencesService via ServiceRegistry.
 * Handles initialization and availability checks for graceful degradation.
 */
public class PreferencesServiceLookup {
    private final LogManager logger;
    private PlayerPreferencesService preferencesService;
    private boolean serviceAvailable = false;

    public PreferencesServiceLookup(Plugin plugin) {
        this.logger = LogManager.getInstance(plugin, "PreferencesServiceLookup");
        initService();
    }

    /**
     * Initialize PlayerPreferencesService from RVNKCore's ServiceRegistry
     */
    private void initService() {
        try {
            RVNKCore core = RVNKCore.getInstance();
            if (core == null) {
                logger.warning("RVNKCore not initialized - preferences will use fallback");
                return;
            }

            ServiceRegistry registry = core.getServiceRegistry();
            this.preferencesService = registry.getService(PlayerPreferencesService.class);
            this.serviceAvailable = true;
            logger.info("PlayerPreferencesService integration enabled");
        } catch (Exception e) {
            logger.error("Failed to initialize PlayerPreferencesService", e);
        }
    }

    /**
     * Get the PlayerPreferencesService instance
     *
     * @return The service, or null if not available
     */
    public PlayerPreferencesService getService() {
        return preferencesService;
    }

    /**
     * Check if PlayerPreferencesService is available
     *
     * @return true if service is available and ready
     */
    public boolean isAvailable() {
        return serviceAvailable;
    }
}
