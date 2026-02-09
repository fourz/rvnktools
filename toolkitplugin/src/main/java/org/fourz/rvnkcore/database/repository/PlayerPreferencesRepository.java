package org.fourz.rvnkcore.database.repository;

import org.fourz.rvnkcore.api.exception.DatabaseException;
import org.fourz.rvnkcore.api.model.PlayerPreferencesDTO;
import org.fourz.rvnkcore.api.model.QuietHoursConfig;
import org.fourz.rvnkcore.database.connection.ConnectionProvider;
import org.fourz.rvnkcore.database.schema.DatabaseSetup;
import org.fourz.rvnkcore.util.log.LogManager;
import org.bukkit.plugin.Plugin;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Repository for player preference database operations.
 *
 * Manages CRUD operations across the four preference tables:
 * rvnk_player_preferences, rvnk_player_notification_types,
 * rvnk_player_notification_channels, rvnk_preference_defaults.
 *
 * All operations are async via CompletableFuture.
 *
 * @since 1.5.0
 */
public class PlayerPreferencesRepository {

    private final ConnectionProvider connectionProvider;
    private final LogManager logger;

    private final String prefsTable;
    private final String typesTable;
    private final String channelsTable;
    private final String defaultsTable;

    public PlayerPreferencesRepository(ConnectionProvider connectionProvider, Plugin plugin) {
        this.connectionProvider = connectionProvider;
        this.logger = LogManager.getInstance(plugin, getClass());

        // FIX Bug #2.2: Use DatabaseSetup table() helper to apply prefix
        DatabaseSetup dbSetup = new DatabaseSetup(connectionProvider, plugin);
        this.prefsTable = dbSetup.table(DatabaseSetup.TABLE_PLAYER_PREFERENCES);
        this.typesTable = dbSetup.table(DatabaseSetup.TABLE_PLAYER_NOTIFICATION_TYPES);
        this.channelsTable = dbSetup.table(DatabaseSetup.TABLE_PLAYER_NOTIFICATION_CHANNELS);
        this.defaultsTable = dbSetup.table(DatabaseSetup.TABLE_PREFERENCE_DEFAULTS);
    }

    // ========== Core Preferences CRUD ==========

    /**
     * Finds preferences for a player/plugin combination.
     * Returns the core preferences row plus associated type and channel preferences.
     */
    public CompletableFuture<Optional<PlayerPreferencesDTO>> findByPlayerAndPlugin(UUID playerUuid, String pluginId) {
        return CompletableFuture.supplyAsync(() -> {
            String playerId = playerUuid.toString();

            try (Connection conn = connectionProvider.getConnection()) {
                // 1. Load core preferences
                PlayerPreferencesDTO dto = loadCorePreferences(conn, playerId, pluginId);
                if (dto == null) {
                    return Optional.empty();
                }

                // 2. Load notification type preferences
                dto.setNotificationTypes(loadNotificationTypes(conn, playerId, pluginId));

                // 3. Load channel preferences
                dto.setChannelPrefs(loadChannelPreferences(conn, playerId, pluginId));

                return Optional.of(dto);
            } catch (SQLException e) {
                logger.error("Failed to find preferences for player " + playerId + " plugin " + pluginId, e);
                throw new DatabaseException("Preferences lookup failed", e);
            }
        });
    }

    /**
     * Saves a complete preferences DTO (upserts core, types, and channels).
     */
    public CompletableFuture<Void> save(PlayerPreferencesDTO dto) {
        return CompletableFuture.runAsync(() -> {
            String playerId = dto.getPlayerUuid().toString();
            String pluginId = dto.getPluginId();

            try (Connection conn = connectionProvider.getConnection()) {
                boolean autoCommit = conn.getAutoCommit();
                conn.setAutoCommit(false);

                try {
                    // 1. Upsert core preferences
                    upsertCorePreferences(conn, dto);

                    // 2. Save notification type preferences
                    saveNotificationTypes(conn, playerId, pluginId, dto.getNotificationTypes());

                    // 3. Save channel preferences
                    saveChannelPreferences(conn, playerId, pluginId, dto.getChannelPrefs());

                    conn.commit();
                    logger.debug("Saved preferences for player " + playerId + " plugin " + pluginId);
                } catch (SQLException e) {
                    conn.rollback();
                    throw e;
                } finally {
                    conn.setAutoCommit(autoCommit);
                }
            } catch (SQLException e) {
                logger.error("Failed to save preferences for player " + playerId, e);
                throw new DatabaseException("Preferences save failed", e);
            }
        });
    }

    /**
     * Deletes all preferences for a player/plugin combination.
     */
    public CompletableFuture<Void> delete(UUID playerUuid, String pluginId) {
        return CompletableFuture.runAsync(() -> {
            String playerId = playerUuid.toString();

            try (Connection conn = connectionProvider.getConnection()) {
                boolean autoCommit = conn.getAutoCommit();
                conn.setAutoCommit(false);

                try {
                    // Delete in reverse dependency order
                    deleteFrom(conn, channelsTable, playerId, pluginId);
                    deleteFrom(conn, typesTable, playerId, pluginId);
                    deleteFrom(conn, prefsTable, playerId, pluginId);

                    conn.commit();
                    logger.debug("Deleted preferences for player " + playerId + " plugin " + pluginId);
                } catch (SQLException e) {
                    conn.rollback();
                    throw e;
                } finally {
                    conn.setAutoCommit(autoCommit);
                }
            } catch (SQLException e) {
                logger.error("Failed to delete preferences for player " + playerId, e);
                throw new DatabaseException("Preferences deletion failed", e);
            }
        });
    }

    // ========== Notification Type Operations ==========

    /**
     * Sets whether a specific notification type is enabled.
     */
    public CompletableFuture<Void> setTypeEnabled(UUID playerUuid, String pluginId, String type, boolean enabled) {
        return CompletableFuture.runAsync(() -> {
            String playerId = playerUuid.toString();
            String sql = "INSERT INTO " + typesTable +
                    " (player_id, plugin_id, notification_type, enabled) VALUES (?, ?, ?, ?)" +
                    " ON DUPLICATE KEY UPDATE enabled = VALUES(enabled)";

            // SQLite fallback
            String sqliteSQL = "INSERT OR REPLACE INTO " + typesTable +
                    " (player_id, plugin_id, notification_type, enabled) VALUES (?, ?, ?, ?)";

            try (Connection conn = connectionProvider.getConnection()) {
                String query = isMysql(conn) ? sql : sqliteSQL;
                try (PreparedStatement stmt = conn.prepareStatement(query)) {
                    stmt.setString(1, playerId);
                    stmt.setString(2, pluginId);
                    stmt.setString(3, type);
                    stmt.setBoolean(4, enabled);
                    stmt.executeUpdate();
                }
            } catch (SQLException e) {
                logger.error("Failed to set type enabled for " + playerId + " " + pluginId + " " + type, e);
                throw new DatabaseException("Type preference update failed", e);
            }
        });
    }

    /**
     * Gets the set of disabled notification types for a player/plugin.
     */
    public CompletableFuture<Set<String>> getDisabledTypes(UUID playerUuid, String pluginId) {
        return CompletableFuture.supplyAsync(() -> {
            String playerId = playerUuid.toString();
            String sql = "SELECT notification_type FROM " + typesTable +
                    " WHERE player_id = ? AND plugin_id = ? AND enabled = FALSE";

            try (Connection conn = connectionProvider.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, playerId);
                stmt.setString(2, pluginId);

                Set<String> disabled = new HashSet<>();
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        disabled.add(rs.getString("notification_type"));
                    }
                }
                return disabled;
            } catch (SQLException e) {
                logger.error("Failed to get disabled types for " + playerId, e);
                throw new DatabaseException("Disabled types lookup failed", e);
            }
        });
    }

    // ========== Channel Preference Operations ==========

    /**
     * Sets whether a channel is enabled for a notification type.
     */
    public CompletableFuture<Void> setChannelEnabled(UUID playerUuid, String pluginId, String type, String channel, boolean enabled) {
        return CompletableFuture.runAsync(() -> {
            String playerId = playerUuid.toString();
            String sql = "INSERT INTO " + channelsTable +
                    " (player_id, plugin_id, notification_type, channel_name, enabled) VALUES (?, ?, ?, ?, ?)" +
                    " ON DUPLICATE KEY UPDATE enabled = VALUES(enabled)";

            String sqliteSQL = "INSERT OR REPLACE INTO " + channelsTable +
                    " (player_id, plugin_id, notification_type, channel_name, enabled) VALUES (?, ?, ?, ?, ?)";

            try (Connection conn = connectionProvider.getConnection()) {
                String query = isMysql(conn) ? sql : sqliteSQL;
                try (PreparedStatement stmt = conn.prepareStatement(query)) {
                    stmt.setString(1, playerId);
                    stmt.setString(2, pluginId);
                    stmt.setString(3, type);
                    stmt.setString(4, channel);
                    stmt.setBoolean(5, enabled);
                    stmt.executeUpdate();
                }
            } catch (SQLException e) {
                logger.error("Failed to set channel enabled for " + playerId, e);
                throw new DatabaseException("Channel preference update failed", e);
            }
        });
    }

    /**
     * Gets channel preferences for a notification type.
     */
    public CompletableFuture<Map<String, Boolean>> getChannelPrefs(UUID playerUuid, String pluginId, String type) {
        return CompletableFuture.supplyAsync(() -> {
            String playerId = playerUuid.toString();
            String sql = "SELECT channel_name, enabled FROM " + channelsTable +
                    " WHERE player_id = ? AND plugin_id = ? AND notification_type = ?";

            try (Connection conn = connectionProvider.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, playerId);
                stmt.setString(2, pluginId);
                stmt.setString(3, type);

                Map<String, Boolean> channels = new HashMap<>();
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        channels.put(rs.getString("channel_name"), rs.getBoolean("enabled"));
                    }
                }
                return channels;
            } catch (SQLException e) {
                logger.error("Failed to get channel prefs for " + playerId, e);
                throw new DatabaseException("Channel preferences lookup failed", e);
            }
        });
    }

    // ========== Master Toggle ==========

    /**
     * Updates only the master_enabled field for a player/plugin.
     */
    public CompletableFuture<Void> setMasterEnabled(UUID playerUuid, String pluginId, boolean enabled) {
        return CompletableFuture.runAsync(() -> {
            String playerId = playerUuid.toString();

            // Upsert: create row if needed, update if exists
            String sql = "INSERT INTO " + prefsTable +
                    " (player_id, plugin_id, master_enabled) VALUES (?, ?, ?)" +
                    " ON DUPLICATE KEY UPDATE master_enabled = VALUES(master_enabled)";

            String sqliteSQL = "INSERT OR REPLACE INTO " + prefsTable +
                    " (player_id, plugin_id, master_enabled, quiet_hours_start, quiet_hours_end) " +
                    "VALUES (?, ?, ?, " +
                    "COALESCE((SELECT quiet_hours_start FROM " + prefsTable + " WHERE player_id = ? AND plugin_id = ?), -1), " +
                    "COALESCE((SELECT quiet_hours_end FROM " + prefsTable + " WHERE player_id = ? AND plugin_id = ?), -1))";

            try (Connection conn = connectionProvider.getConnection()) {
                if (isMysql(conn)) {
                    try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                        stmt.setString(1, playerId);
                        stmt.setString(2, pluginId);
                        stmt.setBoolean(3, enabled);
                        stmt.executeUpdate();
                    }
                } else {
                    try (PreparedStatement stmt = conn.prepareStatement(sqliteSQL)) {
                        stmt.setString(1, playerId);
                        stmt.setString(2, pluginId);
                        stmt.setBoolean(3, enabled);
                        stmt.setString(4, playerId);
                        stmt.setString(5, pluginId);
                        stmt.setString(6, playerId);
                        stmt.setString(7, pluginId);
                        stmt.executeUpdate();
                    }
                }
            } catch (SQLException e) {
                logger.error("Failed to set master enabled for " + playerId + " " + pluginId, e);
                throw new DatabaseException("Master toggle update failed", e);
            }
        });
    }

    // ========== Quiet Hours ==========

    /**
     * Updates quiet hours for a player/plugin.
     */
    public CompletableFuture<Void> setQuietHours(UUID playerUuid, String pluginId, int startHour, int endHour) {
        return CompletableFuture.runAsync(() -> {
            String playerId = playerUuid.toString();

            String sql = "INSERT INTO " + prefsTable +
                    " (player_id, plugin_id, quiet_hours_start, quiet_hours_end) VALUES (?, ?, ?, ?)" +
                    " ON DUPLICATE KEY UPDATE quiet_hours_start = VALUES(quiet_hours_start), quiet_hours_end = VALUES(quiet_hours_end)";

            String sqliteSQL = "INSERT OR REPLACE INTO " + prefsTable +
                    " (player_id, plugin_id, master_enabled, quiet_hours_start, quiet_hours_end) " +
                    "VALUES (?, ?, " +
                    "COALESCE((SELECT master_enabled FROM " + prefsTable + " WHERE player_id = ? AND plugin_id = ?), 0), " +
                    "?, ?)";

            try (Connection conn = connectionProvider.getConnection()) {
                if (isMysql(conn)) {
                    try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                        stmt.setString(1, playerId);
                        stmt.setString(2, pluginId);
                        stmt.setInt(3, startHour);
                        stmt.setInt(4, endHour);
                        stmt.executeUpdate();
                    }
                } else {
                    try (PreparedStatement stmt = conn.prepareStatement(sqliteSQL)) {
                        stmt.setString(1, playerId);
                        stmt.setString(2, pluginId);
                        stmt.setString(3, playerId);
                        stmt.setString(4, pluginId);
                        stmt.setInt(5, startHour);
                        stmt.setInt(6, endHour);
                        stmt.executeUpdate();
                    }
                }
            } catch (SQLException e) {
                logger.error("Failed to set quiet hours for " + playerId + " " + pluginId, e);
                throw new DatabaseException("Quiet hours update failed", e);
            }
        });
    }

    // ========== Admin Defaults ==========

    /**
     * Gets all default preference values for a plugin.
     */
    public CompletableFuture<Map<String, String>> getDefaults(String pluginId) {
        return CompletableFuture.supplyAsync(() -> {
            // Load global defaults first, then plugin-specific overrides
            String sql = "SELECT preference_key, preference_value FROM " + defaultsTable +
                    " WHERE plugin_id = ? OR plugin_id = 'global' ORDER BY plugin_id ASC";

            try (Connection conn = connectionProvider.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, pluginId);

                Map<String, String> defaults = new HashMap<>();
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        // Plugin-specific values override global (because of ORDER BY ASC: global < specific)
                        defaults.put(rs.getString("preference_key"), rs.getString("preference_value"));
                    }
                }
                return defaults;
            } catch (SQLException e) {
                logger.error("Failed to get defaults for plugin " + pluginId, e);
                throw new DatabaseException("Defaults lookup failed", e);
            }
        });
    }

    /**
     * Sets an admin default preference value.
     */
    public CompletableFuture<Void> setDefault(String pluginId, String key, String value) {
        return CompletableFuture.runAsync(() -> {
            String sql = "INSERT INTO " + defaultsTable +
                    " (plugin_id, preference_key, preference_value) VALUES (?, ?, ?)" +
                    " ON DUPLICATE KEY UPDATE preference_value = VALUES(preference_value)";

            String sqliteSQL = "INSERT OR REPLACE INTO " + defaultsTable +
                    " (plugin_id, preference_key, preference_value) VALUES (?, ?, ?)";

            try (Connection conn = connectionProvider.getConnection()) {
                String query = isMysql(conn) ? sql : sqliteSQL;
                try (PreparedStatement stmt = conn.prepareStatement(query)) {
                    stmt.setString(1, pluginId);
                    stmt.setString(2, key);
                    stmt.setString(3, value);
                    stmt.executeUpdate();
                }
            } catch (SQLException e) {
                logger.error("Failed to set default for " + pluginId + " " + key, e);
                throw new DatabaseException("Default preference update failed", e);
            }
        });
    }

    // ========== Internal Helpers ==========

    private PlayerPreferencesDTO loadCorePreferences(Connection conn, String playerId, String pluginId) throws SQLException {
        String sql = "SELECT * FROM " + prefsTable + " WHERE player_id = ? AND plugin_id = ?";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, playerId);
            stmt.setString(2, pluginId);

            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }

                PlayerPreferencesDTO dto = new PlayerPreferencesDTO();
                dto.setPlayerUuid(UUID.fromString(rs.getString("player_id")));
                dto.setPluginId(rs.getString("plugin_id"));
                dto.setMasterEnabled(rs.getBoolean("master_enabled"));
                dto.setQuietHours(new QuietHoursConfig(
                        rs.getInt("quiet_hours_start"),
                        rs.getInt("quiet_hours_end")
                ));

                // Parse metadata if present
                String metadataStr = rs.getString("metadata");
                if (metadataStr != null && !metadataStr.isEmpty()) {
                    Map<String, String> metadata = new HashMap<>();
                    metadata.put("raw", metadataStr);
                    dto.setMetadata(metadata);
                }

                return dto;
            }
        }
    }

    private Map<String, Boolean> loadNotificationTypes(Connection conn, String playerId, String pluginId) throws SQLException {
        String sql = "SELECT notification_type, enabled FROM " + typesTable +
                " WHERE player_id = ? AND plugin_id = ?";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, playerId);
            stmt.setString(2, pluginId);

            Map<String, Boolean> types = new HashMap<>();
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    types.put(rs.getString("notification_type"), rs.getBoolean("enabled"));
                }
            }
            return types;
        }
    }

    private Map<String, Map<String, Boolean>> loadChannelPreferences(Connection conn, String playerId, String pluginId) throws SQLException {
        String sql = "SELECT notification_type, channel_name, enabled FROM " + channelsTable +
                " WHERE player_id = ? AND plugin_id = ?";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, playerId);
            stmt.setString(2, pluginId);

            Map<String, Map<String, Boolean>> channelPrefs = new HashMap<>();
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String notifType = rs.getString("notification_type");
                    String channelName = rs.getString("channel_name");
                    boolean enabled = rs.getBoolean("enabled");

                    channelPrefs.computeIfAbsent(notifType, k -> new HashMap<>())
                            .put(channelName, enabled);
                }
            }
            return channelPrefs;
        }
    }

    private void upsertCorePreferences(Connection conn, PlayerPreferencesDTO dto) throws SQLException {
        String playerId = dto.getPlayerUuid().toString();
        String pluginId = dto.getPluginId();

        String sql = "INSERT INTO " + prefsTable +
                " (player_id, plugin_id, master_enabled, quiet_hours_start, quiet_hours_end, metadata) " +
                "VALUES (?, ?, ?, ?, ?, ?)" +
                " ON DUPLICATE KEY UPDATE master_enabled = VALUES(master_enabled), " +
                "quiet_hours_start = VALUES(quiet_hours_start), quiet_hours_end = VALUES(quiet_hours_end), " +
                "metadata = VALUES(metadata)";

        String sqliteSQL = "INSERT OR REPLACE INTO " + prefsTable +
                " (player_id, plugin_id, master_enabled, quiet_hours_start, quiet_hours_end, metadata) " +
                "VALUES (?, ?, ?, ?, ?, ?)";

        String query = isMysql(conn) ? sql : sqliteSQL;
        try (PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setString(1, playerId);
            stmt.setString(2, pluginId);
            stmt.setBoolean(3, dto.isMasterEnabled());
            stmt.setInt(4, dto.getQuietHours().getStartHour());
            stmt.setInt(5, dto.getQuietHours().getEndHour());

            // Serialize metadata
            String metadataStr = null;
            if (dto.getMetadata() != null && !dto.getMetadata().isEmpty()) {
                StringBuilder sb = new StringBuilder();
                dto.getMetadata().forEach((k, v) -> {
                    if (sb.length() > 0) sb.append(",");
                    sb.append(k).append("=").append(v);
                });
                metadataStr = sb.toString();
            }
            stmt.setString(6, metadataStr);

            stmt.executeUpdate();
        }
    }

    private void saveNotificationTypes(Connection conn, String playerId, String pluginId, Map<String, Boolean> types) throws SQLException {
        if (types == null || types.isEmpty()) return;

        String sql;
        if (isMysql(conn)) {
            sql = "INSERT INTO " + typesTable +
                    " (player_id, plugin_id, notification_type, enabled) VALUES (?, ?, ?, ?)" +
                    " ON DUPLICATE KEY UPDATE enabled = VALUES(enabled)";
        } else {
            sql = "INSERT OR REPLACE INTO " + typesTable +
                    " (player_id, plugin_id, notification_type, enabled) VALUES (?, ?, ?, ?)";
        }

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            for (Map.Entry<String, Boolean> entry : types.entrySet()) {
                stmt.setString(1, playerId);
                stmt.setString(2, pluginId);
                stmt.setString(3, entry.getKey());
                stmt.setBoolean(4, entry.getValue());
                stmt.addBatch();
            }
            stmt.executeBatch();
        }
    }

    private void saveChannelPreferences(Connection conn, String playerId, String pluginId, Map<String, Map<String, Boolean>> channelPrefs) throws SQLException {
        if (channelPrefs == null || channelPrefs.isEmpty()) return;

        String sql;
        if (isMysql(conn)) {
            sql = "INSERT INTO " + channelsTable +
                    " (player_id, plugin_id, notification_type, channel_name, enabled) VALUES (?, ?, ?, ?, ?)" +
                    " ON DUPLICATE KEY UPDATE enabled = VALUES(enabled)";
        } else {
            sql = "INSERT OR REPLACE INTO " + channelsTable +
                    " (player_id, plugin_id, notification_type, channel_name, enabled) VALUES (?, ?, ?, ?, ?)";
        }

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            for (Map.Entry<String, Map<String, Boolean>> typeEntry : channelPrefs.entrySet()) {
                String notifType = typeEntry.getKey();
                for (Map.Entry<String, Boolean> channelEntry : typeEntry.getValue().entrySet()) {
                    stmt.setString(1, playerId);
                    stmt.setString(2, pluginId);
                    stmt.setString(3, notifType);
                    stmt.setString(4, channelEntry.getKey());
                    stmt.setBoolean(5, channelEntry.getValue());
                    stmt.addBatch();
                }
            }
            stmt.executeBatch();
        }
    }

    private void deleteFrom(Connection conn, String table, String playerId, String pluginId) throws SQLException {
        String sql = "DELETE FROM " + table + " WHERE player_id = ? AND plugin_id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, playerId);
            stmt.setString(2, pluginId);
            stmt.executeUpdate();
        }
    }

    private boolean isMysql(Connection conn) {
        try {
            String driverName = conn.getMetaData().getDriverName();
            return driverName != null && driverName.toLowerCase().contains("mysql");
        } catch (SQLException e) {
            return false;
        }
    }
}
