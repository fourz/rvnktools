package org.fourz.rvnkcore.service.kit;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/**
 * Kit definitions (#2415). Every call may block on the database: never call it on the main thread.
 */
public interface KitStore {

    /** Every kit, by name, without payloads. */
    List<Kit> list() throws SQLException;

    Optional<Kit> get(String name) throws SQLException;

    /** Inserts, or replaces and bumps the version. @return the stored kit's version */
    int save(Kit kit) throws SQLException;

    /** @return true when a kit was deleted */
    boolean delete(String name) throws SQLException;
}
