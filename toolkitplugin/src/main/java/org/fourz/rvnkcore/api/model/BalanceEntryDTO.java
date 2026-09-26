package org.fourz.rvnkcore.api.model;

import java.util.UUID;

/**
 * One leaderboard row from {@link org.fourz.rvnkcore.api.service.IEconomyService#getTopBalances(int)}.
 *
 * <p>{@code name} is <b>nullable</b>: null means the player is not in the roster
 * ({@code rvnk_players}) of the economy database, typically an account that has not joined since
 * player tracking began. Consumers choose their own display fallback; the contract does not pass a
 * UUID string off as a name (#2107, #1806).</p>
 *
 * @param uuid    the player's UUID, never null
 * @param name    the player's last known name, or null when unknown
 * @param balance the balance
 * @since 1.5.90-alpha
 */
public record BalanceEntryDTO(UUID uuid, String name, double balance) {
}
