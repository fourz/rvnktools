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
                    + "at_ms BIGINT NOT NULL, "
                    + "INDEX idx_net_sightings_at (at_ms)"
                    + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci"
                // AUTOINCREMENT: SQLite never reuses an id, even after the newest row is deleted.
                : "CREATE TABLE IF NOT EXISTS " + TABLE + " ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + "uuid TEXT NOT NULL, name TEXT NOT NULL, address TEXT NOT NULL, server TEXT NOT NULL, "
                    + "reason TEXT NOT NULL, lists TEXT NOT NULL DEFAULT '', at_ms INTEGER NOT NULL)";
        try (Connection c = db.getConnection(); Statement st = c.createStatement()) {
            st.execute(ddl);
            if (!mysql) st.execute("CREATE INDEX IF NOT EXISTS idx_net_sightings_at ON " + TABLE + " (at_ms)");
        }
    }

    @Override
    public long append(Sighting s) throws SQLException {
        String sql = "INSERT INTO " + TABLE + " (uuid, name, address, server, reason, lists, at_ms) VALUES (?,?,?,?,?,?,?)";
        boolean mysql = "MySQL".equalsIgnoreCase(db.getDatabaseType());
        try (Connection c = db.getConnection();
             PreparedStatement ps = mysql ? c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)
                                          : c.prepareStatement(sql)) {
            ps.setString(1, s.uuid().toString());
            ps.setString(2, s.name());
            ps.setString(3, s.address());
            ps.setString(4, s.server());
            ps.setString(5, s.reason());
            ps.setString(6, String.join(",", s.lists()));
            ps.setLong(7, s.at());
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
        String sql = "SELECT id, uuid, name, address, server, reason, lists, at_ms FROM " + TABLE
                + " WHERE id > ? ORDER BY id LIMIT ?";
        List<Sighting> rows = new ArrayList<>();
        long oldest = 0L;
        try (Connection c = db.getConnection()) {
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setLong(1, after);
                ps.setInt(2, limit + 1);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String lists = rs.getString("lists");
                        rows.add(new Sighting(rs.getLong("id"), UUID.fromString(rs.getString("uuid")),
                                rs.getString("name"), rs.getString("address"), rs.getString("server"),
                                rs.getLong("at_ms"), rs.getString("reason"),
                                lists == null || lists.isEmpty() ? List.of() : Arrays.asList(lists.split(","))));
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
