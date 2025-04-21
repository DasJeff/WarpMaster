package de.dasjeff.warpMaster.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import de.dasjeff.warpMaster.WarpMaster;
import de.dasjeff.warpMaster.util.ConfigUtil;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * Manages database connections and initialization.
 */
public class DatabaseManager {
    private final WarpMaster plugin;
    private final ConfigUtil configUtil;
    private HikariDataSource dataSource;
    private ExecutorService databaseExecutor;

    /**
     * Creates a new DatabaseManager instance.
     *
     * @param plugin The plugin instance
     * @param configUtil The configuration utility
     */
    public DatabaseManager(WarpMaster plugin, ConfigUtil configUtil) {
        this.plugin = plugin;
        this.configUtil = configUtil;
    }

    /**
     * Initializes the database connection.
     *
     * @return True if the connection was initialized successfully, false otherwise
     */
    public boolean initialize() {
        // Check for default MySQL configuration
        if ("mysql".equalsIgnoreCase(configUtil.getDatabaseType()) &&
            configUtil.isDefaultDatabaseConfig()) {
            plugin.getLogger().severe("""
            --------------------------------------------------
            WarpMaster DETECTED DEFAULT MYSQL CONFIGURATION!
            Please configure your MySQL database settings in
            plugins/WarpMaster/config.yml
            and ensure the database server is running.
            Plugin will not enable until configured.
            --------------------------------------------------""");
            return false;
        }

        // Check for default API key
        if (configUtil.isDefaultApiKey()) {
             plugin.getLogger().warning("""
            --------------------------------------------------
            WarpMaster DETECTED DEFAULT API KEY!
            Please set a secure 'api.security.api-key' in
            plugins/WarpMaster/config.yml
            --------------------------------------------------""");
            // Continue initialization, but log warning
        }

        try {
            // Initialize executor first
            databaseExecutor = Executors.newFixedThreadPool(configUtil.getDatabaseThreadPoolSize());
            plugin.getLogger().info("Initialized database thread pool with size: " + configUtil.getDatabaseThreadPoolSize());

            setupDataSource();
            createTables();
            return true;
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to initialize database", e);
            return false;
        }
    }

    /**
     * Sets up the data source for database connections.
     */
    private void setupDataSource() {
        HikariConfig config = new HikariConfig();
        
        String databaseType = configUtil.getDatabaseType();
        boolean isMySql = "mysql".equalsIgnoreCase(databaseType);

        if (isMySql) {
            setupMySqlDataSource(config);
            // Set MySQL specific pool size and validation query
            config.setMaximumPoolSize(configUtil.getDatabasePoolSize());
            config.setConnectionTestQuery("SELECT 1");
        } else {
            setupSqliteDataSource(config);
            // Ensure SQLite pool size is ALWAYS 1, regardless of config, and set validation query
            config.setMaximumPoolSize(1);
            config.setConnectionTestQuery("SELECT 1");
        }
        
        // HikariCP settings (timeouts, lifetime)
        // Pool size is set based on DB type
        config.setConnectionTimeout(configUtil.getDatabaseConnectionTimeout());
        config.setIdleTimeout(configUtil.getDatabaseIdleTimeout());
        config.setMaxLifetime(configUtil.getDatabaseMaxLifetime());
        
        dataSource = new HikariDataSource(config);
    }

    /**
     * Sets up a MySQL data source.
     *
     * @param config The HikariCP configuration
     */
    private void setupMySqlDataSource(HikariConfig config) {
        config.setJdbcUrl(String.format("jdbc:mysql://%s:%d/%s?useSSL=false&serverTimezone=UTC",
                configUtil.getDatabaseHost(),
                configUtil.getDatabasePort(),
                configUtil.getDatabaseName()));
        config.setDriverClassName("com.mysql.cj.jdbc.Driver");
        config.setUsername(configUtil.getDatabaseUsername());
        config.setPassword(configUtil.getDatabasePassword());
    }

    /**
     * Sets up an SQLite data source.
     *
     * @param config The HikariCP configuration
     */
    private void setupSqliteDataSource(HikariConfig config) {
        File databaseFile = new File(plugin.getDataFolder(), "database.db");
        config.setJdbcUrl("jdbc:sqlite:" + databaseFile.getAbsolutePath());
        config.setDriverClassName("org.sqlite.JDBC");
    }

    /**
     * Creates the necessary tables in the database.
     *
     * @throws SQLException If an SQL error occurs
     */
    private void createTables() throws SQLException {
        String databaseType = configUtil.getDatabaseType();
        boolean isMySql = "mysql".equalsIgnoreCase(databaseType);
        
        try (Connection connection = getConnection()) {
            // Create warps table
            String warpsTable = isMySql ? 
                    "CREATE TABLE IF NOT EXISTS warps (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY," +
                    "owner_uuid VARCHAR(36) NOT NULL," +
                    "name VARCHAR(32) NOT NULL," +
                    "world_name VARCHAR(64) NOT NULL," +
                    "x DOUBLE NOT NULL," +
                    "y DOUBLE NOT NULL," +
                    "z DOUBLE NOT NULL," +
                    "yaw FLOAT NOT NULL," +
                    "pitch FLOAT NOT NULL," +
                    "created_at BIGINT NOT NULL," +
                    "INDEX idx_owner_uuid (owner_uuid)," +
                    "UNIQUE INDEX idx_owner_name (owner_uuid, name)" +
                    ")" :
                    "CREATE TABLE IF NOT EXISTS warps (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "owner_uuid TEXT NOT NULL," +
                    "name TEXT NOT NULL," +
                    "world_name TEXT NOT NULL," +
                    "x REAL NOT NULL," +
                    "y REAL NOT NULL," +
                    "z REAL NOT NULL," +
                    "yaw REAL NOT NULL," +
                    "pitch REAL NOT NULL," +
                    "created_at INTEGER NOT NULL," +
                    "UNIQUE(owner_uuid, name)" +
                    ")";
            
            try (PreparedStatement statement = connection.prepareStatement(warpsTable)) {
                statement.executeUpdate();
            }
            
            // Create player_data table
            String playerDataTable = isMySql ?
                    "CREATE TABLE IF NOT EXISTS player_data (" +
                    "uuid VARCHAR(36) PRIMARY KEY," +
                    "warp_limit INT NOT NULL DEFAULT 5," +
                    "last_warp_time BIGINT NOT NULL DEFAULT 0" +
                    ")" :
                    "CREATE TABLE IF NOT EXISTS player_data (" +
                    "uuid TEXT PRIMARY KEY," +
                    "warp_limit INTEGER NOT NULL DEFAULT 5," +
                    "last_warp_time INTEGER NOT NULL DEFAULT 0" +
                    ")";
            
            try (PreparedStatement statement = connection.prepareStatement(playerDataTable)) {
                statement.executeUpdate();
            }
            
            // Create indices for SQLite (MySQL indices are created in the table definition)
            if (!isMySql) {
                String ownerUuidIndex = "CREATE INDEX IF NOT EXISTS idx_owner_uuid ON warps (owner_uuid)";
                try (PreparedStatement statement = connection.prepareStatement(ownerUuidIndex)) {
                    statement.executeUpdate();
                }
            }
        }
    }

    /**
     * Gets a connection from the connection pool.
     *
     * @return A database connection
     * @throws SQLException If a connection cannot be obtained
     */
    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    /**
     * Closes the data source and all connections.
     */
    public void close() {
        // Shutdown executor
        if (databaseExecutor != null) {
            databaseExecutor.shutdown();
            try {
                if (!databaseExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    databaseExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                databaseExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        // Close datasource
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }

    /**
     * Gets the database executor service.
     *
     * @return The executor service for database operations
     */
    public ExecutorService getDatabaseExecutor() {
        return databaseExecutor;
    }

    /**
     * Executes a given function within a database transaction.
     * Handles connection acquisition, commit, rollback, and closing.
     *
     * @param function The function to execute, accepting a Connection and returning a result.
     * @param <T> The type of the result.
     * @return A CompletableFuture that completes with the result of the function, or exceptionally on error.
     */
     public <T> CompletableFuture<T> executeInTransaction(TransactionFunction<T> function) {
         return CompletableFuture.supplyAsync(() -> {
             Connection connection = null;
             try {
                 connection = getConnection();
                 connection.setAutoCommit(false); // Start transaction
                 
                 T result = function.apply(connection);
                 
                 connection.commit(); // Commit transaction
                 return result;
                 
             } catch (SQLException e) {
                 // Rollback on SQL error
                 if (connection != null) {
                     try {
                         connection.rollback();
                         plugin.getLogger().log(Level.WARNING, "Transaction rolled back due to SQLException.", e);
                     } catch (SQLException rollbackEx) {
                         plugin.getLogger().log(Level.SEVERE, "Failed to rollback transaction.", rollbackEx);
                     }
                 }
                 // Re-throw as RuntimeException to fail the CompletableFuture
                 throw new RuntimeException("Transaction failed", e);
             } catch (Exception e) {
                 // Rollback on any other exception during function execution
                 if (connection != null) {
                     try {
                         connection.rollback();
                          plugin.getLogger().log(Level.WARNING, "Transaction rolled back due to Exception.", e);
                     } catch (SQLException rollbackEx) {
                         plugin.getLogger().log(Level.SEVERE, "Failed to rollback transaction.", rollbackEx);
                     }
                 }
                 // Re-throw as RuntimeException
                 throw new RuntimeException("Transaction failed", e);
             } finally {
                 // Ensure connection is closed and auto-commit is reset
                 if (connection != null) {
                     try {
                         if (!connection.getAutoCommit()) {
                              connection.setAutoCommit(true); // Reset auto-commit
                         }
                         connection.close();
                     } catch (SQLException closeEx) {
                         plugin.getLogger().log(Level.SEVERE, "Failed to close connection after transaction.", closeEx);
                     }
                 }
             }
         }, databaseExecutor); // Run the whole transaction logic on the DB executor
     }

    /**
     * Functional interface for operations within a transaction.
     *
     * @param <T> The return type of the function.
     */
    @FunctionalInterface
    public interface TransactionFunction<T> {
        /**
         * Applies this function to the given connection.
         *
         * @param connection The database connection within the transaction.
         * @return The function result.
         * @throws Exception if unable to compute a result.
         */
        T apply(Connection connection) throws Exception;
    }
}
