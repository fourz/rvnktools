package org.fourz.rvnkcore.service.kit;

import org.fourz.rvnkcore.database.connection.ConnectionProvider;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * {@code rvnk_kits} (#2415). Built on the cluster provider when clustering is on, so nations and Event
 * share one set of kit definitions; Dev, which never joins the cluster, keeps its own.
 *
 * <p>Cluster-safe by the rule in {@code docs/architecture/cluster-shared-data.md}: a kit is item data,
 * true on every server. It has no world column and no foreign key into a per-server table. The items
 * travel as encoded bytes ({@link KitPayload}).</p>
 */
public class JdbcKitStore implements KitStore {

    public static final String TABLE = "rvnk_kits";
    private static final String COLUMNS = "name, clear_first, codec, item_count, version, saved_by, saved_at";

    private final ConnectionProvider db;
    private final boolean mysql;

    public JdbcKitStore(ConnectionProvider db) {
        this.db = db;
        this.mysql = "MySQL".equalsIgnoreCase(db.getDatabaseType());
    }

    public void ensureSchema() throws SQLException {
        try (Connection c = db.getConnection(); Statement st = c.createStatement()) {
            st.execute(ddl(mysql));
        }
    }

    static String ddl(boolean mysql) {
        return mysql
                ? "CREATE TABLE IF NOT EXISTS " + TABLE + " ("
                    + "name VARCHAR(32) NOT NULL, "
                    + "clear_first TINYINT(1) NOT NULL DEFAULT 0, "
                    + "codec VARCHAR(16) NOT NULL, "
                    + "item_count INT NOT NULL DEFAULT 0, "
                    + "payload MEDIUMTEXT NOT NULL, "
                    + "version INT NOT NULL DEFAULT 1, "
                    + "saved_by VARCHAR(64) NOT NULL DEFAULT '', "
                    + "saved_at BIGINT NOT NULL, "
                    + "PRIMARY KEY (name)"
                    + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci"
                : "CREATE TABLE IF NOT EXISTS " + TABLE + " ("
                    + "name TEXT NOT NULL PRIMARY KEY, clear_first INTEGER NOT NULL DEFAULT 0, "
                    + "codec TEXT NOT NULL, item_count INTEGER NOT NULL DEFAULT 0, payload TEXT NOT NULL, "
                    + "version INTEGER NOT NULL DEFAULT 1, saved_by TEXT NOT NULL DEFAULT '', "
                    + "saved_at INTEGER NOT NULL)";
    }

    /** Insert, or replace and bump the version. SQLite takes the {@code ON CONFLICT} form (#2103). */
    static String upsertSql(boolean mysql) {
        String insert = "INSERT INTO " + TABLE
                + " (name, clear_first, codec, item_count, payload, version, saved_by, saved_at)"
                + " VALUES (?, ?, ?, ?, ?, 1, ?, ?)";
        if (mysql) {
            return insert + " ON DUPLICATE KEY UPDATE clear_first = VALUES(clear_first), codec = VALUES(codec),"
                    + " item_count = VALUES(item_count), payload = VALUES(payload), version = version + 1,"
                    + " saved_by = VALUES(saved_by), saved_at = VALUES(saved_at)";
        }
        return insert + " ON CONFLICT(name) DO UPDATE SET clear_first = excluded.clear_first,"
                + " codec = excluded.codec, item_count = excluded.item_count, payload = excluded.payload,"
                + " version = " + TABLE + ".version + 1, saved_by = excluded.saved_by, saved_at = excluded.saved_at";
    }

    @Override
    public List<Kit> list() throws SQLException {
        List<Kit> out = new ArrayList<>();
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT " + COLUMNS + " FROM " + TABLE + " ORDER BY name");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) out.add(summary(rs));
        }
        return out;
    }

    @Override
    public Optional<Kit> get(String name) throws SQLException {
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT " + COLUMNS + ", payload FROM " + TABLE + " WHERE name = ?")) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                Kit s = summary(rs);
                return Optional.of(new Kit(s.name(), s.clearFirst(), s.codec(), s.itemCount(),
                        KitPayload.decode(rs.getString("payload")), s.version(), s.savedBy(), s.savedAt()));
            }
        }
    }

    @Override
    public int save(Kit kit) throws SQLException {
        try (Connection c = db.getConnection()) {
            try (PreparedStatement ps = c.prepareStatement(upsertSql(mysql))) {
                ps.setString(1, kit.name());
                ps.setInt(2, kit.clearFirst() ? 1 : 0);
                ps.setString(3, kit.codec());
                ps.setInt(4, kit.items().size());
                ps.setString(5, KitPayload.encode(kit.items()));
                String by = kit.savedBy() == null ? "" : kit.savedBy();
                ps.setString(6, by.length() > 64 ? by.substring(0, 64) : by);
                ps.setLong(7, kit.savedAt());
                ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement("SELECT version FROM " + TABLE + " WHERE name = ?")) {
                ps.setString(1, kit.name());
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? rs.getInt(1) : 1;
                }
            }
        }
    }

    @Override
    public boolean delete(String name) throws SQLException {
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM " + TABLE + " WHERE name = ?")) {
            ps.setString(1, name);
            return ps.executeUpdate() > 0;
        }
    }

    private static Kit summary(ResultSet rs) throws SQLException {
        return Kit.summary(rs.getString("name"), rs.getInt("clear_first") != 0, rs.getString("codec"),
                rs.getInt("item_count"), rs.getInt("version"), rs.getString("saved_by"), rs.getLong("saved_at"));
    }
}
