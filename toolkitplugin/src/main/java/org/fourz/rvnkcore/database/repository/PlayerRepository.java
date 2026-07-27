package org.fourz.rvnkcore.database.repository;

import org.fourz.rvnkcore.api.model.PlayerDTO;
import org.fourz.rvnkcore.database.connection.ConnectionProvider;
import org.fourz.rvnkcore.database.query.QueryBuilder;
import org.bukkit.plugin.Plugin;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Repository implementation for Player data operations.
 * 
 * Provides database access methods for player information including
 * activity tracking, location data, name history, and permission groups.
 * 
 * @since 1.0.0
 */
public class PlayerRepository extends BaseRepository<PlayerDTO, UUID> {
    
    private static final String TABLE_NAME = "rvnk_players";
    
    /**
     * Constructor for PlayerRepository.
     * 
     * @param connectionProvider The database connection provider
     * @param queryBuilder The query builder for database operations
     * @param plugin The plugin instance for logging
     */
    public PlayerRepository(ConnectionProvider connectionProvider,
                          QueryBuilder queryBuilder,
                          Plugin plugin) {
        this(connectionProvider, queryBuilder, plugin, "local");
    }

    /**
     * Constructor with an explicit server identity (#1811).
     *
     * @param connectionProvider The database connection provider
     * @param queryBuilder The query builder for database operations
     * @param plugin The plugin instance for logging
     * @param serverId This server's identity, used to scope per-server activity rows
     */
    public PlayerRepository(ConnectionProvider connectionProvider,
                          QueryBuilder queryBuilder,
                          Plugin plugin,
                          String serverId) {
        super(connectionProvider, queryBuilder, TABLE_NAME, PlayerDTO.class, plugin);
        this.serverId = (serverId == null || serverId.isBlank()) ? "local" : serverId;
    }

    /** This server's identity, scoping rows in {@code rvnk_player_server_state}. */
    private final String serverId;

    /**
     * Saves the player, then mirrors the per-server activity columns into
     * {@code rvnk_player_server_state} (#1811).
     *
     * <p><b>Dual-write, deliberately.</b> This phase is additive: {@code rvnk_players} keeps its
     * {@code current_world}, {@code times_joined}, {@code total_playtime_hours} and
     * {@code last_seen} columns, and every read still comes from them, so behaviour is unchanged.
     * The mirror simply accumulates so that #1812 can switch reads over against data that is
     * already warm, rather than cutting over and backfilling in the same step.</p>
     *
     * <p>A mirror failure is logged but never propagated — it must not be able to break player
     * saves during a migration that is supposed to be invisible. It is logged at WARNING rather
     * than debug precisely so an empty mirror is noticed before #1812 relies on it.</p>
     */
    @Override
    public CompletableFuture<PlayerDTO> save(PlayerDTO entity) {
        return super.save(entity).thenApply(saved -> {
            mirrorServerState(saved);
            return saved;
        });
    }

    /** One player's per-server activity, as held in {@code rvnk_player_server_state}. */
    private static final class ServerState {
        final String currentWorld;
        final int timesJoined;
        final float playtimeHours;
        final Timestamp lastSeen;

        ServerState(String currentWorld, int timesJoined, float playtimeHours, Timestamp lastSeen) {
            this.currentWorld = currentWorld;
            this.timesJoined = timesJoined;
            this.playtimeHours = playtimeHours;
            this.lastSeen = lastSeen;
        }
    }

    /** Cached mirror for this server, keyed by player. Guarded by {@link #cacheLock}. */
    private volatile java.util.Map<UUID, ServerState> stateCache = java.util.Collections.emptyMap();
    private volatile long stateCacheLoadedAt = 0L;
    private final Object cacheLock = new Object();

    /** How long the mirror snapshot is reused before being reloaded. */
    private static final long STATE_CACHE_TTL_MS = 30_000L;

    /**
     * Returns this server's activity row for a player, or null when there is none.
     *
     * <p>Backed by a whole-table snapshot rather than a per-player query. {@code mapResultSet} is
     * called once per row, so a query here would turn every {@code findAll} into N+1 round trips —
     * on the WebUI roster that is one query per player, every page load.</p>
     *
     * <p>The mirror is per-server and therefore bounded by that server's own player count, so
     * holding it in memory is cheap. A {@value #STATE_CACHE_TTL_MS}ms TTL keeps it fresh enough for
     * activity data that is itself only updated on join/quit, and the cache is dropped immediately
     * after any save so a player never reads back stale values for their own action.</p>
     *
     * <p>This design also survives #1813: once identity moves to the cluster database the mirror is
     * still local, and a snapshot works across separate connections where a SQL join could not.</p>
     */
    private ServerState lookupServerState(UUID playerId) {
        long now = System.currentTimeMillis();
        if (now - stateCacheLoadedAt > STATE_CACHE_TTL_MS) {
            synchronized (cacheLock) {
                if (now - stateCacheLoadedAt > STATE_CACHE_TTL_MS) {
                    loadStateCache();
                    stateCacheLoadedAt = now;
                }
            }
        }
        return stateCache.get(playerId);
    }

    /** Loads this server's mirror rows into {@link #stateCache}. Never throws. */
    private void loadStateCache() {
        java.util.Map<UUID, ServerState> loaded = new java.util.HashMap<>();
        try (var conn = connectionProvider.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "SELECT player_id, current_world, times_joined, total_playtime_hours, last_seen "
                 + "FROM rvnk_player_server_state WHERE server_id = ?")) {
            stmt.setString(1, serverId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    try {
                        loaded.put(UUID.fromString(rs.getString("player_id")), new ServerState(
                            rs.getString("current_world"),
                            rs.getInt("times_joined"),
                            rs.getFloat("total_playtime_hours"),
                            rs.getTimestamp("last_seen")));
                    } catch (IllegalArgumentException ignored) {
                        // Malformed UUID in the mirror: skip the row rather than fail every read.
                    }
                }
            }
            stateCache = loaded;
        } catch (SQLException e) {
            // Leave the previous snapshot in place. Reads then fall back to the legacy columns for
            // anything missing, which is still correct — degraded, not wrong.
            logger.warning("Could not load per-server player state (#1812); "
                    + "falling back to legacy rvnk_players columns: " + e.getMessage());
        }
    }

    /** Drops the cached snapshot so the next read reflects a just-written change. */
    private void invalidateStateCache() {
        stateCacheLoadedAt = 0L;
    }

    /**
     * Copies per-server activity out of {@code rvnk_players} into the mirror for this server (#1812).
     *
     * <p>Existing mirror rows are <b>left untouched</b> — the dual-write from #1811 has been running
     * since deploy, so any row already present is newer than the legacy columns it would be
     * overwritten from. Backfill only fills gaps.</p>
     *
     * <p>Idempotent: safe to run repeatedly, and safe to run while players are online.</p>
     *
     * @return number of rows inserted
     */
    public int backfillServerState() {
        boolean mysql = "mysql".equalsIgnoreCase(connectionProvider.getDatabaseType());
        String sql = (mysql ? "INSERT IGNORE INTO " : "INSERT OR IGNORE INTO ")
            + "rvnk_player_server_state "
            + "(player_id, server_id, current_world, times_joined, total_playtime_hours, last_seen) "
            + "SELECT id, ?, current_world, times_joined, total_playtime_hours, last_seen "
            + "FROM rvnk_players";

        try (var conn = connectionProvider.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, serverId);
            int inserted = stmt.executeUpdate();
            invalidateStateCache();
            logger.info("Backfilled " + inserted + " per-server player state row(s) for server '"
                    + serverId + "' (#1812)");
            return inserted;
        } catch (SQLException e) {
            logger.error("Per-server state backfill failed", e);
            throw new RuntimeException("Backfill failed: " + e.getMessage(), e);
        }
    }

    /**
     * Upserts this server's activity row for the player. Best-effort; see {@link #save}.
     */
    private void mirrorServerState(PlayerDTO player) {
        if (player == null || player.getId() == null) {
            return;
        }
        boolean mysql = "mysql".equalsIgnoreCase(connectionProvider.getDatabaseType());
        String sql = mysql
            ? "INSERT INTO rvnk_player_server_state "
                + "(player_id, server_id, current_world, times_joined, total_playtime_hours, last_seen) "
                + "VALUES (?, ?, ?, ?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE current_world = VALUES(current_world), "
                + "times_joined = VALUES(times_joined), "
                + "total_playtime_hours = VALUES(total_playtime_hours), "
                + "last_seen = VALUES(last_seen)"
            : "INSERT INTO rvnk_player_server_state "
                + "(player_id, server_id, current_world, times_joined, total_playtime_hours, last_seen) "
                + "VALUES (?, ?, ?, ?, ?, ?) "
                + "ON CONFLICT(player_id, server_id) DO UPDATE SET current_world = excluded.current_world, "
                + "times_joined = excluded.times_joined, "
                + "total_playtime_hours = excluded.total_playtime_hours, "
                + "last_seen = excluded.last_seen";

        try (var conn = connectionProvider.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, player.getId().toString());
            stmt.setString(2, serverId);
            stmt.setString(3, player.getCurrentWorld());
            stmt.setInt(4, player.getTimesJoined());
            stmt.setFloat(5, player.getTotalPlaytimeHours());
            Timestamp lastSeen = player.getLastSeen();
            stmt.setTimestamp(6, lastSeen != null ? lastSeen : Timestamp.valueOf(LocalDateTime.now()));
            stmt.executeUpdate();
            invalidateStateCache();
        } catch (SQLException e) {
            logger.warning("Failed to mirror per-server state for " + player.getId()
                    + " (#1811 dual-write; rvnk_players is unaffected): " + e.getMessage());
        }
    }
    
    /**
     * Finds a player by their current name.
     * 
     * @param playerName The current name of the player
     * @return CompletableFuture containing the player if found
     */
    public CompletableFuture<Optional<PlayerDTO>> findByCurrentName(String playerName) {
        return CompletableFuture.supplyAsync(() -> {
            // Create a new QueryBuilder instance for thread safety
            QueryBuilder builder = createQueryBuilder();
            String query = builder.select("*")
                .from(tableName)
                .where("current_name = ?")
                .build();
                
            try (var conn = connectionProvider.getConnection();
                 var stmt = conn.prepareStatement(query)) {
                
                stmt.setString(1, playerName);
                
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(mapResultSet(rs));
                    }
                    return Optional.empty();
                }
            } catch (SQLException e) {
                logger.error("Failed to find player by name: " + playerName, e);
                throw new org.fourz.rvnkcore.api.exception.DatabaseException("Player lookup by name failed", e);
            }
        });
    }
    
    /**
     * Finds players who were last seen within the specified hours.
     * 
     * @param hoursAgo The number of hours to look back
     * @return CompletableFuture containing list of recent players
     */
    public CompletableFuture<List<PlayerDTO>> findRecentPlayers(int hoursAgo) {
        return CompletableFuture.supplyAsync(() -> {
            // Create a new QueryBuilder instance for thread safety
            QueryBuilder builder = createQueryBuilder();
            String query = builder.select("*")
                .from(tableName)
                .where("last_seen > ?")
                .orderBy("last_seen", false)
                .build();
                
            try (var conn = connectionProvider.getConnection();
                 var stmt = conn.prepareStatement(query)) {
                
                Timestamp cutoff = Timestamp.valueOf(LocalDateTime.now().minusHours(hoursAgo));
                stmt.setTimestamp(1, cutoff);
                
                try (ResultSet rs = stmt.executeQuery()) {
                    List<PlayerDTO> results = new ArrayList<>();
                    while (rs.next()) {
                        results.add(mapResultSet(rs));
                    }
                    return results;
                }
            } catch (SQLException e) {
                logger.error("Failed to find recent players", e);
                throw new org.fourz.rvnkcore.api.exception.DatabaseException("Recent players lookup failed", e);
            }
        });
    }
    
    /**
     * Finds players by their primary permission group.
     * 
     * @param groupName The name of the permission group
     * @return CompletableFuture containing list of players in the group
     */
    public CompletableFuture<List<PlayerDTO>> findByPrimaryGroup(String groupName) {
        return CompletableFuture.supplyAsync(() -> {
            // Create a new QueryBuilder instance for thread safety
            QueryBuilder builder = createQueryBuilder();
            String query = builder.select("*")
                .from(tableName)
                .where("primary_group = ?")
                .orderBy("current_name", true)
                .build();
                
            try (var conn = connectionProvider.getConnection();
                 var stmt = conn.prepareStatement(query)) {
                
                stmt.setString(1, groupName);
                
                try (ResultSet rs = stmt.executeQuery()) {
                    List<PlayerDTO> results = new ArrayList<>();
                    while (rs.next()) {
                        results.add(mapResultSet(rs));
                    }
                    return results;
                }
            } catch (SQLException e) {
                logger.error("Failed to find players by group: " + groupName, e);
                throw new org.fourz.rvnkcore.api.exception.DatabaseException("Players by group lookup failed", e);
            }
        });
    }
    
    /**
     * Searches for players whose names match the provided pattern.
     * 
     * @param namePattern The pattern to match (supports SQL LIKE syntax)
     * @return CompletableFuture containing list of matching players
     */
    public CompletableFuture<List<PlayerDTO>> searchByNamePattern(String namePattern) {
        return CompletableFuture.supplyAsync(() -> {
            // Create a new QueryBuilder instance for thread safety
            QueryBuilder builder = createQueryBuilder();
            String query = builder.select("*")
                .from(tableName)
                .where("current_name LIKE ? ESCAPE '!' OR name_history LIKE ? ESCAPE '!'")
                .orderBy("current_name", true)
                .build();
                
            try (var conn = connectionProvider.getConnection();
                 var stmt = conn.prepareStatement(query)) {
                
                stmt.setString(1, namePattern);
                stmt.setString(2, namePattern);
                
                try (ResultSet rs = stmt.executeQuery()) {
                    List<PlayerDTO> results = new ArrayList<>();
                    while (rs.next()) {
                        results.add(mapResultSet(rs));
                    }
                    return results;
                }
            } catch (SQLException e) {
                logger.error("Failed to search players by name pattern: " + namePattern, e);
                throw new org.fourz.rvnkcore.api.exception.DatabaseException("Player name search failed", e);
            }
        });
    }
    
    /**
     * Reads a Timestamp column, returning null for zero-date values (0000-00-00 00:00:00).
     * MySQL Connector/J 9.x throws by default on zero dates; this prevents cascade failures
     * for migrated rows. The caller's update path will overwrite null timestamps with current time.
     */
    private Timestamp safeGetTimestamp(ResultSet rs, String column) throws SQLException {
        try {
            return rs.getTimestamp(column);
        } catch (SQLException e) {
            if (e.getMessage() != null && e.getMessage().contains("Zero date value prohibited")) {
                logger.warning("Zero date in column '" + column + "' — treating as null (row will self-heal on next update)");
                return null;
            }
            throw e;
        }
    }

    @Override
    protected PlayerDTO mapResultSet(ResultSet rs) throws SQLException {
        UUID playerId = UUID.fromString(rs.getString("id"));

        // Per-server activity comes from the mirror when this server has a row for the player,
        // otherwise from the legacy rvnk_players columns (#1812).
        //
        // The fallback is what makes the cut-over safe: a player with no mirror row yet reads
        // exactly as before, so the migration is per-player and self-healing rather than a
        // flag-day. After the backfill every existing player has a row, and new players get one
        // on their first save.
        ServerState state = lookupServerState(playerId);

        PlayerDTO.Builder builder = new PlayerDTO.Builder()
            .id(playerId)
            .currentName(rs.getString("current_name"))
            .firstJoin(safeGetTimestamp(rs, "first_join"))
            .lastSeen(state != null ? state.lastSeen : safeGetTimestamp(rs, "last_seen"))
            .currentWorld(state != null ? state.currentWorld : rs.getString("current_world"))
            .timesJoined(state != null ? state.timesJoined : rs.getInt("times_joined"))
            .totalPlaytimeHours(state != null ? state.playtimeHours : rs.getFloat("total_playtime_hours"))
            .primaryGroup(rs.getString("primary_group"))
            .banned(rs.getBoolean("banned"));

        // Parse name history from comma-separated string
        String nameHistoryStr = rs.getString("name_history");
        if (nameHistoryStr != null && !nameHistoryStr.trim().isEmpty()) {
            List<String> nameHistory = Arrays.asList(nameHistoryStr.split(","));
            builder.nameHistory(nameHistory);
        }
        
        // Parse groups — JSON array preferred, CSV tolerated for legacy rows not yet migrated
        String groupsStr = rs.getString("groups");
        if (groupsStr != null && !groupsStr.trim().isEmpty()) {
            String trimmed = groupsStr.trim();
            if (trimmed.startsWith("[")) {
                Type listType = new TypeToken<List<String>>(){}.getType();
                List<String> groups = new Gson().fromJson(trimmed, listType);
                if (groups != null) builder.groups(groups);
            } else {
                builder.groups(Arrays.asList(trimmed.split(",")));
            }
        }
        
        return builder.build();
    }
    
    @Override
    protected String getPrimaryKeyColumn() {
        return "id";
    }
    
    @Override
    protected UUID getId(PlayerDTO entity) {
        return entity.getId();
    }
    
    @Override
    protected void setPrimaryKeyParameter(PreparedStatement stmt, int parameterIndex, UUID id) throws SQLException {
        stmt.setString(parameterIndex, id.toString());
    }
    
    @Override
    protected String buildInsertQuery() {
        // Create a new QueryBuilder instance for thread safety
        QueryBuilder builder = createQueryBuilder();
        return builder.insert(tableName)
            .columns("id", "current_name", "name_history", "first_join", "last_seen",
                    "current_world", "times_joined", "total_playtime_hours", "primary_group", "groups", "banned")
            .values("?", "?", "?", "?", "?", "?", "?", "?", "?", "?", "?")
            .build();
    }
    
    @Override
    protected String buildUpdateQuery() {
        // Create a new QueryBuilder instance for thread safety
        QueryBuilder builder = createQueryBuilder();
        builder = builder.update(tableName);
        builder.set("current_name", "?")
               .set("name_history", "?")
               .set("last_seen", "?")
               .set("current_world", "?")
               .set("times_joined", "?")
               .set("total_playtime_hours", "?")
               .set("primary_group", "?")
               .set("groups", "?")
               .set("banned", "?")
               .where("id = ?");
        return builder.build();
    }
    
    @Override
    protected void setInsertParameters(PreparedStatement stmt, PlayerDTO entity) throws SQLException {
        stmt.setString(1, entity.getId().toString());
        stmt.setString(2, entity.getCurrentName());
        stmt.setString(3, String.join(",", entity.getNameHistory()));
        stmt.setTimestamp(4, entity.getFirstJoin());
        stmt.setTimestamp(5, entity.getLastSeen());
        stmt.setString(6, entity.getCurrentWorld());
        stmt.setInt(7, entity.getTimesJoined());
        stmt.setFloat(8, entity.getTotalPlaytimeHours());
        stmt.setString(9, entity.getPrimaryGroup());
        stmt.setString(10, new Gson().toJson(entity.getGroups()));
        stmt.setBoolean(11, entity.isBanned());
    }
    
    @Override
    protected void setUpdateParameters(PreparedStatement stmt, PlayerDTO entity) throws SQLException {
        stmt.setString(1, entity.getCurrentName());
        stmt.setString(2, String.join(",", entity.getNameHistory()));
        stmt.setTimestamp(3, entity.getLastSeen());
        stmt.setString(4, entity.getCurrentWorld());
        stmt.setInt(5, entity.getTimesJoined());
        stmt.setFloat(6, entity.getTotalPlaytimeHours());
        stmt.setString(7, entity.getPrimaryGroup());
        stmt.setString(8, new Gson().toJson(entity.getGroups()));
        stmt.setBoolean(9, entity.isBanned());
        stmt.setString(10, entity.getId().toString()); // WHERE clause
    }
}
