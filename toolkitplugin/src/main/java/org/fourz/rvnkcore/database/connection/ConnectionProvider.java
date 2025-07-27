package org.fourz.rvnkcore.database.connection;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Interface for providing database connections to the RVNKCore framework.
 * 
 * Implementations of this interface handle the specifics of connection
 * management for different database types (SQLite, MySQL, etc.) while
 * providing a unified interface for the rest of the framework.
 */
public interface ConnectionProvider extends AutoCloseable {
    
    /**
     * Gets a database connection.
     * 
     * For pooled connections, this will return a connection from the pool.
     * For single-connection databases like SQLite, this will return the
     * shared connection instance.
     * 
     * @return A database connection ready for use
     * @throws SQLException If a connection cannot be obtained
     */
    Connection getConnection() throws SQLException;
    
    /**
     * Validates that the connection provider is still operational.
     * 
     * This method should check if the underlying connection mechanism
     * is still functional and can provide valid connections.
     * 
     * @return true if the provider can supply valid connections, false otherwise
     */
    boolean isValid();
    
    /**
     * Gets the type of database this provider manages.
     * 
     * @return A string identifying the database type (e.g., "mysql", "sqlite")
     */
    String getDatabaseType();
    
    /**
     * Closes all connections and cleans up resources.
     * 
     * This method should be called when the connection provider is no longer
     * needed, typically during plugin shutdown.
     */
    @Override
    void close();
}
