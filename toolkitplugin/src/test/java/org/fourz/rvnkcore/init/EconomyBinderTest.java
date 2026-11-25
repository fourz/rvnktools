package org.fourz.rvnkcore.init;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Server;
import org.bukkit.event.server.ServiceRegisterEvent;
import org.bukkit.event.server.ServiceUnregisterEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.ServicesManager;
import org.fourz.rvnkcore.service.registry.ServiceRegistry;
import org.fourz.rvnkcore.util.log.LogManager;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * #2395: TokenEconomy enables after RVNKCore, so the Vault lookup at enable found no provider
 * and RVNKCore.getEconomy() stayed null for the whole session. The registry entry must follow
 * the provider as it registers and unregisters.
 */
class EconomyBinderTest {

    private static RegisteredServiceProvider<Economy> provider(Economy economy) {
        return new RegisteredServiceProvider<>(Economy.class, economy, ServicePriority.Normal, mock(Plugin.class));
    }

    @Test
    void followsTheProviderAsItRegistersAndLeaves() {
        Server server = mock(Server.class);
        ServicesManager sm = mock(ServicesManager.class);
        ServiceRegistry registry = mock(ServiceRegistry.class);
        when(server.getServicesManager()).thenReturn(sm);

        EconomyBinder binder = new EconomyBinder(server, registry, mock(LogManager.class));
        assertFalse(binder.bind(), "no provider at enable");
        verify(registry, never()).registerService(eq(Economy.class), any());

        Economy economy = mock(Economy.class);
        RegisteredServiceProvider<Economy> rsp = provider(economy);
        when(sm.getRegistration(Economy.class)).thenReturn(rsp);
        ServiceRegisterEvent registered = mock(ServiceRegisterEvent.class);
        doReturn(rsp).when(registered).getProvider();
        binder.onServiceRegister(registered);

        assertTrue(binder.isBound(), "bound after the provider registered");
        verify(registry).registerService(Economy.class, economy);

        when(sm.getRegistration(Economy.class)).thenReturn(null);
        ServiceUnregisterEvent unregistered = mock(ServiceUnregisterEvent.class);
        doReturn(rsp).when(unregistered).getProvider();
        binder.onServiceUnregister(unregistered);

        assertFalse(binder.isBound(), "unbound after the provider left");
        verify(registry).unregisterService(Economy.class);
    }

    @Test
    void replacesTheEntryWhenAHigherProviderRegisters() {
        Server server = mock(Server.class);
        ServicesManager sm = mock(ServicesManager.class);
        ServiceRegistry registry = mock(ServiceRegistry.class);
        when(server.getServicesManager()).thenReturn(sm);
        EconomyBinder binder = new EconomyBinder(server, registry, mock(LogManager.class));

        Economy first = mock(Economy.class);
        Economy second = mock(Economy.class);
        when(sm.getRegistration(Economy.class)).thenReturn(provider(first));
        assertTrue(binder.bind());
        assertTrue(binder.bind(), "same provider again is a no-op");
        verify(registry, times(1)).registerService(Economy.class, first);

        when(sm.getRegistration(Economy.class)).thenReturn(provider(second));
        assertTrue(binder.bind());
        verify(registry).unregisterService(Economy.class);
        verify(registry).registerService(Economy.class, second);
    }

    @Test
    void ignoresOtherServices() {
        Server server = mock(Server.class);
        ServiceRegistry registry = mock(ServiceRegistry.class);
        EconomyBinder binder = new EconomyBinder(server, registry, mock(LogManager.class));

        RegisteredServiceProvider<Runnable> other = new RegisteredServiceProvider<>(
                Runnable.class, mock(Runnable.class), ServicePriority.Normal, mock(Plugin.class));
        ServiceRegisterEvent registered = mock(ServiceRegisterEvent.class);
        doReturn(other).when(registered).getProvider();
        binder.onServiceRegister(registered);

        verifyNoInteractions(server, registry);
    }
}
