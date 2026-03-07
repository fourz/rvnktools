package org.fourz.rvnkcore.database.transaction;

import org.fourz.rvnkcore.api.exception.DatabaseException;
import org.fourz.rvnkcore.database.connection.ConnectionProvider;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Holds connection state for a database transaction.
 *
 * TransactionContext maintains a single connection in transaction mode (autoCommit=false)
 * and provides commit/rollback operations. This class is NOT thread-safe and should be
 * used within a single thread or properly synchronized.
 */
public class TransactionContext implements AutoCloseable {

    private final Connection connection;
    private final boolean originalAutoCommit;
    private boolean committed = false;
    private boolean rolledBack = false;

    /**
     * Creates a new transaction context from the given connection provider.
     *
     * @param connectionProvider The provider to get a connection from
     * @throws DatabaseException if transaction cannot be started
     */
    public TransactionContext(ConnectionProvider connectionProvider) {
        try {
            this.connection = connectionProvider.getConnection();
            this.originalAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
        } catch (SQLException e) {
            throw new DatabaseException("Failed to start transaction", e);
        }
    }

    /**
     * Gets the connection for this transaction.
     *
     * @return The transaction connection
     * @throws IllegalStateException if transaction is already completed
     */
    public Connection getConnection() {
        if (committed || rolledBack) {
            throw new IllegalStateException("Transaction already completed");
        }
        return connection;
    }

    /**
     * Commits the transaction.
     *
     * @throws DatabaseException if commit fails
     */
    public void commit() {
        if (committed || rolledBack) {
            throw new IllegalStateException("Transaction already completed");
        }
        try {
            connection.commit();
            committed = true;
        } catch (SQLException e) {
            throw new DatabaseException("Failed to commit transaction", e);
        }
    }

    /**
     * Rolls back the transaction.
     *
     * @throws DatabaseException if rollback fails
     */
    public void rollback() {
        if (committed || rolledBack) {
            return; // Silently ignore if already completed
        }
        try {
            connection.rollback();
            rolledBack = true;
        } catch (SQLException e) {
            throw new DatabaseException("Failed to rollback transaction", e);
        }
    }

    /**
     * Checks if this transaction has been committed.
     *
     * @return true if committed
     */
    public boolean isCommitted() {
        return committed;
    }

    /**
     * Checks if this transaction has been rolled back.
     *
     * @return true if rolled back
     */
    public boolean isRolledBack() {
        return rolledBack;
    }

    /**
     * Checks if this transaction is still active.
     *
     * @return true if not committed and not rolled back
     */
    public boolean isActive() {
        return !committed && !rolledBack;
    }

    /**
     * Closes the transaction context.
     *
     * If the transaction has not been committed, it will be rolled back.
     * The connection's autoCommit setting will be restored.
     */
    @Override
    public void close() {
        try {
            if (isActive()) {
                rollback();
            }
            connection.setAutoCommit(originalAutoCommit);
            connection.close();
        } catch (SQLException e) {
            throw new DatabaseException("Failed to close transaction context", e);
        }
    }
}
