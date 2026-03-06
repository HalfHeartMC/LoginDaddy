package org.halfheart.logindaddy.database;

import net.fabricmc.loader.api.FabricLoader;
import org.halfheart.logindaddy.LoginDaddy;
import org.halfheart.logindaddy.config.ConfigManager;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.sql.*;
import java.util.Base64;

public class DatabaseManager {
    private Connection connection;
    private final String databaseType;
    private final ConfigManager config;

    private static final int ITERATIONS = 65536;
    private static final int KEY_LENGTH  = 256;
    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";

    public static class PlayerData {
        public final double x, y, z;
        public final float yaw, pitch;
        public final float health;
        public final int food;
        public final float saturation;
        public final boolean died;
        public final String dimension;

        public PlayerData(double x, double y, double z, float yaw, float pitch,
                          float health, int food, float saturation, boolean died,
                          String dimension) {
            this.x = x; this.y = y; this.z = z;
            this.yaw = yaw; this.pitch = pitch;
            this.health = health; this.food = food; this.saturation = saturation;
            this.died = died;
            this.dimension = dimension != null ? dimension : "minecraft:overworld";
        }
    }

    public DatabaseManager(ConfigManager config) {
        this.config = config;
        this.databaseType = config.getDatabaseType();
        try {
            if (databaseType.equalsIgnoreCase("mysql")) {
                connectMySQL(config);
            } else {
                connectSQLite();
            }
            createTables();
            LoginDaddy.LOGGER.info("Database connected ({})", databaseType);
        } catch (Exception e) {
            LoginDaddy.LOGGER.error("Database connection failed: ", e);
        }
    }

    private void connectSQLite() throws Exception {
        Path dbDir = FabricLoader.getInstance().getConfigDir().resolve("LoginDaddy");
        try {
            Files.createDirectories(dbDir);
        } catch (IOException e) {
            LoginDaddy.LOGGER.error("Failed to create config directory for SQLite", e);
        }
        Class.forName("org.sqlite.JDBC");
        connection = DriverManager.getConnection("jdbc:sqlite:" + dbDir.resolve("logindaddy.db"));
    }

    private void connectMySQL(ConfigManager config) throws Exception {
        Class.forName("com.mysql.cj.jdbc.Driver");
        connection = DriverManager.getConnection(
                config.getMysqlJdbcUrl(), config.getMysqlUsername(), config.getMysqlPassword());
    }

    private void createTables() {
        boolean mysql = databaseType.equalsIgnoreCase("mysql");

        String whitelist = mysql
                ? "CREATE TABLE IF NOT EXISTS whitelist (username VARCHAR(255) PRIMARY KEY)"
                : "CREATE TABLE IF NOT EXISTS whitelist (username TEXT PRIMARY KEY COLLATE NOCASE)";

        String users = mysql
                ? "CREATE TABLE IF NOT EXISTS users (username VARCHAR(255) PRIMARY KEY, password_hash TEXT NOT NULL)"
                : "CREATE TABLE IF NOT EXISTS users (username TEXT PRIMARY KEY COLLATE NOCASE, password_hash TEXT NOT NULL)";

        String playerData = mysql
                ? "CREATE TABLE IF NOT EXISTS player_data (" +
                "uuid VARCHAR(36) PRIMARY KEY, " +
                "x DOUBLE NOT NULL, y DOUBLE NOT NULL, z DOUBLE NOT NULL, " +
                "yaw FLOAT NOT NULL, pitch FLOAT NOT NULL, " +
                "health FLOAT NOT NULL, food INT NOT NULL, saturation FLOAT NOT NULL, " +
                "died TINYINT(1) NOT NULL DEFAULT 0, " +
                "dimension VARCHAR(100) NOT NULL DEFAULT 'minecraft:overworld')"
                : "CREATE TABLE IF NOT EXISTS player_data (" +
                "uuid TEXT PRIMARY KEY, " +
                "x REAL NOT NULL, y REAL NOT NULL, z REAL NOT NULL, " +
                "yaw REAL NOT NULL, pitch REAL NOT NULL, " +
                "health REAL NOT NULL, food INTEGER NOT NULL, saturation REAL NOT NULL, " +
                "died INTEGER NOT NULL DEFAULT 0, " +
                "dimension TEXT NOT NULL DEFAULT 'minecraft:overworld')";

        try (Statement stmt = connection.createStatement()) {
            stmt.execute(whitelist);
            stmt.execute(users);
            stmt.execute(playerData);

            if (mysql) {
                silentAlterAdd("ALTER TABLE player_data ADD COLUMN dimension VARCHAR(100) NOT NULL DEFAULT 'minecraft:overworld'");
            } else {
                silentAlterAdd("ALTER TABLE player_data ADD COLUMN dimension TEXT NOT NULL DEFAULT 'minecraft:overworld'");
            }
        } catch (SQLException e) {
            LoginDaddy.LOGGER.error("Failed to create tables", e);
        }
    }

    private void silentAlterAdd(String sql) {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(sql);
        } catch (SQLException ignored) {
        }
    }

    public void savePlayerData(String uuid,
                               double x, double y, double z, float yaw, float pitch,
                               float health, int food, float saturation,
                               boolean died, String dimension) {
        ensureConnection();
        if (connection == null) return;

        String sql = databaseType.equalsIgnoreCase("mysql")
                ? "INSERT INTO player_data " +
                "(uuid,x,y,z,yaw,pitch,health,food,saturation,died,dimension) " +
                "VALUES (?,?,?,?,?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE " +
                "x=VALUES(x), y=VALUES(y), z=VALUES(z), " +
                "yaw=VALUES(yaw), pitch=VALUES(pitch), " +
                "health=VALUES(health), food=VALUES(food), saturation=VALUES(saturation), " +
                "died=VALUES(died), dimension=VALUES(dimension)"
                : "INSERT OR REPLACE INTO player_data " +
                "(uuid,x,y,z,yaw,pitch,health,food,saturation,died,dimension) " +
                "VALUES (?,?,?,?,?,?,?,?,?,?,?)";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, uuid);
            stmt.setDouble(2, x);
            stmt.setDouble(3, y);
            stmt.setDouble(4, z);
            stmt.setFloat(5, yaw);
            stmt.setFloat(6, pitch);
            stmt.setFloat(7, health);
            stmt.setInt(8, food);
            stmt.setFloat(9, saturation);
            stmt.setInt(10, died ? 1 : 0);
            stmt.setString(11, dimension != null ? dimension : "minecraft:overworld");
            stmt.executeUpdate();
        } catch (SQLException e) {
            LoginDaddy.LOGGER.error("Failed to save player data", e);
        }
    }

    public PlayerData loadAndDeletePlayerData(String uuid) {
        ensureConnection();
        if (connection == null) return null;
        try {
            PlayerData data = null;
            try (PreparedStatement stmt = connection.prepareStatement(
                    "SELECT x,y,z,yaw,pitch,health,food,saturation,died,dimension " +
                            "FROM player_data WHERE uuid=?")) {
                stmt.setString(1, uuid);
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    data = new PlayerData(
                            rs.getDouble("x"),    rs.getDouble("y"),    rs.getDouble("z"),
                            rs.getFloat("yaw"),   rs.getFloat("pitch"),
                            rs.getFloat("health"),rs.getInt("food"),    rs.getFloat("saturation"),
                            rs.getInt("died") == 1,
                            rs.getString("dimension"));
                }
            }
            if (data != null) {
                try (PreparedStatement del = connection.prepareStatement(
                        "DELETE FROM player_data WHERE uuid=?")) {
                    del.setString(1, uuid);
                    del.executeUpdate();
                }
            }
            return data;
        } catch (SQLException e) {
            LoginDaddy.LOGGER.error("Failed to load player data", e);
            return null;
        }
    }

    public boolean isWhitelisted(String username) {
        return queryExists("SELECT username FROM whitelist WHERE LOWER(username) = LOWER(?)", username);
    }

    public boolean addToWhitelist(String username) {
        String sql = databaseType.equalsIgnoreCase("mysql")
                ? "INSERT IGNORE INTO whitelist (username) VALUES (?)"
                : "INSERT OR IGNORE INTO whitelist (username) VALUES (?)";
        return executeUpdate(sql, username) > 0;
    }

    public boolean removeFromWhitelist(String username) {
        return executeUpdate("DELETE FROM whitelist WHERE LOWER(username) = LOWER(?)", username) > 0;
    }

    public java.util.List<String> getWhitelistedPlayers() {
        java.util.List<String> players = new java.util.ArrayList<>();
        ensureConnection();
        if (connection == null) return players;
        try (PreparedStatement stmt = connection.prepareStatement(
                "SELECT username FROM whitelist ORDER BY username ASC")) {
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) players.add(rs.getString("username"));
        } catch (SQLException e) {
            LoginDaddy.LOGGER.error("Error fetching whitelist", e);
        }
        return players;
    }

    public boolean isRegistered(String username) {
        return queryExists("SELECT username FROM users WHERE LOWER(username) = LOWER(?)", username);
    }

    public boolean registerUser(String username, String password) {
        if (isRegistered(username)) return false;
        String sql = databaseType.equalsIgnoreCase("mysql")
                ? "INSERT IGNORE INTO users (username, password_hash) VALUES (?, ?)"
                : "INSERT OR IGNORE INTO users (username, password_hash) VALUES (?, ?)";
        return executeUpdate(sql, username, hashPassword(password)) > 0;
    }

    public boolean verifyPassword(String username, String password) {
        ensureConnection();
        if (connection == null) return false;
        try (PreparedStatement stmt = connection.prepareStatement(
                "SELECT password_hash FROM users WHERE LOWER(username) = LOWER(?)")) {
            stmt.setString(1, username);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return checkPassword(password, rs.getString("password_hash"));
        } catch (SQLException e) {
            LoginDaddy.LOGGER.error("Error verifying password", e);
        }
        return false;
    }

    public boolean resetPassword(String username, String newPassword) {
        return executeUpdate(
                "UPDATE users SET password_hash = ? WHERE LOWER(username) = LOWER(?)",
                hashPassword(newPassword), username) > 0;
    }

    private boolean queryExists(String sql, String param) {
        ensureConnection();
        if (connection == null) return false;
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, param);
            return stmt.executeQuery().next();
        } catch (SQLException e) {
            LoginDaddy.LOGGER.error("Query error", e);
            return false;
        }
    }

    private int executeUpdate(String sql, String... params) {
        ensureConnection();
        if (connection == null) return 0;
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) stmt.setString(i + 1, params[i]);
            return stmt.executeUpdate();
        } catch (SQLException e) {
            LoginDaddy.LOGGER.error("Update error", e);
            return 0;
        }
    }

    private String hashPassword(String password) {
        try {
            SecureRandom random = new SecureRandom();
            byte[] salt = new byte[16];
            random.nextBytes(salt);
            PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_LENGTH);
            SecretKeyFactory factory = SecretKeyFactory.getInstance(ALGORITHM);
            byte[] hash = factory.generateSecret(spec).getEncoded();
            spec.clearPassword();
            return ITERATIONS + ":" + Base64.getEncoder().encodeToString(salt)
                    + ":" + Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new RuntimeException("Failed to hash password", e);
        }
    }

    private boolean checkPassword(String password, String stored) {
        try {
            String[] parts = stored.split(":");
            if (parts.length != 3) return false;
            int iterations  = Integer.parseInt(parts[0]);
            byte[] salt     = Base64.getDecoder().decode(parts[1]);
            byte[] expected = Base64.getDecoder().decode(parts[2]);
            PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, expected.length * 8);
            SecretKeyFactory factory = SecretKeyFactory.getInstance(ALGORITHM);
            byte[] actual = factory.generateSecret(spec).getEncoded();
            spec.clearPassword();
            int diff = 0;
            for (int i = 0; i < actual.length; i++) diff |= actual[i] ^ expected[i];
            return diff == 0;
        } catch (Exception e) {
            LoginDaddy.LOGGER.error("Error verifying hash", e);
            return false;
        }
    }

    public void ensureConnection() {
        try {
            if (connection != null && !connection.isClosed() && connection.isValid(2)) return;
            LoginDaddy.LOGGER.warn("Database connection lost, reconnecting...");
            try {
                if (connection != null && !connection.isClosed()) connection.close();
            } catch (SQLException ignored) {}
            if (databaseType.equalsIgnoreCase("mysql")) {
                connectMySQL(config);
            } else {
                connectSQLite();
            }
            createTables();
            LoginDaddy.LOGGER.info("Database reconnected ({})", databaseType);
        } catch (Exception e) {
            LoginDaddy.LOGGER.error("Database reconnection failed: ", e);
        }
    }

    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                LoginDaddy.LOGGER.info("Database connection closed");
            }
        } catch (SQLException e) {
            LoginDaddy.LOGGER.error("Error closing database", e);
        }
    }
}