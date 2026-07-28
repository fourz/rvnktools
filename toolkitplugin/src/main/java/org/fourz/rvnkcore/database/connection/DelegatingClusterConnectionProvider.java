package org.fourz.rvnkcore.database.connection;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Default {@link ClusterConnectionProvider}, implemented as a thin delegate over an underlying
 * {@link ConnectionProvider} (#1796 Phase 2).
 *
 * <p>Two shapes, decided by role:</p>
 *
 * <table>
 *   <caption>Roles</caption>
 *   <tr><th>Role</th><th>Delegate</th><th>Owns the pool?</th></tr>
 *   <tr>
 *     <td>{@code authoritative}</td>
 *     <td>the server's primary provider</td>
 *     <td><b>No</b> — the cluster database <i>is</i> the local database, so this costs zero
 *         additional connections</td>
 *   </tr>
 *   <tr>
 *     <td>{@code member}</td>
 *     <td>a second {@code MySQLConnectionProvider} aimed at the authoritative server</td>
 *     <td><b>Yes</b></td>
 *   </tr>
 * </table>
 *
 * <p><b>Ownership is the point of this class.</b> {@link #close()} closes the delegate only when
 * this provider created it. An authoritative server borrows the primary pool, and closing it on
 * cluster shutdown would tear down the connection pool the entire plugin is still running on.</p>
 *
 * @since 1.5.33
 */
public class DelegatingClusterConnectionProvider implements ClusterConnectionProvider {

    private final ConnectionProvider delegate;
    private final boolean authoritative;
    private final boolean ownsDelegate;
    private final String target;

    private DelegatingClusterConnectionProvider(ConnectionProvider delegate,
                                                boolean authoritative,
                                                boolean ownsDelegate,
                                                String target) {
        this.delegate = delegate;
        this.authoritative = authoritative;
        this.ownsDelegate = ownsDelegate;
        this.target = target;
    }

    /**
     * Authoritative role — the cluster data lives in this server's own database.
     *
     * <p>Wraps the primary provider without opening anything new. The wrapper is deliberately kept
     * rather than registering the primary provider directly under the cluster key, so consumers can
     * always resolve {@link ClusterConnectionProvider} the same way regardless of role, and so
     * {@link #close()} stays a no-op for a pool we do not own.</p>
     *
     * @param primary the server's primary connection provider
     * @return a cluster provider backed by the local pool
     */
    public static ClusterConnectionProvider authoritative(ConnectionProvider primary) {
        return new DelegatingClusterConnectionProvider(
                primary, true, false, "local primary pool (authoritative)");
    }

    /**
     * Member role — the cluster data lives on another server.
     *
     * @param clusterPool a provider aimed at the authoritative server's database; this instance
     *                    takes ownership and will close it
     * @param target      human-readable host/database, for diagnostics
     * @return a cluster provider backed by its own pool
     */
    public static ClusterConnectionProvider member(ConnectionProvider clusterPool, String target) {
        return new DelegatingClusterConnectionProvider(
                clusterPool, false, true, target + " (member)");
    }

    @Override
    public Connection getConnection() throws SQLException {
        return delegate.getConnection();
    }

    @Override
    public boolean isValid() {
        return delegate != null && delegate.isValid();
    }

    @Override
    public String getDatabaseType() {
        return delegate.getDatabaseType();
    }

    @Override
    public boolean isAuthoritative() {
        return authoritative;
    }

    @Override
    public String describeTarget() {
        return target;
    }

    /**
     * Closes the underlying pool <b>only if this provider created it</b>.
     *
     * <p>On an authoritative server the delegate is the primary pool, still in use by every other
     * repository in the plugin. Closing it here would take the whole database layer down as a side
     * effect of shutting down a feature that never opened a connection of its own.</p>
     */
    @Override
    public void close() {
        if (ownsDelegate && delegate != null) {
            delegate.close();
        }
    }
}
