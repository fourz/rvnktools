package org.fourz.rvnkcore.database.transaction;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Functional interface for operations executed within a transaction.
 *
 * @param <T> The return type of the operation
 */
@FunctionalInterface
public interface TransactionalOperation<T> {

    /**
     * Executes the operation within the transaction.
     *
     * @param connection The transaction connection (autoCommit=false)
     * @return The operation result
     * @throws SQLException if the operation fails
     */
    T execute(Connection connection) throws SQLException;
}
