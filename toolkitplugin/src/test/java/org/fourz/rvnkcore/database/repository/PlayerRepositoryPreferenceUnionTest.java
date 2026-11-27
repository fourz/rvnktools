package org.fourz.rvnkcore.database.repository;

import org.bukkit.plugin.Plugin;
import org.fourz.rvnkcore.database.connection.ConnectionProvider;
import org.fourz.rvnkcore.database.query.QueryBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * #2479: the preference union wrote with INSERT IGNORE, a syntax error on SQLite. On a MySQL outage the
 * cluster pool falls back to SQLite, so every fallback boot logged "near IGNORE: syntax error".
 */
class PlayerRepositoryPreferenceUnionTest {

    @TempDir
    Path dir;

    private static final String[] SCHEMA = {
        "CREATE TABLE rvnk_player_preferences (player_id TEXT, plugin_id TEXT, master_enabled INTEGER,"
            + " quiet_hours_start TEXT, quiet_hours_end TEXT, metadata TEXT, PRIMARY KEY (player_id, plugin_id))",
        "CREATE TABLE rvnk_player_notification_types (player_id TEXT, plugin_id TEXT, notification_type TEXT,"
            + " enabled INTEGER, PRIMARY KEY (player_id, plugin_id, notification_type))",
        "CREATE TABLE rvnk_player_notification_channels (player_id TEXT, plugin_id TEXT, notification_type TEXT,"
            + " channel_name TEXT, enabled INTEGER, PRIMARY KEY (player_id, plugin_id, notification_type, channel_name))",
    };

    /** A provider that opens a fresh connection to one SQLite file each time, as a pool would. */
    private ConnectionProvider sqlite(String name) throws SQLException {
        String url = "jdbc:sqlite:" + dir.resolve(name);
        try (Connection c = DriverManager.getConnection(url); Statement s = c.createStatement()) {
            for (String ddl : SCHEMA) s.execute(ddl);
        }
        ConnectionProvider p = mock(ConnectionProvider.class);
        when(p.getConnection()).thenAnswer(inv -> DriverManager.getConnection(url));
        when(p.getDatabaseType()).thenReturn("sqlite");
        return p;
    }

    private static void exec(ConnectionProvider p, String sql) throws SQLException {
        try (Connection c = p.getConnection(); Statement s = c.createStatement()) {
            s.execute(sql);
        }
    }

    private static String scalar(ConnectionProvider p, String sql) throws SQLException {
        try (Connection c = p.getConnection(); Statement s = c.createStatement(); ResultSet rs = s.executeQuery(sql)) {
            return rs.next() ? rs.getString(1) : null;
        }
    }

    private static PlayerRepository repository(ConnectionProvider local, ConnectionProvider cluster) {
        Plugin plugin = mock(Plugin.class);
        when(plugin.getName()).thenReturn("RVNKCorePreferenceUnionTest");
        return new PlayerRepository(local, local, cluster, mock(QueryBuilder.class), plugin, "test");
    }

    @Test
    void unionIntoASqliteTargetInsertsAndKeepsExistingRows() throws Exception {
        ConnectionProvider local = sqlite("local.db");
        ConnectionProvider cluster = sqlite("cluster.db");
        exec(local, "INSERT INTO rvnk_player_preferences VALUES ('p1', 'core', 1, NULL, NULL, 'local')");
        exec(local, "INSERT INTO rvnk_player_preferences VALUES ('p2', 'core', 0, NULL, NULL, 'local')");
        exec(local, "INSERT INTO rvnk_player_notification_types VALUES ('p1', 'core', 'announce', 1)");
        exec(local, "INSERT INTO rvnk_player_notification_channels VALUES ('p1', 'core', 'announce', 'chat', 1)");
        // The cluster already has p1: insert-only, so its value must survive
        exec(cluster, "INSERT INTO rvnk_player_preferences VALUES ('p1', 'core', 0, NULL, NULL, 'cluster')");

        int[] moved = repository(local, cluster).unionPreferencesIntoCluster();

        assertArrayEquals(new int[] {1, 1, 1}, moved);
        assertEquals("cluster", scalar(cluster, "SELECT metadata FROM rvnk_player_preferences WHERE player_id = 'p1'"));
        assertEquals("local", scalar(cluster, "SELECT metadata FROM rvnk_player_preferences WHERE player_id = 'p2'"));
    }

    @Test
    void secondRunInsertsNothing() throws Exception {
        ConnectionProvider local = sqlite("local2.db");
        ConnectionProvider cluster = sqlite("cluster2.db");
        exec(local, "INSERT INTO rvnk_player_preferences VALUES ('p1', 'core', 1, NULL, NULL, 'local')");
        PlayerRepository repo = repository(local, cluster);
        repo.unionPreferencesIntoCluster();
        assertArrayEquals(new int[] {0, 0, 0}, repo.unionPreferencesIntoCluster());
    }

    @Test
    void dialectPrefix() {
        assertEquals("INSERT IGNORE INTO ", PlayerRepository.insertIgnore(true));
        assertEquals("INSERT OR IGNORE INTO ", PlayerRepository.insertIgnore(false));
    }
}
