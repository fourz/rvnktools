package org.fourz.rvnkcore.database.transaction;

import org.fourz.rvnkcore.api.exception.DatabaseException;
import org.fourz.rvnkcore.database.connection.ConnectionProvider;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Manages database transactions for RVNKCore.
 *
 * Provides methods for executing operations within transaction boundaries
 * with automatic commit on success and rollback on failure.
 *
 * <p>Example usage:
 * <pre>{@code
 * TransactionManager txManager = new TransactionManager(connectionProvider);
 *
 * // Synchronous transaction
 * String result = txManager.executeInTransaction(conn -> {
 *     // perform database operations
 *     return "success";
 * });
 *
 * // Async transaction
 * CompletableFuture<String> future = txManager.executeInTransactionAsync(conn -> {
 *     // perform database operations
 *     return "success";
 * });
 * }</pre>
 */
public class TransactionManager {

    private final ConnectionProvider connectionProvider;

    /**
     * Creates a new TransactionManager.
     *
     * @param connectionProvider The connection provider for database access
     */
    public TransactionManager(ConnectionProvider connectionProvider) {
        this.connectionProvider = connectionProvider;
    }

    /**
     * Executes an operation within a transaction boundary.
     *
     * The operation will be committed on success or rolled back on exception.
     *
     * @param operation The operation to execute
     * @param <T> The return type
     * @return The result of the operation
     * @throws DatabaseException if the operation fails
     */
    public <T> T executeInTransaction(TransactionalOperation<T> operation) {
        try (TransactionContext tx = new TransactionContext(connectionProvider)) {
            T result = operation.execute(tx.getConnection());
            tx.commit();
            return result;
        } catch (SQLException e) {
            throw new DatabaseException("Transaction failed", e);
        }
    }

    /**
     * Executes an operation within a transaction boundary asynchronously.
     *
     * @param operation The operation to execute
     * @param <T> The return type
     * @return CompletableFuture containing the result
     */
    public <T> CompletableFuture<T> executeInTransactionAsync(TransactionalOperation<T> operation) {
        return CompletableFuture.supplyAsync(() -> executeInTransaction(operation));
    }

    /**
     * Executes a void operation within a transaction boundary.
     *
     * @param operation The operation to execute (uses Connection parameter)
     * @throws DatabaseException if the operation fails
     */
    public void executeInTransaction(Consumer<Connection> operation) {
        executeInTransaction(conn -> {
            operation.accept(conn);
            return null;
        });
    }

    /**
     * Executes a void operation within a transaction boundary asynchronously.
     *
     * @param operation The operation to execute
     * @return CompletableFuture that completes when done
     */
    public CompletableFuture<Void> executeInTransactionAsync(Consumer<Connection> operation) {
        return CompletableFuture.runAsync(() -> executeInTransaction(operation));
    }

    /**
     * Creates a new transaction context for manual transaction management.
     *
     * Use this method when you need more control over transaction boundaries.
     * Remember to call commit() or the transaction will be rolled back on close.
     *
     * <p>Example usage:
     * <pre>{@code
     * try (TransactionContext tx = txManager.beginTransaction()) {
     *     Connection conn = tx.getConnection();
     *     // perform operations
     *     tx.commit();
     * } // auto-rollback if not committed
     * }</pre>
     *
     * @return A new transaction context
     */
    public TransactionContext beginTransaction() {
        return new TransactionContext(connectionProvider);
    }

    /**
     * Runs multiple operations in a single transaction.
     *
     * All operations share the same connection and are committed together.
     *
     * @param operations The operations to execute
     * @throws DatabaseException if any operation fails (all are rolled back)
     */
    @SafeVarargs
    public final void executeAllInTransaction(Consumer<Connection>... operations) {
        executeInTransaction(conn -> {
            for (Consumer<Connection> op : operations) {
                op.accept(conn);
            }
            return null;
        });
    }

    /**
     * Runs multiple operations in a single transaction asynchronously.
     *
     * @param operations The operations to execute
     * @return CompletableFuture that completes when all operations are done
     */
    @SafeVarargs
    public final CompletableFuture<Void> executeAllInTransactionAsync(Consumer<Connection>... operations) {
        return CompletableFuture.runAsync(() -> executeAllInTransaction(operations));
    }
}
