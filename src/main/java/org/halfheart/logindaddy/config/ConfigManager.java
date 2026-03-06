package org.halfheart.logindaddy.config;

import net.fabricmc.loader.api.FabricLoader;
import org.halfheart.logindaddy.LoginDaddy;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public class ConfigManager {
    private static final Path CONFIG_DIR = FabricLoader.getInstance().getConfigDir().resolve("LoginDaddy");
    private static final Path CONFIG_FILE = CONFIG_DIR.resolve("logindaddy.properties");

    private String databaseType = "sqlite";
    private String mysqlHost = "localhost";
    private int mysqlPort = 3306;
    private String mysqlDatabase = "logindaddy";
    private String mysqlUsername = "root";
    private String mysqlPassword = "";
    private String serverKey = "";

    public void loadConfig() {
        try {
            Files.createDirectories(CONFIG_DIR);
        } catch (IOException e) {
            LoginDaddy.LOGGER.error("Failed to create config directory", e);
        }

        File configFile = CONFIG_FILE.toFile();
        if (!configFile.exists()) {
            createDefaultConfig();
            LoginDaddy.LOGGER.info("Created default config at: " + CONFIG_FILE);
            return;
        }

        try (FileInputStream fis = new FileInputStream(configFile)) {
            Properties props = new Properties();
            props.load(fis);

            databaseType  = props.getProperty("database.type", "sqlite");
            mysqlHost     = props.getProperty("mysql.host", "localhost");
            mysqlPort     = Integer.parseInt(props.getProperty("mysql.port", "3306"));
            mysqlDatabase = props.getProperty("mysql.database", "logindaddy");
            mysqlUsername = props.getProperty("mysql.username", "root");
            mysqlPassword = props.getProperty("mysql.password", "");
            serverKey     = props.getProperty("server.key", "");

            LoginDaddy.LOGGER.info("Loaded config: database={}, key={}", databaseType,
                    serverKey.isEmpty() ? "(none)" : "(set)");
        } catch (IOException e) {
            LoginDaddy.LOGGER.error("Failed to load config file", e);
            createDefaultConfig();
        }
    }

    private void createDefaultConfig() {
        try {
            Files.createDirectories(CONFIG_DIR);
        } catch (IOException e) {
            LoginDaddy.LOGGER.error("Failed to create config directory", e);
        }

        Properties props = new Properties();
        props.setProperty("database.type", "sqlite");
        props.setProperty("mysql.host", "localhost");
        props.setProperty("mysql.port", "3306");
        props.setProperty("mysql.database", "logindaddy");
        props.setProperty("mysql.username", "root");
        props.setProperty("mysql.password", "");
        props.setProperty("server.key", "");

        try (FileOutputStream fos = new FileOutputStream(CONFIG_FILE.toFile())) {
            props.store(fos, "LoginDaddy Configuration");
        } catch (IOException e) {
            LoginDaddy.LOGGER.error("Failed to create default config", e);
        }
    }

    public String getDatabaseType()  { return databaseType; }
    public String getMysqlHost()     { return mysqlHost; }
    public int    getMysqlPort()     { return mysqlPort; }
    public String getMysqlDatabase() { return mysqlDatabase; }
    public String getMysqlUsername() { return mysqlUsername; }
    public String getMysqlPassword() { return mysqlPassword; }
    public String getServerKey()     { return serverKey; }

    public String getMysqlJdbcUrl() {
        return "jdbc:mysql://" + mysqlHost + ":" + mysqlPort + "/" + mysqlDatabase
                + "?useSSL=false&allowPublicKeyRetrieval=true";
    }
}