package org.fourz.rvnkcore.database.repository;

import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Runs the SQLite dialect of the push-subscription SQL against a real SQLite database (#2103). */
class PushSubscriptionSqlTest {

    private static final String TABLE = "rvnk_push_subscriptions";

    @Test
    void sqliteDdlCreatesTableAndIsRepeatable() throws Exception {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite::memory:");
             Statement stmt = conn.createStatement()) {
            for (int pass = 0; pass < 2; pass++) {
                for (String sql : PushSubscriptionRepository.createTableSql(false, TABLE)) {
                    stmt.execute(sql);
                }
            }
            try (ResultSet rs = stmt.executeQuery(
                    "SELECT count(*) FROM sqlite_master WHERE name IN ('" + TABLE + "', 'idx_push_player')")) {
                assertEquals(2, rs.getInt(1));
            }
        }
    }

    @Test
    void sqliteUpsertRefreshesExistingEndpoint() throws Exception {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite::memory:");
             Statement stmt = conn.createStatement()) {
            for (String sql : PushSubscriptionRepository.createTableSql(false, TABLE)) {
                stmt.execute(sql);
            }
            upsert(conn, "p1", "https://push/e1", "k1", "a1");
            upsert(conn, "p2", "https://push/e1", "k2", "a2");

            try (ResultSet rs = stmt.executeQuery("SELECT count(*), player_id, auth_key FROM " + TABLE)) {
                assertEquals(1, rs.getInt(1));
                assertEquals("p2", rs.getString(2));
                assertEquals("a2", rs.getString(3));
            }
        }
    }

    @Test
    void mysqlDialectKeepsInlineIndexesAndDuplicateKeyClause() {
        assertTrue(PushSubscriptionRepository.createTableSql(true, TABLE).get(0).contains("UNIQUE INDEX"));
        assertTrue(PushSubscriptionRepository.upsertSql(true, TABLE).contains("ON DUPLICATE KEY UPDATE"));
    }

    private static void upsert(Connection conn, String player, String endpoint, String p256dh, String auth)
            throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(PushSubscriptionRepository.upsertSql(false, TABLE))) {
            ps.setString(1, player);
            ps.setString(2, endpoint);
            ps.setString(3, p256dh);
            ps.setString(4, auth);
            ps.executeUpdate();
        }
    }
}
