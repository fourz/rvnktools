package org.fourz.rvnkcore.service.npc.citizens;

import net.citizensnpcs.api.CitizensAPI;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.fourz.rvnkcore.api.service.INpcService;
import org.fourz.rvnkcore.service.npc.NpcClickDispatcher;
import org.fourz.rvnkcore.service.npc.NpcInteractionTracker;
import org.fourz.rvnkcore.service.npc.harness.NpcHarness;
import org.fourz.rvnkcore.util.log.LogManager;

/**
 * Entry point into the Citizens adapter (#2213).
 *
 * <p>Only {@link org.fourz.rvnkcore.service.npc.NpcBridge} calls this, and only after
 * {@code isPluginEnabled("Citizens")} returned true. The signature names no Citizens type, so the
 * caller can be verified and loaded without Citizens on the classpath.</p>
 *
 * @since 1.5.99-alpha
 */
public final class CitizensNpcAdapter {

    private CitizensNpcAdapter() {
    }

    /**
     * Builds the Citizens-backed service and registers the click listener.
     *
     * @param plugin     RVNKCore, which owns the listener
     * @param tracker    the shared last-interaction tracker
     * @param dispatcher fires {@code RvnkNpcInteractEvent}; shared with {@code /rvnk npc click} (#2255)
     * @param logger     RVNKCore's logger
     * @return the service
     */
    public static INpcService start(Plugin plugin, NpcInteractionTracker tracker, NpcClickDispatcher dispatcher,
                                    LogManager logger) {
        CitizensNpcService service = new CitizensNpcService(
                CitizensAPI::getNPCRegistry,
                CitizensAPI::getDefaultNPCSelector,
                CitizensAPI::hasImplementation,
                tracker,
                logger::warning);

        CitizensNpcListener listener = new CitizensNpcListener(dispatcher, logger::debug);
        Bukkit.getPluginManager().registerEvents(listener, plugin);
        return service;
    }

    /**
     * Builds the Citizens-backed write side used by {@code /rvnk npc create|move|apply|...} (#2248).
     * Same rule as {@link #start}: the signature names no Citizens type.
     *
     * @param plugin RVNKCore, which owns the async skin tasks
     * @param logger RVNKCore's logger
     * @return the harness
     */
    public static NpcHarness harness(Plugin plugin, LogManager logger) {
        return new CitizensNpcHarness(CitizensAPI::getNPCRegistry, plugin, logger::warning);
    }
}
