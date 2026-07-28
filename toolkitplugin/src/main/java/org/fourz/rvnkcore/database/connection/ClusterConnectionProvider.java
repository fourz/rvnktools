package org.fourz.rvnkcore.database.connection;

/**
 * Connection provider for <b>cluster-shared</b> tables — data that is one set network-wide rather
 * than one set per server (#1796 Phase 2).
 *
 * <p>This interface adds no methods. It exists purely as a <b>distinct type</b> so it can be
 * registered under its own key in the ServiceRegistry, which is keyed by {@code Class} and
 * therefore holds exactly one {@link ConnectionProvider} per JVM. Without a separate type, a
 * second pool could not be published to dependent plugins at all.</p>
 *
 * <p>Resolve it the same way as the primary provider:</p>
 * <pre>{@code
 * ClusterConnectionProvider cluster = core.getService(ClusterConnectionProvider.class);
 * }</pre>
 *
 * <p><b>It is absent when clustering is off.</b> Consumers must handle a null/missing service and
 * fall back to the primary provider — that is the normal single-server configuration, not an
 * error.</p>
 *
 * <h2>What may and may not live here</h2>
 *
 * <p>Only tables with <b>no foreign key into per-server data</b> may be served by this provider.
 * {@code rvnk_announcements} and {@code rvnk_announcement_types} qualify: they are self-contained
 * content with no FKs at all.</p>
 *
 * <p>{@code rvnk_player_preferences} does <b>not</b> qualify, despite being conceptually
 * server-agnostic. It carries
 * {@code FOREIGN KEY (player_id) REFERENCES rvnk_players(id)}, and {@code rvnk_players} stays
 * per-server. Relocating preferences alone would point that FK at the authoritative server's
 * roster, so writing preferences for a player who has never joined that server would fail the
 * constraint. Sharing preferences requires a decision about {@code rvnk_players} first.</p>
 *
 * <p>This is the same structural constraint found in Phase 1, where TokenEconomy's leaderboard
 * join forced the economy tables to be co-located with {@code rvnk_players}. The player identity
 * table is the gravitational centre: anything player-scoped that moves to the cluster drags it
 * along.</p>
 *
 * <p><b>Never cluster-shared:</b> {@code rvnk_players}, {@code rvnk_worlds},
 * {@code rvnk_player_world_data}, {@code rvnk_cross_server_portal}.</p>
 *
 * <h2>No cross-database transactions</h2>
 *
 * <p>{@code TransactionManager} takes a single provider, so a local write and a cluster write are
 * two independent operations with no shared atomicity. Consumers must be written to tolerate one
 * succeeding without the other.</p>
 *
 * @since 1.5.33
 */
public interface ClusterConnectionProvider extends ConnectionProvider {

    /**
     * Whether this server owns the cluster database (authoritative) or connects out to one
     * (member).
     *
     * @return true when this server hosts the cluster database
     */
    boolean isAuthoritative();

    /**
     * Human-readable description of what this provider is pointed at, for diagnostics.
     *
     * @return e.g. {@code "local primary pool (authoritative)"} or {@code "host/database (member)"}
     */
    String describeTarget();
}
