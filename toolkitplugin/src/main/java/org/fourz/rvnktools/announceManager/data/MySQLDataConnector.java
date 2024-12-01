package org.fourz.rvnktools.announceManager.data;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

import org.fourz.rvnktools.announceManager.AnnounceType;
import org.fourz.rvnktools.announceManager.Announcement;

public class MySQLDataConnector implements DataStore {
    private final String url;
    private final String username;
    private final String password;
    private final String database; // Add database field
    private Connection connection;

    public MySQLDataConnector(String host, int port, String database, String username, String password, boolean useSSL) {
        this.url = "jdbc:mysql://" + host + ":" + port + "/" + database + "?useSSL=" + useSSL;
        this.username = username;
        this.password = password;
        this.database = database; // Store database name
    }

    @Override
    public void connect() {
        try {
            connection = DriverManager.getConnection(url, username, password);
            // Initialize tables immediately after connection
            initializeTables();
        } catch (SQLException e) {
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

    @Override
    public void saveAnnouncement(Announcement announcement) {
        String query = "INSERT INTO announcements (id, text, type, recurrence, owner, permission, date, time, expiration) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, announcement.getId());
            statement.setString(2, announcement.getText());
            statement.setString(3, announcement.getType());
            statement.setString(4, announcement.getRecurrence());
            statement.setString(5, announcement.getOwner());
            statement.setString(6, announcement.getPermission());
            statement.setDate(7, announcement.getDate() != null ? Date.valueOf(announcement.getDate()) : null);
            statement.setTime(8, announcement.getTime() != null ? Time.valueOf(announcement.getTime()) : null);
            statement.setTimestamp(9, announcement.getExpiration() != null ? Timestamp.valueOf(announcement.getExpiration()) : null);
            statement.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void deleteAnnouncement(String id) {
        String query = "DELETE FROM announcements WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, id);
            statement.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public List<Announcement> loadAnnouncements() {
        List<Announcement> announcements = new ArrayList<>();
        String query = "SELECT * FROM announcements";
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(query)) {
            while (resultSet.next()) {
                Announcement announcement = new Announcement();
                announcement.setId(resultSet.getString("id"));
                announcement.setText(resultSet.getString("text"));
                announcement.setType(resultSet.getString("type"));
                announcement.setRecurrence(resultSet.getString("recurrence"));
                announcement.setOwner(resultSet.getString("owner"));
                announcement.setPermission(resultSet.getString("permission"));
                announcement.setDate(resultSet.getDate("date") != null ? resultSet.getDate("date").toLocalDate() : null);
                announcement.setTime(resultSet.getTime("time") != null ? resultSet.getTime("time").toLocalTime() : null);
                announcement.setExpiration(resultSet.getTimestamp("expiration") != null ? resultSet.getTimestamp("expiration").toLocalDateTime() : null);
                announcements.add(announcement);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return announcements;
    }

    @Override
    public void saveAnnounceType(AnnounceType announceType) {
        String query = "INSERT INTO announce_types (id, prefix, suffix, permission, listing_fee) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, announceType.getId());
            statement.setString(2, announceType.getPrefix());
            statement.setString(3, announceType.getSuffix());
            statement.setString(4, announceType.getPermission());
            statement.setDouble(5, announceType.getListingFee() != null ? announceType.getListingFee() : 0.0);
            statement.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public List<AnnounceType> loadAnnounceTypes() {
        List<AnnounceType> announceTypes = new ArrayList<>();
        String query = "SELECT * FROM announce_types";
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
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return announceTypes;
    }

    @Override
    public void initializeTables() {
        try {
            DatabaseMetaData metaData = connection.getMetaData();
            ResultSet tables;
            
            // Check if announcements table exists
            tables = metaData.getTables(database, null, "announcements", null);
            if (!tables.next()) {
                Statement stmt = connection.createStatement();
                String createAnnouncementsTable = "CREATE TABLE announcements (" +
                    "id VARCHAR(64) PRIMARY KEY," +
                    "text TEXT NOT NULL," +
                    "type VARCHAR(32) NOT NULL," +
                    "recurrence VARCHAR(32)," +
                    "owner VARCHAR(64)," +
                    "permission VARCHAR(128)," +
                    "date DATE," +
                    "time TIME," +
                    "expiration DATETIME" +
                    ")";
                stmt.executeUpdate(createAnnouncementsTable);
            }
            
            // Check if announce_types table exists
            tables = metaData.getTables(database, null, "announce_types", null);
            if (!tables.next()) {
                Statement stmt = connection.createStatement();
                String createAnnounceTypesTable = "CREATE TABLE announce_types (" +
                    "id VARCHAR(64) PRIMARY KEY," +
                    "prefix VARCHAR(128)," + 
                    "suffix VARCHAR(128)," +
                    "permission VARCHAR(128)," +
                    "listing_fee DOUBLE" +
                    ")";
                stmt.executeUpdate(createAnnounceTypesTable);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public boolean announcementExists(String id) {
        String query = "SELECT COUNT(*) FROM announcements WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, id);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    int count = resultSet.getInt(1);
                    return count > 0;
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }
}