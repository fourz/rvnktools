package org.fourz.rvnkcore.service.sightings;

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

/**
 * Entitled lists per player (#2351), for example {@code mc-dev}. Game state: a quest grants it. On a
 * clustered server the table is cluster-shared, so a grant on Event is seen on prod. Every call may
 * block on the database: never call it on the main thread.
 */
public interface EntitlementStore {

    /** The player's lists, sorted. */
    List<String> lists(UUID uuid) throws SQLException;

    /** @return true when the player did not have the list before */
    boolean grant(UUID uuid, String list, String by) throws SQLException;

    /** @return true when the player had the list */
    boolean revoke(UUID uuid, String list) throws SQLException;
}
