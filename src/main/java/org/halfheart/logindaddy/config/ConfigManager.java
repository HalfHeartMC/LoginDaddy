package org.halfheart.logindaddy.config;

import org.halfheart.logindaddy.LoginDaddy;

import java.io.*;
import java.util.Properties;

public class ConfigManager {
    private static final String CONFIG_FILE = "logindaddy.properties";

    private String databaseType = "mysql";
    private String mysqlHost = "localhost";
    private int mysqlPort = 3306;
    private String mysqlDatabase = "assets/logindaddy";
    private String mysqlUsername = "root";
    private String mysqlPassword = "";

    public void loadConfig() {
        File configFile = new File(CONFIG_FILE);

        if (!configFile.exists()) {
            createDefaultConfig();
            LoginDaddy.LOGGER.info("Created default config file: " + CONFIG_FILE);
            return;
        }

        try (FileInputStream fis = new FileInputStream(configFile)) {
            Properties props = new Properties();
            props.load(fis);

            databaseType = props.getProperty("database.type", "sqlite");
            mysqlHost = props.getProperty("mysql.host", "localhost");
            mysqlPort = Integer.parseInt(props.getProperty("mysql.port", "3306"));
            mysqlDatabase = props.getProperty("mysql.database", "assets/logindaddy");
            mysqlUsername = props.getProperty("mysql.username", "root");
            mysqlPassword = props.getProperty("mysql.password", "");

            LoginDaddy.LOGGER.info("Loaded config: Database type = " + databaseType);
        } catch (IOException e) {
            LoginDaddy.LOGGER.error("Failed to load config file", e);
            createDefaultConfig();
        }
    }

    private void createDefaultConfig() {
        Properties props = new Properties();
        props.setProperty("database.type", "sqlite");
        props.setProperty("mysql.host", "localhost");
        props.setProperty("mysql.port", "3306");
        props.setProperty("mysql.database", "assets/logindaddy");
        props.setProperty("mysql.username", "root");
        props.setProperty("mysql.password", "");

        try (FileOutputStream fos = new FileOutputStream(CONFIG_FILE)) {
            props.store(fos, "LoginDaddy Configuration\n# database.type: sqlite or mysql");
        } catch (IOException e) {
            LoginDaddy.LOGGER.error("Failed to create default config", e);
        }
    }

    public String getDatabaseType() { return databaseType; }
    public String getMysqlHost() { return mysqlHost; }
    public int getMysqlPort() { return mysqlPort; }
    public String getMysqlDatabase() { return mysqlDatabase; }
    public String getMysqlUsername() { return mysqlUsername; }
    public String getMysqlPassword() { return mysqlPassword; }

    public String getMysqlJdbcUrl() {
        return "jdbc:mysql://" + mysqlHost + ":" + mysqlPort + "/" + mysqlDatabase + "?useSSL=false&allowPublicKeyRetrieval=true";
    }
}