package org.fourz.rvnkcore.init;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Server;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerLoadEvent;
import org.bukkit.event.server.ServiceRegisterEvent;
import org.bukkit.event.server.ServiceUnregisterEvent;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.fourz.rvnkcore.service.registry.ServiceRegistry;
import org.fourz.rvnkcore.util.log.LogManager;

/**
 * Keeps the {@link Economy} entry in the ServiceRegistry in step with Vault.
 *
 * <p>TokenEconomy soft-depends on RVNKCore, so it always enables after RVNKCore and a lookup
 * at enable finds no provider. Binding on {@link ServiceRegisterEvent} picks the provider up
 * when it arrives (#2395). Adding TokenEconomy to RVNKCore's softdepend is not an option: the
 * two would each wait for the other.</p>
 *
 * <p>Only constructed when Vault is present - the handlers reference {@link Economy}.</p>
 */
public class EconomyBinder implements Listener {

    private final Server server;
    private final ServiceRegistry registry;
    private final LogManager logger;
    private Economy bound;

    public EconomyBinder(Server server, ServiceRegistry registry, LogManager logger) {
        this.server = server;
        this.registry = registry;
        this.logger = logger;
    }

    /**
     * Look up the current Vault economy provider and register it, replacing any older one.
     * @return true when a provider is bound
     */
    synchronized boolean bind() {
        RegisteredServiceProvider<Economy> rsp = server.getServicesManager().getRegistration(Economy.class);
        Economy provider = rsp == null ? null : rsp.getProvider();

        if (provider == bound) {
            return provider != null;
        }
        if (bound != null) {
            registry.unregisterService(Economy.class);
        }
        bound = provider;
        if (provider == null) {
            logger.warning("Economy provider unregistered - economy features disabled");
            return false;
        }
        registry.registerService(Economy.class, provider);
        logger.info("Economy integration enabled (" + provider.getName() + ")");
        return true;
    }

    synchronized boolean isBound() {
        return bound != null;
    }

    @EventHandler
    public void onServiceRegister(ServiceRegisterEvent event) {
        if (event.getProvider().getService() == Economy.class) {
            bind();
        }
    }

    @EventHandler
    public void onServiceUnregister(ServiceUnregisterEvent event) {
        if (event.getProvider().getService() == Economy.class) {
            bind();
        }
    }

    /** All plugins are enabled now; a missing provider here is a real gap, not load order. */
    @EventHandler
    public void onServerLoad(ServerLoadEvent event) {
        if (!isBound()) {
            logger.info("Economy not found - economy features disabled");
        }
    }
}
