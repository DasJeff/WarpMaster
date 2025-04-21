package de.dasjeff.warpMaster.util;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

/**
 * Utility class for handling configuration in the plugin.
 */
public class ConfigUtil {
    private final JavaPlugin plugin;
    private FileConfiguration config;

    // Cached values
    private int defaultWarpLimit;
    private int warpCooldown;
    private boolean apiEnabled;
    private int apiPort;
    private String apiHost;
    private String apiKey;
    private List<String> apiIpWhitelist;
    private boolean apiRateLimitEnabled;
    private int apiRequestsPerMinute;
    private String databaseType;
    // DB credentials not cached for security
    private int databasePoolSize;
    private int databaseConnectionTimeout;
    private int databaseIdleTimeout;
    private int databaseMaxLifetime;
    private int databaseThreadPoolSize;

    /**
     * Creates a new ConfigUtil instance.
     *
     * @param plugin The plugin instance
     */
    public ConfigUtil(JavaPlugin plugin) {
        this.plugin = plugin;
        this.plugin.saveDefaultConfig();
        this.config = plugin.getConfig();
        // Load initial values into cache
        loadCachedValues();
    }

    /**
     * Gets the plugin configuration.
     *
     * @return The plugin configuration
     */
    public FileConfiguration getConfig() {
        return config;
    }

    /**
     * Reloads the plugin configuration.
     */
    public void reloadConfig() {
        plugin.reloadConfig();
        // Re-fetch the configuration object
        this.config = plugin.getConfig();
        // Reload cached values
        loadCachedValues();
        plugin.getLogger().info("WarpMaster configuration reloaded.");
    }

    /**
     * Loads or reloads values from the FileConfiguration into cached fields.
     */
    private void loadCachedValues() {
        defaultWarpLimit = getInt("warps.default-limit", 5);
        warpCooldown = getInt("warps.cooldown", 3);
        apiEnabled = getBoolean("api.enabled", true);
        apiPort = getInt("api.port", 8080);
        apiHost = getString("api.host", "0.0.0.0");
        apiKey = getString("api.security.api-key", "change-this-to-a-secure-key");
        apiIpWhitelist = getStringList("api.security.ip-whitelist");
        apiRateLimitEnabled = getBoolean("api.security.rate-limit.enabled", true);
        apiRequestsPerMinute = getInt("api.security.rate-limit.requests-per-minute", 60);
        databaseType = getString("database.type", "mysql");
        databasePoolSize = getInt("database.pool-size", 10);
        databaseConnectionTimeout = getInt("database.connection-timeout", 30000);
        databaseIdleTimeout = getInt("database.idle-timeout", 600000);
        databaseMaxLifetime = getInt("database.max-lifetime", 1800000);
        databaseThreadPoolSize = getInt("database.thread-pool-size", Math.max(2, Runtime.getRuntime().availableProcessors()));
    }

    /**
     * Gets a string from the configuration.
     *
     * @param path The path to the string
     * @param defaultValue The default value to return if the path doesn't exist
     * @return The string at the path, or the default value if not found
     */
    public String getString(String path, String defaultValue) {
        return config.getString(path, defaultValue);
    }

    /**
     * Gets an integer from the configuration.
     *
     * @param path The path to the integer
     * @param defaultValue The default value to return if the path doesn't exist
     * @return The integer at the path, or the default value if not found
     */
    public int getInt(String path, int defaultValue) {
        return config.getInt(path, defaultValue);
    }

    /**
     * Gets a boolean from the configuration.
     *
     * @param path The path to the boolean
     * @param defaultValue The default value to return if the path doesn't exist
     * @return The boolean at the path, or the default value if not found
     */
    public boolean getBoolean(String path, boolean defaultValue) {
        return config.getBoolean(path, defaultValue);
    }

    /**
     * Gets a list of strings from the configuration.
     *
     * @param path The path to the list
     * @return The list at the path, or an empty list if not found
     */
    public List<String> getStringList(String path) {
        return config.getStringList(path);
    }

    /**
     * Gets the default warp limit for players.
     *
     * @return The default warp limit
     */
    public int getDefaultWarpLimit() {
        return defaultWarpLimit;
    }

    /**
     * Gets the cooldown between warps in seconds.
     *
     * @return The cooldown in seconds
     */
    public int getWarpCooldown() {
        return warpCooldown;
    }

    /**
     * Checks if the API is enabled.
     *
     * @return True if the API is enabled, false otherwise
     */
    public boolean isApiEnabled() {
        return apiEnabled;
    }

    /**
     * Gets the API port.
     *
     * @return The API port
     */
    public int getApiPort() {
        return apiPort;
    }

    /**
     * Gets the API host.
     *
     * @return The API host
     */
    public String getApiHost() {
        return apiHost;
    }

    /**
     * Gets the API key.
     *
     * @return The API key
     */
    public String getApiKey() {
        return apiKey;
    }

    /**
     * Gets the IP whitelist for the API.
     *
     * @return The IP whitelist
     */
    public List<String> getApiIpWhitelist() {
        return apiIpWhitelist;
    }

    /**
     * Checks if rate limiting is enabled for the API.
     *
     * @return True if rate limiting is enabled, false otherwise
     */
    public boolean isApiRateLimitEnabled() {
        return apiRateLimitEnabled;
    }

    /**
     * Gets the maximum number of requests per minute for the API.
     *
     * @return The maximum number of requests per minute
     */
    public int getApiRequestsPerMinute() {
        return apiRequestsPerMinute;
    }

    /**
     * Gets the database type.
     *
     * @return The database type (mysql or sqlite)
     */
    public String getDatabaseType() {
        return databaseType;
    }

    /**
     * Gets the database host.
     *
     * @return The database host
     */
    public String getDatabaseHost() {
        return getString("database.host", "localhost");
    }

    /**
     * Gets the database port.
     *
     * @return The database port
     */
    public int getDatabasePort() {
        return getInt("database.port", 3306);
    }

    /**
     * Gets the database name.
     *
     * @return The database name
     */
    public String getDatabaseName() {
        return getString("database.database", "warpmaster");
    }

    /**
     * Gets the database username.
     *
     * @return The database username
     */
    public String getDatabaseUsername() {
        return getString("database.username", "root");
    }

    /**
     * Gets the database password.
     *
     * @return The database password
     */
    public String getDatabasePassword() {
        return getString("database.password", "password");
    }

    /**
     * Gets the database connection pool size.
     *
     * @return The connection pool size
     */
    public int getDatabasePoolSize() {
        // Return cached value
        return databasePoolSize;
    }

    /**
     * Gets the database connection timeout in milliseconds.
     *
     * @return The connection timeout
     */
    public int getDatabaseConnectionTimeout() {
        // Return cached value
        return databaseConnectionTimeout;
    }

    /**
     * Gets the database idle timeout in milliseconds.
     *
     * @return The idle timeout
     */
    public int getDatabaseIdleTimeout() {
        // Return cached value
        return databaseIdleTimeout;
    }

    /**
     * Gets the database maximum lifetime in milliseconds.
     *
     * @return The maximum lifetime
     */
    public int getDatabaseMaxLifetime() {
        // Return cached value
        return databaseMaxLifetime;
    }

    /**
     * Gets the desired thread pool size for database operations.
     *
     * @return The database thread pool size
     */
    public int getDatabaseThreadPoolSize() {
        // Return cached value
        return databaseThreadPoolSize;
    }

    /**
     * Checks if the current database configuration uses default credentials.
     *
     * @return True if default credentials are used, false otherwise
     */
    public boolean isDefaultDatabaseConfig() {
        // Read directly for security check
        return "localhost".equals(getString("database.host", "localhost")) &&
               "root".equals(getString("database.username", "root")) &&
               "password".equals(getString("database.password", "password"));
    }

    /**
     * Checks if the default API key is being used.
     *
     * @return True if the default API key is used, false otherwise
     */
    public boolean isDefaultApiKey() {
        // Use cached value for this check
        return "change-this-to-a-secure-key".equals(apiKey);
    }
}
