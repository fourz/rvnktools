package org.fourz.rvnkcore.api.service;

import org.fourz.rvnkcore.api.model.BalanceEntryDTO;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Economy operations that Vault cannot express, for the RVNK plugin ecosystem.
 *
 * <p>Provided by TokenEconomy 1.2.8 and later, which registers it in RVNKCore's ServiceRegistry on
 * enable and unregisters it on disable. It may be absent (TokenEconomy not installed, or an older
 * version), so resolve it defensively:</p>
 * <pre>
 *     IEconomyService economy = registry.hasService(IEconomyService.class)
 *             ? registry.getService(IEconomyService.class) : null;
 * </pre>
 *
 * <p>Balance reads, deposits, withdrawals and transfers belong to Vault, which every tier has. This
 * contract deliberately does not duplicate them (#2107).</p>
 *
 * <p><b>Failure semantics.</b> Expected conditions (a refused write, the economy database being
 * unreachable, a bad argument) complete <i>normally</i> with {@code false} or an empty list. Futures
 * never complete exceptionally for them, so a consumer's {@code thenAccept} chain is safe. Use
 * {@link #isAvailable()} to tell "refused" from "economy offline".</p>
 *
 * @since 1.3.5-alpha
 */
public interface IEconomyService {

    /**
     * Sets a player's balance to an absolute value, creating the account if it does not exist.
     * Works for offline players: it takes a UUID and writes directly.
     *
     * @param playerId the player's UUID
     * @param amount   the new balance; negative, NaN or infinite values are refused
     * @return true when written; false when refused or the economy is unavailable
     */
    CompletableFuture<Boolean> setBalance(UUID playerId, double amount);

    /**
     * Gets the highest balances, ordered descending by balance.
     *
     * @param limit maximum number of entries to return
     * @return the ordered entries; empty when the economy is unavailable
     * @since 1.5.90-alpha (previously returned a name-keyed map, which lost order and UUIDs)
     */
    CompletableFuture<List<BalanceEntryDTO>> getTopBalances(int limit);

    /**
     * Whether the economy database is currently reachable and accepting writes. Synchronous and
     * I/O-free: it reads a flag, so it is safe on the main thread.
     *
     * @return false while the provider is refusing every operation
     * @since 1.5.90-alpha
     */
    boolean isAvailable();
}
