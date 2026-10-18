package org.fourz.rvnkcore.service.sightings;

import org.fourz.rvnkcore.database.connection.ConnectionProvider;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * {@code rvnk_player_entitlements} (#2351). Built on the cluster provider when clustering is on, so
 * nations and Event share one set; Dev, which never joins the cluster, keeps its own. No foreign key
 * into per-server tables, so it is safe to cluster-share.
 */
public class JdbcEntitlementStore implements EntitlementStore {

    public static final String TABLE = "rvnk_player_entitlements";

    private final ConnectionProvider db;
    private final boolean mysql;

    public JdbcEntitlementStore(ConnectionProvider db) {
        this.db = db;
        this.mysql = "MySQL".equalsIgnoreCase(db.getDatabaseType());
    }

    public void ensureSchema() throws SQLException {
        String ddl = mysql
                ? "CREATE TABLE IF NOT EXISTS " + TABLE + " ("
                    + "uuid VARCHAR(36) NOT NULL, "
                    + "list_name VARCHAR(32) NOT NULL, "
                    + "granted_at BIGINT NOT NULL, "
                    + "granted_by VARCHAR(64) NOT NULL DEFAULT '', "
                    + "PRIMARY KEY (uuid, list_name)"
                    + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci"
                : "CREATE TABLE IF NOT EXISTS " + TABLE + " ("
                    + "uuid TEXT NOT NULL, list_name TEXT NOT NULL, granted_at INTEGER NOT NULL, "
                    + "granted_by TEXT NOT NULL DEFAULT '', PRIMARY KEY (uuid, list_name))";
        try (Connection c = db.getConnection(); Statement st = c.createStatement()) {
            st.execute(ddl);
        }
    }

    @Override
    public List<String> lists(UUID uuid) throws SQLException {
        List<String> out = new ArrayList<>();
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT list_name FROM " + TABLE + " WHERE uuid = ? ORDER BY list_name")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(rs.getString(1));
            }
        }
        return out;
    }

    @Override
    public boolean grant(UUID uuid, String list, String by) throws SQLException {
        String sql = (mysql ? "INSERT IGNORE INTO " : "INSERT OR IGNORE INTO ") + TABLE
                + " (uuid, list_name, granted_at, granted_by) VALUES (?,?,?,?)";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, list);
            ps.setLong(3, System.currentTimeMillis());
            ps.setString(4, by == null ? "" : by.length() > 64 ? by.substring(0, 64) : by);
            return ps.executeUpdate() == 1;
        }
    }

    @Override
    public boolean revoke(UUID uuid, String list) throws SQLException {
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM " + TABLE + " WHERE uuid = ? AND list_name = ?")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, list);
            return ps.executeUpdate() > 0;
        }
    }
}
