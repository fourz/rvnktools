package org.fourz.rvnktools.announceManager.data;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.Set;
import java.util.HashSet;
import java.util.Map;
import java.util.HashMap;
import org.bukkit.Bukkit;

import org.fourz.rvnktools.announceManager.AnnounceType;
import org.fourz.rvnktools.announceManager.Announcement;

public class MySQLDataConnector implements DataStore {
    private final String url;
    private final String username;
    private final String password;
    private final String database;
    private Connection connection;
    private boolean empty = false;

    public MySQLDataConnector(String host, int port, String database, String username, String password, boolean useSSL) {
        this.url = "jdbc:mysql://" + host + ":" + port + "/" + database + "?useSSL=" + useSSL;
        this.username = username;
        this.password = password;
        this.database = database;
    }

    @Override
    public void connect() {
        try {
            if (connection == null || connection.isClosed()) {
                Bukkit.getLogger().info("[RVNKToolKit] Attempting to establish MySQL connection to " + url);
                connection = DriverManager.getConnection(url, username, password);
                Bukkit.getLogger().info("[RVNKToolKit] MySQL connection established successfully");
                initializeTables();
            }
        } catch (SQLException e) {
            Bukkit.getLogger().severe("[RVNKToolKit] Failed to connect to MySQL: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public void disconnect() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void ensureConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            Bukkit.getLogger().info("[RVNKToolKit] Re-establishing lost MySQL connection");
            connect();
        }
    }

    @Override
    public void saveAnnouncement(Announcement announcement) {
        String query = "INSERT INTO announcements (id, text, type, recurrence, owner, permission, date, time, expiration) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try {
            ensureConnection();
            try (PreparedStatement statement = connection.prepareStatement(query)) {
                statement.setString(1, announcement.getId());
                statement.setString(2, announcement.getText());
                statement.setString(3, announcement.getType());
                statement.setObject(4, announcement.getRecurrence()); // Changed to handle Long
                statement.setString(5, announcement.getOwner());
                statement.setString(6, announcement.getPermission());
                statement.setDate(7, announcement.getDate() != null ? Date.valueOf(announcement.getDate()) : null);
                statement.setTime(8, announcement.getTime() != null ? Time.valueOf(announcement.getTime()) : null);
                statement.setTimestamp(9, announcement.getExpiration() != null ? Timestamp.valueOf(announcement.getExpiration()) : null);
                statement.executeUpdate();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void deleteAnnouncement(String id) {
        String query = "DELETE FROM announcements WHERE id = ?";
        try {
            ensureConnection();
            try (PreparedStatement statement = connection.prepareStatement(query)) {
                statement.setString(1, id);
                statement.executeUpdate();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public List<Announcement> loadAnnouncements() {
        Map<String, Announcement> announcements = new HashMap<>();
        String query = "SELECT * FROM announcements";
        try {
            ensureConnection();
            try (Statement statement = connection.createStatement();
                 ResultSet resultSet = statement.executeQuery(query)) {
                while (resultSet.next()) {
                    Announcement announcement = new Announcement();
                    announcement.setId(resultSet.getString("id"));
                    announcement.setText(resultSet.getString("text"));
                    announcement.setType(resultSet.getString("type"));
                    announcement.setRecurrence(resultSet.getObject("recurrence") != null ? resultSet.getLong("recurrence") : null);
                    announcement.setOwner(resultSet.getString("owner"));
                    announcement.setPermission(resultSet.getString("permission"));
                    announcement.setDate(resultSet.getDate("date") != null ? resultSet.getDate("date").toLocalDate() : null);
                    announcement.setTime(resultSet.getTime("time") != null ? resultSet.getTime("time").toLocalTime() : null);
                    announcement.setExpiration(resultSet.getTimestamp("expiration") != null ? resultSet.getTimestamp("expiration").toLocalDateTime() : null);
                    announcements.put(announcement.getId(), announcement);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return new ArrayList<>(announcements.values());
    }

    @Override
    public void saveAnnounceType(AnnounceType announceType) {
        String query = "INSERT INTO announce_types (id, prefix, suffix, permission, listing_fee) VALUES (?, ?, ?, ?, ?)";
        try {
            ensureConnection();
            try (PreparedStatement statement = connection.prepareStatement(query)) {
                statement.setString(1, announceType.getId());
                statement.setString(2, announceType.getPrefix());
                statement.setString(3, announceType.getSuffix());
                statement.setString(4, announceType.getPermission());
                statement.setDouble(5, announceType.getListingFee() != null ? announceType.getListingFee() : 0.0);
                statement.executeUpdate();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public List<AnnounceType> loadAnnounceTypes() {
        List<AnnounceType> announceTypes = new ArrayList<>();
        String query = "SELECT * FROM announce_types";
        try {
            ensureConnection();
            try (Statement statement = connection.createStatement();
                 ResultSet resultSet = statement.executeQuery(query)) {
                while (resultSet.next()) {
                    AnnounceType announceType = new AnnounceType();
                    announceType.setId(resultSet.getString("id"));
                    announceType.setPrefix(resultSet.getString("prefix"));
                    announceType.setSuffix(resultSet.getString("suffix"));
                    announceType.setPermission(resultSet.getString("permission"));
                    announceType.setListingFee(resultSet.getDouble("listing_fee"));
                    announceTypes.add(announceType);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return announceTypes;
    }

    @Override
    public void initializeTables() {
        try {
            ensureConnection();
            DatabaseMetaData metaData = connection.getMetaData();
            ResultSet tables;
            
            Bukkit.getLogger().info("[RVNKToolKit] Checking and creating necessary MySQL tables");
            
            // Check if announcements table exists
            tables = metaData.getTables(database, null, "announcements", null);
            if (!tables.next()) {
                Bukkit.getLogger().info("[RVNKToolKit] Creating announcements table");
                Statement stmt = connection.createStatement();
                String createAnnouncementsTable = "CREATE TABLE announcements (" +
                    "id VARCHAR(64) PRIMARY KEY," +
                    "text TEXT NOT NULL," +
                    "type VARCHAR(32) NOT NULL," +
                    "recurrence BIGINT," + // Changed from VARCHAR to BIGINT
                    "owner VARCHAR(64)," +
                    "permission VARCHAR(128)," +
                    "date DATE," +
                    "time TIME," +
                    "expiration DATETIME" +
                    ")";
                stmt.executeUpdate(createAnnouncementsTable);
                Bukkit.getLogger().info("[RVNKToolKit] announcements table created successfully");
                empty = true;
            } else {
                Bukkit.getLogger().info("[RVNKToolKit] announcements table already exists");
            }
            
            // Check if announce_types table exists
            tables = metaData.getTables(database, null, "announce_types", null);
            if (!tables.next()) {
                Bukkit.getLogger().info("[RVNKToolKit] Creating announce_types table");
                Statement stmt = connection.createStatement();
                String createAnnounceTypesTable = "CREATE TABLE announce_types (" +
                    "id VARCHAR(64) PRIMARY KEY," +
                    "prefix VARCHAR(128)," + 
                    "suffix VARCHAR(128)," +
                    "permission VARCHAR(128)," +
                    "listing_fee DOUBLE" +
                    ")";
                stmt.executeUpdate(createAnnounceTypesTable);
                Bukkit.getLogger().info("[RVNKToolKit] announce_types table created successfully");
            } else {
                Bukkit.getLogger().info("[RVNKToolKit] announce_types table already exists");
            }

            // Check if announce_disabledtypes table exists
            tables = metaData.getTables(database, null, "announce_disabledtypes", null);
            if (!tables.next()) {
                Bukkit.getLogger().info("[RVNKToolKit] Creating announce_disabledtypes table");
                Statement stmt = connection.createStatement();
                String createAnnounceDisabledTypesTable = "CREATE TABLE announce_disabledtypes (" +
                    "player_id VARCHAR(36)," +
                    "type VARCHAR(64)," +
                    "PRIMARY KEY (player_id, type)" +
                    ")";
                stmt.executeUpdate(createAnnounceDisabledTypesTable);
                Bukkit.getLogger().info("[RVNKToolKit] announce_disabledtypes table created successfully");
            } else {
                Bukkit.getLogger().info("[RVNKToolKit] announce_disabledtypes table already exists");
            }

            // Check if announce_prefs table exists
            tables = metaData.getTables(database, null, "announce_prefs", null);
            if (!tables.next()) {
                Bukkit.getLogger().info("[RVNKToolKit] Creating announce_prefs table");
                Statement stmt = connection.createStatement();
                String createAnnouncePrefsTable = "CREATE TABLE announce_prefs (" +
                    "player_id VARCHAR(36) PRIMARY KEY," +
                    "text VARCHAR(512)" +
                    ")";
                stmt.executeUpdate(createAnnouncePrefsTable);
                Bukkit.getLogger().info("[RVNKToolKit] announce_prefs table created successfully");
            } else {
                Bukkit.getLogger().info("[RVNKToolKit] announce_prefs table already exists");
            }
            
            Bukkit.getLogger().info("[RVNKToolKit] All database tables verified/created successfully");
            
        } catch (SQLException e) {
            Bukkit.getLogger().severe("[RVNKToolKit] Failed to initialize database tables: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public boolean announcementExists(String id) {
        String query = "SELECT COUNT(*) FROM announcements WHERE id = ?";
        try {
            ensureConnection();
            try (PreparedStatement statement = connection.prepareStatement(query)) {
                statement.setString(1, id);
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (resultSet.next()) {
                        int count = resultSet.getInt(1);
                        return count > 0;
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    @Override
    public boolean isEmpty() {
        try {
            ensureConnection();
            
            // Check both tables for data
            try (Statement stmt = connection.createStatement()) {
                ResultSet rs1 = stmt.executeQuery("SELECT COUNT(*) FROM announcements");
                rs1.next();
                int announceCount = rs1.getInt(1);
                
                ResultSet rs2 = stmt.executeQuery("SELECT COUNT(*) FROM announce_types");
                rs2.next();
                int typeCount = rs2.getInt(1);
                
                return announceCount == 0 && typeCount == 0;
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return true; // Assume empty on error
        }
    }

    @Override
    public void savePlayerDisabledType(UUID playerId, String type) {
        String query = "INSERT IGNORE INTO announce_disabledtypes (player_id, type) VALUES (?, ?)";
        try {
            ensureConnection();
            try (PreparedStatement statement = connection.prepareStatement(query)) {
                statement.setString(1, playerId.toString());
                statement.setString(2, type);
                statement.executeUpdate();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void removePlayerDisabledType(UUID playerId, String type) {
        String query = "DELETE FROM announce_disabledtypes WHERE player_id = ? AND type = ?";
        try {
            ensureConnection();
            try (PreparedStatement statement = connection.prepareStatement(query)) {
                statement.setString(1, playerId.toString());
                statement.setString(2, type);
                statement.executeUpdate();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public Set<String> getPlayerDisabledTypes(UUID playerId) {
        Set<String> types = new HashSet<>();
        String query = "SELECT type FROM announce_disabledtypes WHERE player_id = ?";
        try {
            ensureConnection();
            try (PreparedStatement statement = connection.prepareStatement(query)) {
                statement.setString(1, playerId.toString());
                try (ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        types.add(resultSet.getString("type"));
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return types;
    }

    @Override
    public Map<UUID, Set<String>> getAllPlayerDisabledTypes() {
        Map<UUID, Set<String>> allTypes = new HashMap<>();
        String query = "SELECT player_id, type FROM announce_disabledtypes";
        try {
            ensureConnection();
            try (Statement statement = connection.createStatement();
                 ResultSet resultSet = statement.executeQuery(query)) {
                while (resultSet.next()) {
                    UUID playerId = UUID.fromString(resultSet.getString("player_id"));
                    String type = resultSet.getString("type");
                    allTypes.computeIfAbsent(playerId, k -> new HashSet<>()).add(type);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return allTypes;
    }

    @Override
    public void savePlayerPreferences(UUID playerId, String preferences) {
        String query = "INSERT INTO announce_prefs (player_id, text) VALUES (?, ?) ON DUPLICATE KEY UPDATE text = ?";
        try {
            ensureConnection();
            try (PreparedStatement statement = connection.prepareStatement(query)) {
                statement.setString(1, playerId.toString());
                statement.setString(2, preferences);
                statement.setString(3, preferences);
                statement.executeUpdate();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public String getPlayerPreferences(UUID playerId) {
        String query = "SELECT text FROM announce_prefs WHERE player_id = ?";
        try {
            ensureConnection();
            try (PreparedStatement statement = connection.prepareStatement(query)) {
                statement.setString(1, playerId.toString());
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (resultSet.next()) {
                        return resultSet.getString("text");
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }
}