package org.fourz.rvnkcore.api.service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Service contract for economy operations across the RVNK plugin ecosystem.
 *
 * Registered in RVNKCore's ServiceRegistry by TokenEconomy when integration is
 * enabled. Other RVNK plugins retrieve it via:
 * <pre>
 *     IEconomyService economy = serviceRegistry.getService(IEconomyService.class);
 * </pre>
 *
 * Exposes operations not available through Vault. For standard balance queries
 * and transfers, use Vault's Economy API directly.
 *
 * All operations are asynchronous to prevent blocking the main thread.
 *
 * @since 1.3.5-alpha
 */
public interface IEconomyService {

    /**
     * Sets a player's balance to an absolute value.
     * Creates the player's account if it does not exist.
     *
     * @param playerId the player's UUID
     * @param amount   the new balance (must be non-negative)
     * @return CompletableFuture containing true if successful
     */
    CompletableFuture<Boolean> setBalance(UUID playerId, double amount);

    /**
     * Gets the top balances ordered descending by balance.
     *
     * @param limit maximum number of entries to return
     * @return CompletableFuture containing a map of player names to balances
     */
    CompletableFuture<Map<String, Double>> getTopBalances(int limit);
}
