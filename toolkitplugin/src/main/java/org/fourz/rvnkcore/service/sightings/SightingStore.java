package org.fourz.rvnkcore.service.sightings;

import java.sql.SQLException;
import java.util.List;

/** Per-server sighting rows (#2351). Every call may block on the database: never call it on the main thread. */
public interface SightingStore {

    /** One page read after a cursor. */
    record Page(List<Sighting> sightings, boolean more, long oldestId) { }

    /** Stores the sighting and returns its id. */
    long append(Sighting sighting) throws SQLException;

    /** Rows with {@code id > after}, oldest first, at most {@code limit}; {@code more} when further rows wait. */
    Page after(long after, int limit) throws SQLException;

    /**
     * Deletes rows older than {@code beforeMillis}, but never the newest row, so the id sequence
     * cannot restart below a cursor z-office already holds.
     *
     * @return rows deleted
     */
    int prune(long beforeMillis) throws SQLException;
}
