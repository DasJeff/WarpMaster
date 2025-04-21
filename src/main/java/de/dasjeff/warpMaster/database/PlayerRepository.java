package de.dasjeff.warpMaster.database;

import de.dasjeff.warpMaster.model.PlayerData;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Repository for managing player data in the database.
 */
public class PlayerRepository {
    private final DatabaseManager databaseManager;
    private final Logger logger;
    private final int defaultWarpLimit;
    private final ExecutorService executor;

    /**
     * Creates a new PlayerRepository instance.
     *
     * @param databaseManager The database manager
     * @param logger The logger
     * @param defaultWarpLimit The default warp limit for players
     * @param executor The executor service for database operations
     */
    public PlayerRepository(DatabaseManager databaseManager, Logger logger, int defaultWarpLimit, ExecutorService executor) {
        this.databaseManager = databaseManager;
        this.logger = logger;
        this.defaultWarpLimit = defaultWarpLimit;
        this.executor = executor;
    }

    /**
     * Gets player data by UUID.
     *
     * @param uuid The UUID of the player
     * @return A CompletableFuture that completes with the player data, or an empty Optional if not found
     */
    public CompletableFuture<Optional<PlayerData>> getPlayerData(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection connection = databaseManager.getConnection();
                 PreparedStatement statement = connection.prepareStatement(
                         "SELECT * FROM player_data WHERE uuid = ?")) {
                
                statement.setString(1, uuid.toString());
                
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (resultSet.next()) {
                        return Optional.of(mapResultSetToPlayerData(resultSet));
                    } else {
                        return Optional.empty();
                    }
                }
            } catch (SQLException e) {
                logger.log(Level.SEVERE, "Error getting player data", e);
                throw new RuntimeException("Error getting player data", e);
            }
        }, executor);
    }

    /**
     * Gets or creates player data by UUID.
     *
     * @param uuid The UUID of the player
     * @return A CompletableFuture that completes with the player data
     */
    public CompletableFuture<PlayerData> getOrCreatePlayerData(UUID uuid) {
        return getPlayerData(uuid).thenComposeAsync(optionalPlayerData -> {
            if (optionalPlayerData.isPresent()) {
                return CompletableFuture.completedFuture(optionalPlayerData.get());
            } else {
                PlayerData playerData = new PlayerData(uuid, defaultWarpLimit, 0);
                return createPlayerData(playerData).thenApply(created -> playerData);
            }
        }, executor);
    }

    /**
     * Creates player data in the database.
     *
     * @param playerData The player data to create
     * @return A CompletableFuture that completes with a boolean indicating success
     */
    public CompletableFuture<Boolean> createPlayerData(PlayerData playerData) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection connection = databaseManager.getConnection();
                 PreparedStatement statement = connection.prepareStatement(
                         "INSERT INTO player_data (uuid, warp_limit, last_warp_time) VALUES (?, ?, ?)")) {
                
                statement.setString(1, playerData.getUuid().toString());
                statement.setInt(2, playerData.getWarpLimit());
                statement.setLong(3, playerData.getLastWarpTime());
                
                int affectedRows = statement.executeUpdate();
                return affectedRows > 0;
            } catch (SQLException e) {
                logger.log(Level.SEVERE, "Error creating player data", e);
                throw new RuntimeException("Error creating player data", e);
            }
        }, executor);
    }

    /**
     * Updates player data in the database.
     *
     * @param playerData The player data to update
     * @return A CompletableFuture that completes with a boolean indicating success
     */
    public CompletableFuture<Boolean> updatePlayerData(PlayerData playerData) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection connection = databaseManager.getConnection();
                 PreparedStatement statement = connection.prepareStatement(
                         "UPDATE player_data SET warp_limit = ?, last_warp_time = ? WHERE uuid = ?")) {
                
                statement.setInt(1, playerData.getWarpLimit());
                statement.setLong(2, playerData.getLastWarpTime());
                statement.setString(3, playerData.getUuid().toString());
                
                int affectedRows = statement.executeUpdate();
                return affectedRows > 0;
            } catch (SQLException e) {
                logger.log(Level.SEVERE, "Error updating player data", e);
                throw new RuntimeException("Error updating player data", e);
            }
        }, executor);
    }

    /**
     * Updates the warp limit for a player.
     *
     * @param uuid The UUID of the player
     * @param warpLimit The new warp limit
     * @return A CompletableFuture that completes with a boolean indicating success
     */
    public CompletableFuture<Boolean> updateWarpLimit(UUID uuid, int warpLimit) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection connection = databaseManager.getConnection();
                 PreparedStatement statement = connection.prepareStatement(
                         "UPDATE player_data SET warp_limit = ? WHERE uuid = ?")) {
                
                statement.setInt(1, warpLimit);
                statement.setString(2, uuid.toString());
                
                int affectedRows = statement.executeUpdate();
                return affectedRows > 0;
            } catch (SQLException e) {
                logger.log(Level.SEVERE, "Error updating warp limit", e);
                throw new RuntimeException("Error updating warp limit", e);
            }
        }, executor);
    }

    /**
     * Updates the last warp time for a player.
     *
     * @param uuid The UUID of the player
     * @param lastWarpTime The new last warp time
     * @return A CompletableFuture that completes with a boolean indicating success
     */
    public CompletableFuture<Boolean> updateLastWarpTime(UUID uuid, long lastWarpTime) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection connection = databaseManager.getConnection();
                 PreparedStatement statement = connection.prepareStatement(
                         "UPDATE player_data SET last_warp_time = ? WHERE uuid = ?")) {
                
                statement.setLong(1, lastWarpTime);
                statement.setString(2, uuid.toString());
                
                int affectedRows = statement.executeUpdate();
                return affectedRows > 0;
            } catch (SQLException e) {
                logger.log(Level.SEVERE, "Error updating last warp time", e);
                throw new RuntimeException("Error updating last warp time", e);
            }
        }, executor);
    }

    /**
     * Maps a ResultSet to a PlayerData object.
     *
     * @param resultSet The ResultSet to map
     * @return The mapped PlayerData
     * @throws SQLException If an SQL error occurs
     */
    private PlayerData mapResultSetToPlayerData(ResultSet resultSet) throws SQLException {
        return new PlayerData(
                UUID.fromString(resultSet.getString("uuid")),
                resultSet.getInt("warp_limit"),
                resultSet.getLong("last_warp_time")
        );
    }
}
