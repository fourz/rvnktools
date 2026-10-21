package org.fourz.rvnkcore.service.sightings;

import org.fourz.rvnkcore.database.connection.ConnectionProvider;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * {@code rvnk_net_sightings} on this server's own database (#2351). Never cluster-shared: each server
 * serves its own rows and z-office keeps one cursor per server.
 */
public class JdbcSightingStore implements SightingStore {

    public static final String TABLE = "rvnk_net_sightings";

    private final ConnectionProvider db;

    public JdbcSightingStore(ConnectionProvider db) {
        this.db = db;
    }

    public void ensureSchema() throws SQLException {
        boolean mysql = "MySQL".equalsIgnoreCase(db.getDatabaseType());
        String ddl = mysql
                ? "CREATE TABLE IF NOT EXISTS " + TABLE + " ("
                    + "id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, "
                    + "uuid VARCHAR(36) NOT NULL, "
                    + "name VARCHAR(16) NOT NULL, "
                    + "address VARCHAR(15) NOT NULL, "
                    + "server VARCHAR(32) NOT NULL, "
                    + "reason VARCHAR(16) NOT NULL, "
                    + "lists VARCHAR(255) NOT NULL DEFAULT '', "
                    + "revoked VARCHAR(255) NOT NULL DEFAULT '', "
                    + "at_ms BIGINT NOT NULL, "
                    + "INDEX idx_net_sightings_at (at_ms)"
                    + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci"
                // AUTOINCREMENT: SQLite never reuses an id, even after the newest row is deleted.
                : "CREATE TABLE IF NOT EXISTS " + TABLE + " ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + "uuid TEXT NOT NULL, name TEXT NOT NULL, address TEXT NOT NULL, server TEXT NOT NULL, "
                    + "reason TEXT NOT NULL, lists TEXT NOT NULL DEFAULT '', revoked TEXT NOT NULL DEFAULT '', "
                    + "at_ms INTEGER NOT NULL)";
        try (Connection c = db.getConnection(); Statement st = c.createStatement()) {
            st.execute(ddl);
            if (!mysql) st.execute("CREATE INDEX IF NOT EXISTS idx_net_sightings_at ON " + TABLE + " (at_ms)");
            // #2375: tables made by 1.5.111..1.5.114 have no revoked column. Neither dialect has a
            // portable ADD COLUMN IF NOT EXISTS, so probe first.
            if (!columnExists(c, mysql, "revoked")) {
                st.execute("ALTER TABLE " + TABLE + " ADD COLUMN revoked "
                        + (mysql ? "VARCHAR(255)" : "TEXT") + " NOT NULL DEFAULT ''");
            }
        }
    }

    private static boolean columnExists(Connection c, boolean mysql, String column) throws SQLException {
        if (mysql) {
            try (PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM information_schema.COLUMNS "
                    + "WHERE table_schema = DATABASE() AND table_name = ? AND column_name = ?")) {
                ps.setString(1, TABLE);
                ps.setString(2, column);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() && rs.getInt(1) > 0;
                }
            }
        }
        try (PreparedStatement ps = c.prepareStatement("PRAGMA table_info(" + TABLE + ")");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                if (column.equalsIgnoreCase(rs.getString("name"))) return true;
            }
        }
        return false;
    }

    @Override
    public long append(Sighting s) throws SQLException {
        String sql = "INSERT INTO " + TABLE + " (uuid, name, address, server, reason, lists, revoked, at_ms) VALUES (?,?,?,?,?,?,?,?)";
        boolean mysql = "MySQL".equalsIgnoreCase(db.getDatabaseType());
        try (Connection c = db.getConnection();
             PreparedStatement ps = mysql ? c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)
                                          : c.prepareStatement(sql)) {
            ps.setString(1, s.uuid().toString());
            // name is VARCHAR(16). A revoke by UUID for a player this server never saw carries the
            // UUID as the name (#2375); store no name rather than lose the row.
            ps.setString(2, s.name() == null || s.name().length() > 16 ? "" : s.name());
            // address is NOT NULL in the table; a revoke row for an offline player stores ''.
            ps.setString(3, s.address() == null ? "" : s.address());
            ps.setString(4, s.server());
            ps.setString(5, s.reason());
            ps.setString(6, String.join(",", s.lists()));
            ps.setString(7, String.join(",", s.revoked()));
            ps.setLong(8, s.at());
            ps.executeUpdate();
            if (!mysql) {
                // sqlite-jdbc does not implement generated keys; the rowid is per connection.
                try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("SELECT last_insert_rowid()")) {
                    return rs.next() ? rs.getLong(1) : 0L;
                }
            }
            try (ResultSet keys = ps.getGeneratedKeys()) {
                return keys.next() ? keys.getLong(1) : 0L;
            }
        }
    }

    @Override
    public Page after(long after, int limit) throws SQLException {
        String sql = "SELECT id, uuid, name, address, server, reason, lists, revoked, at_ms FROM " + TABLE
                + " WHERE id > ? ORDER BY id LIMIT ?";
        List<Sighting> rows = new ArrayList<>();
        long oldest = 0L;
        try (Connection c = db.getConnection()) {
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setLong(1, after);
                ps.setInt(2, limit + 1);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String address = rs.getString("address");
                        rows.add(new Sighting(rs.getLong("id"), UUID.fromString(rs.getString("uuid")),
                                rs.getString("name"), address == null || address.isEmpty() ? null : address,
                                rs.getString("server"), rs.getLong("at_ms"), rs.getString("reason"),
                                split(rs.getString("lists")), split(rs.getString("revoked"))));
                    }
                }
            }
            try (PreparedStatement ps = c.prepareStatement("SELECT MIN(id) FROM " + TABLE);
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next()) oldest = rs.getLong(1);
            }
        }
        boolean more = rows.size() > limit;
        return new Page(more ? rows.subList(0, limit) : rows, more, oldest);
    }

    private static List<String> split(String csv) {
        return csv == null || csv.isEmpty() ? List.of() : Arrays.asList(csv.split(","));
    }

    @Override
    public int prune(long beforeMillis) throws SQLException {
        try (Connection c = db.getConnection()) {
            long newest;
            try (PreparedStatement ps = c.prepareStatement("SELECT MAX(id) FROM " + TABLE);
                 ResultSet rs = ps.executeQuery()) {
                newest = rs.next() ? rs.getLong(1) : 0L;
            }
            if (newest == 0L) return 0;
            try (PreparedStatement ps = c.prepareStatement(
                    "DELETE FROM " + TABLE + " WHERE at_ms < ? AND id < ?")) {
                ps.setLong(1, beforeMillis);
                ps.setLong(2, newest);
                return ps.executeUpdate();
            }
        }
    }
}
