package org.halfheart.logindaddy.database;

import org.halfheart.logindaddy.LoginDaddy;
import org.halfheart.logindaddy.config.ConfigManager;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.SecureRandom;
import java.sql.*;
import java.util.Base64;

public class DatabaseManager {
    private Connection connection;
    private final String databaseType;

    private static final int ITERATIONS = 65536;
    private static final int KEY_LENGTH  = 256;
    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";

    public DatabaseManager(ConfigManager config) {
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
        Class.forName("org.sqlite.JDBC");
        connection = DriverManager.getConnection("jdbc:sqlite:logindaddy.db");
    }

    private void connectMySQL(ConfigManager config) throws Exception {
        Class.forName("com.mysql.cj.jdbc.Driver");
        connection = DriverManager.getConnection(
                config.getMysqlJdbcUrl(), config.getMysqlUsername(), config.getMysqlPassword());
    }

    private void createTables() {
        String whitelist = databaseType.equalsIgnoreCase("mysql")
                ? "CREATE TABLE IF NOT EXISTS whitelist (username VARCHAR(255) PRIMARY KEY)"
                : "CREATE TABLE IF NOT EXISTS whitelist (username TEXT PRIMARY KEY COLLATE NOCASE)";

        String users = databaseType.equalsIgnoreCase("mysql")
                ? "CREATE TABLE IF NOT EXISTS users (username VARCHAR(255) PRIMARY KEY, password_hash TEXT NOT NULL)"
                : "CREATE TABLE IF NOT EXISTS users (username TEXT PRIMARY KEY COLLATE NOCASE, password_hash TEXT NOT NULL)";

        try (Statement stmt = connection.createStatement()) {
            stmt.execute(whitelist);
            stmt.execute(users);
        } catch (SQLException e) {
            LoginDaddy.LOGGER.error("Failed to create tables", e);
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
        String sql = "SELECT password_hash FROM users WHERE LOWER(username) = LOWER(?)";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
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