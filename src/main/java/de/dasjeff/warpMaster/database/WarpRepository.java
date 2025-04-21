package de.dasjeff.warpMaster.database;

import de.dasjeff.warpMaster.model.Warp;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Repository for managing warp data in the database.
 */
public class WarpRepository {
    private final DatabaseManager databaseManager;
    private final Logger logger;
    private final ExecutorService executor;

    /**
     * Creates a new WarpRepository instance.
     *
     * @param databaseManager The database manager
     * @param logger The logger
     * @param executor The executor service for database operations
     */
    public WarpRepository(DatabaseManager databaseManager, Logger logger, ExecutorService executor) {
        this.databaseManager = databaseManager;
        this.logger = logger;
        this.executor = executor;
    }

    /**
     * Creates a new warp in the database using its own connection.
     * Runs asynchronously on the configured executor.
     *
     * @param warp The warp to create
     * @return A CompletableFuture that completes with the created warp (with ID)
     */
    public CompletableFuture<Warp> createWarp(Warp warp) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection connection = databaseManager.getConnection()) {
                // Delegate to the transactional version
                return createWarpTransactional(connection, warp);
            } catch (SQLException e) {
                logger.log(Level.SEVERE, "Error creating warp", e);
                throw new RuntimeException("Error creating warp", e);
            }
        }, executor);
    }

    /**
     * Creates a new warp using a provided database connection (for transactions).
     * This method runs synchronously within the calling thread.
     *
     * @param connection The existing database connection.
     * @param warp The warp to create.
     * @return The created warp (with ID).
     * @throws SQLException If a database error occurs.
     */
    public Warp createWarpTransactional(Connection connection, Warp warp) throws SQLException {
        // Use try-with-resources for the PreparedStatement, but NOT the connection
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO warps (owner_uuid, name, world_name, x, y, z, yaw, pitch, created_at) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS)) {

            statement.setString(1, warp.getOwnerUuid().toString());
            statement.setString(2, warp.getName());
            statement.setString(3, warp.getWorldName());
            statement.setDouble(4, warp.getX());
            statement.setDouble(5, warp.getY());
            statement.setDouble(6, warp.getZ());
            statement.setFloat(7, warp.getYaw());
            statement.setFloat(8, warp.getPitch());
            statement.setLong(9, warp.getCreatedAt());

            int affectedRows = statement.executeUpdate();
            if (affectedRows == 0) {
                throw new SQLException("Creating warp failed, no rows affected.");
            }

            try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    int id = generatedKeys.getInt(1);
                    // Return a new Warp object with the generated ID
                    return new Warp(
                            id,
                            warp.getOwnerUuid(),
                            warp.getName(),
                            warp.getWorldName(),
                            warp.getX(),
                            warp.getY(),
                            warp.getZ(),
                            warp.getYaw(),
                            warp.getPitch(),
                            warp.getCreatedAt()
                    );
                } else {
                    throw new SQLException("Creating warp failed, no ID obtained.");
                }
            }
        }
        // Connection close is managed by the caller (transaction)
    }

    /**
     * Gets a warp by its ID.
     *
     * @param id The ID of the warp
     * @return A CompletableFuture that completes with the warp, or an empty Optional if not found
     */
    public CompletableFuture<Optional<Warp>> getWarpById(int id) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection connection = databaseManager.getConnection();
                 PreparedStatement statement = connection.prepareStatement(
                         "SELECT * FROM warps WHERE id = ?")) {

                statement.setInt(1, id);

                try (ResultSet resultSet = statement.executeQuery()) {
                    if (resultSet.next()) {
                        return Optional.of(mapResultSetToWarp(resultSet));
                    } else {
                        return Optional.empty();
                    }
                }
            } catch (SQLException e) {
                logger.log(Level.SEVERE, "Error getting warp by ID", e);
                throw new RuntimeException("Error getting warp by ID", e);
            }
        }, executor);
    }

    /**
     * Gets a warp by its owner and name.
     *
     * @param ownerUuid The UUID of the owner
     * @param name The name of the warp
     * @return A CompletableFuture that completes with the warp, or an empty Optional if not found
     */
    public CompletableFuture<Optional<Warp>> getWarpByOwnerAndName(UUID ownerUuid, String name) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection connection = databaseManager.getConnection();
                 PreparedStatement statement = connection.prepareStatement(
                         "SELECT * FROM warps WHERE owner_uuid = ? AND name = ?")) {

                statement.setString(1, ownerUuid.toString());
                statement.setString(2, name);

                try (ResultSet resultSet = statement.executeQuery()) {
                    if (resultSet.next()) {
                        return Optional.of(mapResultSetToWarp(resultSet));
                    } else {
                        return Optional.empty();
                    }
                }
            } catch (SQLException e) {
                logger.log(Level.SEVERE, "Error getting warp by owner and name", e);
                throw new RuntimeException("Error getting warp by owner and name", e);
            }
        }, executor);
    }

    /**
     * Gets all warps owned by a player.
     *
     * @param ownerUuid The UUID of the owner
     * @return A CompletableFuture that completes with a list of warps
     */
    public CompletableFuture<List<Warp>> getWarpsByOwner(UUID ownerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection connection = databaseManager.getConnection();
                 PreparedStatement statement = connection.prepareStatement(
                         "SELECT * FROM warps WHERE owner_uuid = ?")) {

                statement.setString(1, ownerUuid.toString());

                try (ResultSet resultSet = statement.executeQuery()) {
                    List<Warp> warps = new ArrayList<>();
                    while (resultSet.next()) {
                        warps.add(mapResultSetToWarp(resultSet));
                    }
                    return warps;
                }
            } catch (SQLException e) {
                logger.log(Level.SEVERE, "Error getting warps by owner", e);
                throw new RuntimeException("Error getting warps by owner", e);
            }
        }, executor);
    }

    /**
     * Gets the number of warps owned by a player.
     *
     * @param ownerUuid The UUID of the owner
     * @return A CompletableFuture that completes with the number of warps
     */
    public CompletableFuture<Integer> getWarpCountByOwner(UUID ownerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection connection = databaseManager.getConnection();
                 PreparedStatement statement = connection.prepareStatement(
                         "SELECT COUNT(*) FROM warps WHERE owner_uuid = ?")) {

                statement.setString(1, ownerUuid.toString());

                try (ResultSet resultSet = statement.executeQuery()) {
                    if (resultSet.next()) {
                        return resultSet.getInt(1);
                    } else {
                        return 0;
                    }
                }
            } catch (SQLException e) {
                logger.log(Level.SEVERE, "Error getting warp count by owner", e);
                throw new RuntimeException("Error getting warp count by owner", e);
            }
        }, executor);
    }

    /**
     * Updates a warp in the database.
     *
     * @param warp The warp to update
     * @return A CompletableFuture that completes with a boolean indicating success
     */
    public CompletableFuture<Boolean> updateWarp(Warp warp) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection connection = databaseManager.getConnection();
                 PreparedStatement statement = connection.prepareStatement(
                         "UPDATE warps SET owner_uuid = ?, name = ?, world_name = ?, " +
                                 "x = ?, y = ?, z = ?, yaw = ?, pitch = ? " +
                                 "WHERE id = ?")) {

                statement.setString(1, warp.getOwnerUuid().toString());
                statement.setString(2, warp.getName());
                statement.setString(3, warp.getWorldName());
                statement.setDouble(4, warp.getX());
                statement.setDouble(5, warp.getY());
                statement.setDouble(6, warp.getZ());
                statement.setFloat(7, warp.getYaw());
                statement.setFloat(8, warp.getPitch());
                statement.setInt(9, warp.getId());

                int affectedRows = statement.executeUpdate();
                return affectedRows > 0;
            } catch (SQLException e) {
                logger.log(Level.SEVERE, "Error updating warp", e);
                throw new RuntimeException("Error updating warp", e);
            }
        }, executor);
    }

    /**
     * Deletes a warp from the database using a provided connection (for transactions).
     * This method runs synchronously within the calling thread.
     *
     * @param connection The existing database connection.
     * @param id The ID of the warp to delete.
     * @return True if a row was deleted, false otherwise.
     * @throws SQLException If a database error occurs.
     */
    public boolean deleteWarpTransactional(Connection connection, int id) throws SQLException {
        // Use try-with-resources for the PreparedStatement, but NOT the connection
        try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM warps WHERE id = ?")) {

            statement.setInt(1, id);
            int affectedRows = statement.executeUpdate();
            return affectedRows > 0;
        }
        // Connection close is managed by the caller (transaction)
    }

    /**
     * Deletes a warp from the database.
     *
     * @param id The ID of the warp to delete
     * @return A CompletableFuture that completes with a boolean indicating success
     */
    public CompletableFuture<Boolean> deleteWarp(int id) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection connection = databaseManager.getConnection()) {
                 // Delegate to the transactional version
                 return deleteWarpTransactional(connection, id);
            } catch (SQLException e) {
                logger.log(Level.SEVERE, "Error deleting warp by ID", e);
                throw new RuntimeException("Error deleting warp by ID", e);
            }
        }, executor);
    }

    /**
     * Deletes a warp by its owner and name.
     *
     * @param ownerUuid The UUID of the owner
     * @param name The name of the warp
     * @return A CompletableFuture that completes with a boolean indicating success
     */
    public CompletableFuture<Boolean> deleteWarpByOwnerAndName(UUID ownerUuid, String name) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection connection = databaseManager.getConnection();
                 PreparedStatement statement = connection.prepareStatement(
                         "DELETE FROM warps WHERE owner_uuid = ? AND name = ?")) {

                statement.setString(1, ownerUuid.toString());
                statement.setString(2, name);

                int affectedRows = statement.executeUpdate();
                return affectedRows > 0;
            } catch (SQLException e) {
                logger.log(Level.SEVERE, "Error deleting warp by owner and name", e);
                throw new RuntimeException("Error deleting warp by owner and name", e);
            }
        }, executor);
    }

    /**
     * Gets a list of all players who have warps.
     *
     * @return A CompletableFuture that completes with a list of player UUIDs
     */
    public CompletableFuture<List<UUID>> getPlayersWithWarps() {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection connection = databaseManager.getConnection();
                 PreparedStatement statement = connection.prepareStatement(
                         "SELECT DISTINCT owner_uuid FROM warps")) {

                try (ResultSet resultSet = statement.executeQuery()) {
                    List<UUID> players = new ArrayList<>();
                    while (resultSet.next()) {
                        players.add(UUID.fromString(resultSet.getString("owner_uuid")));
                    }
                    return players;
                }
            } catch (SQLException e) {
                logger.log(Level.SEVERE, "Error getting players with warps", e);
                throw new RuntimeException("Error getting players with warps", e);
            }
        }, executor);
    }

    /**
     * Maps a ResultSet to a Warp object.
     *
     * @param resultSet The ResultSet to map
     * @return The mapped Warp
     * @throws SQLException If an SQL error occurs
     */
    private Warp mapResultSetToWarp(ResultSet resultSet) throws SQLException {
        return new Warp(
                resultSet.getInt("id"),
                UUID.fromString(resultSet.getString("owner_uuid")),
                resultSet.getString("name"),
                resultSet.getString("world_name"),
                resultSet.getDouble("x"),
                resultSet.getDouble("y"),
                resultSet.getDouble("z"),
                resultSet.getFloat("yaw"),
                resultSet.getFloat("pitch"),
                resultSet.getLong("created_at")
        );
    }
}
