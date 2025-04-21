package de.dasjeff.warpMaster.service;

import de.dasjeff.warpMaster.database.PlayerRepository;
import de.dasjeff.warpMaster.database.WarpRepository;
import de.dasjeff.warpMaster.model.PlayerData;
import de.dasjeff.warpMaster.model.Warp;
import de.dasjeff.warpMaster.util.ConfigUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import de.dasjeff.warpMaster.database.DatabaseManager;
import java.sql.SQLException;
import java.util.Collections;
import java.util.stream.Collectors;

/**
 * Service for managing warps.
 */
public class WarpService {
    private final WarpRepository warpRepository;
    private final PlayerRepository playerRepository;
    private final ConfigUtil configUtil;
    private final JavaPlugin plugin;
    private final ExecutorService executor;
    private final DatabaseManager databaseManager;

    // Simple Caches
    private final ConcurrentHashMap<UUID, PlayerData> playerDataCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, List<Warp>> playerWarpsCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Integer> warpCountCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, List<String>> warpNameCache = new ConcurrentHashMap<>();

    /**
     * Creates a new WarpService instance.
     *
     * @param warpRepository The warp repository
     * @param playerRepository The player repository
     * @param configUtil The configuration utility
     * @param plugin The plugin instance
     * @param executor The executor service for database operations
     */
    public WarpService(WarpRepository warpRepository, PlayerRepository playerRepository, ConfigUtil configUtil, JavaPlugin plugin, ExecutorService executor, DatabaseManager databaseManager) {
        this.warpRepository = warpRepository;
        this.playerRepository = playerRepository;
        this.configUtil = configUtil;
        this.plugin = plugin;
        this.executor = executor;
        this.databaseManager = databaseManager;
    }

    /**
     * Creates a new warp for a player.
     *
     * @param player The player
     * @param name The name of the warp
     * @return A CompletableFuture that completes with a Result containing the created warp or an error message
     */
    public CompletableFuture<Result<Warp>> createWarp(Player player, String name) {
        UUID playerUuid = player.getUniqueId();
        Location location = player.getLocation();

        return playerRepository.getOrCreatePlayerData(playerUuid)
                .thenComposeAsync(playerData -> warpRepository.getWarpCountByOwner(playerUuid)
                        .thenComposeAsync(count -> {
                            if (count >= playerData.getWarpLimit()) {
                                return CompletableFuture.completedFuture(Result.error("warp-limit-reached", "limit", String.valueOf(playerData.getWarpLimit())));
                            }

                            return warpRepository.getWarpByOwnerAndName(playerUuid, name)
                                    .thenComposeAsync(existingWarp -> {
                                        if (existingWarp.isPresent()) {
                                            return CompletableFuture.completedFuture(Result.error("warp-already-exists", "name", name));
                                        }

                                        Warp warp = new Warp(0, playerUuid, name, location, System.currentTimeMillis());
                                        return warpRepository.createWarp(warp)
                                                .thenApply(createdWarp -> {
                                                    // Invalidate caches on success
                                                    invalidatePlayerCaches(playerUuid);
                                                    return Result.success(createdWarp);
                                                });
                                    }, executor);
                        }, executor), executor);
    }

    /**
     * Gets a warp by its owner and name.
     *
     * @param ownerUuid The UUID of the owner
     * @param name The name of the warp
     * @return A CompletableFuture that completes with an Optional containing the warp, or empty if not found
     */
    public CompletableFuture<Optional<Warp>> getWarp(UUID ownerUuid, String name) {
        // Check cache first for the list
        List<Warp> cachedWarps = playerWarpsCache.get(ownerUuid);
        if (cachedWarps != null) {
            return CompletableFuture.completedFuture(
                cachedWarps.stream()
                           .filter(w -> w.getName().equalsIgnoreCase(name))
                           .findFirst()
            );
        }
        // Fallback to repository if not cached
        return warpRepository.getWarpByOwnerAndName(ownerUuid, name);
    }

    /**
     * Gets all warps owned by a player, using cache.
     *
     * @param ownerUuid The UUID of the owner
     * @return A CompletableFuture that completes with a list of warps
     */
    public CompletableFuture<List<Warp>> getWarps(UUID ownerUuid) {
        // Check cache first
        List<Warp> cachedWarps = playerWarpsCache.get(ownerUuid);
        if (cachedWarps != null) {
            return CompletableFuture.completedFuture(cachedWarps);
        }

        // If not in cache, fetch from repository and cache the result
        return warpRepository.getWarpsByOwner(ownerUuid)
               .thenApplyAsync(warps -> {
                   playerWarpsCache.put(ownerUuid, warps);
                   // Update count cache as well
                   warpCountCache.put(ownerUuid, warps.size());
                   // Update name cache
                   List<String> names = warps.stream().map(Warp::getName).collect(Collectors.toList());
                   warpNameCache.put(ownerUuid, names);
                   return warps;
               }, executor);
    }

    /**
     * Deletes a warp by its owner and name.
     *
     * @param ownerUuid The UUID of the owner
     * @param name The name of the warp
     * @return A CompletableFuture that completes with a boolean indicating success
     */
    public CompletableFuture<Boolean> deleteWarp(UUID ownerUuid, String name) {
        return warpRepository.deleteWarpByOwnerAndName(ownerUuid, name)
                .thenApplyAsync(deleted -> {
                    if (deleted) {
                        // Invalidate caches on success
                        invalidatePlayerCaches(ownerUuid);
                    }
                    return deleted;
                }, executor);
    }

    /**
     * Teleports a player to a warp.
     *
     * @param player The player to teleport
     * @param ownerUuid The UUID of the warp owner
     * @param name The name of the warp
     * @return A CompletableFuture that completes with a Result indicating success or an error message
     */
    public CompletableFuture<Result<Void>> teleportToWarp(Player player, UUID ownerUuid, String name) {
        UUID playerUuid = player.getUniqueId();
        int cooldown = configUtil.getWarpCooldown();
        if (plugin.getLogger().isLoggable(Level.FINE)) {
             plugin.getLogger().log(Level.FINE, "[DEBUG] teleportToWarp called for player {0} to warp ''{1}'' owned by {2}", new Object[]{player.getName(), name, ownerUuid});
        }

        return playerRepository.getOrCreatePlayerData(playerUuid)
                .thenComposeAsync(playerData -> {
                    if (plugin.getLogger().isLoggable(Level.FINE)) {
                        plugin.getLogger().log(Level.FINE, "[DEBUG] Checking cooldown for {0}", player.getName());
                    }
                    if (playerData.isOnCooldown(cooldown)) {
                        int remainingCooldown = playerData.getRemainingCooldown(cooldown);
                         if (plugin.getLogger().isLoggable(Level.FINE)) {
                            plugin.getLogger().log(Level.FINE, "[DEBUG] Player {0} is on cooldown ({1}s remaining)", new Object[]{player.getName(), remainingCooldown});
                         }
                        return CompletableFuture.completedFuture(Result.<Void>error("cooldown-active", "time", String.valueOf(remainingCooldown)));
                    }

                    if (plugin.getLogger().isLoggable(Level.FINE)) {
                        plugin.getLogger().log(Level.FINE, "[DEBUG] Fetching warp ''{0}'' for owner {1}", new Object[]{name, ownerUuid});
                    }
                    return warpRepository.getWarpByOwnerAndName(ownerUuid, name)
                            .thenComposeAsync(optionalWarp -> {
                                if (optionalWarp.isEmpty()) {
                                     if (plugin.getLogger().isLoggable(Level.FINE)) {
                                        plugin.getLogger().log(Level.FINE, "[DEBUG] Warp ''{0}'' not found for owner {1}", new Object[]{name, ownerUuid});
                                     }
                                    return CompletableFuture.completedFuture(Result.<Void>error("warp-not-found", "name", name));
                                }
                                if (plugin.getLogger().isLoggable(Level.FINE)) {
                                    plugin.getLogger().log(Level.FINE, "[DEBUG] Warp ''{0}'' found.", name);
                                }

                                Warp warp = optionalWarp.get();
                                Location location = warp.toLocation();

                                if (location == null) {
                                     if (plugin.getLogger().isLoggable(Level.WARNING)) {
                                        plugin.getLogger().log(Level.WARNING, "[DEBUG] World ''{0}'' for warp ''{1}'' not found or loaded!", new Object[]{warp.getWorldName(), name});
                                     }
                                    return CompletableFuture.completedFuture(Result.<Void>error("world-not-found", "world", warp.getWorldName()));
                                }
                                if (plugin.getLogger().isLoggable(Level.FINE)) {
                                    plugin.getLogger().log(Level.FINE, "[DEBUG] Location for warp ''{0}'' resolved: {1}", new Object[]{name, location});
                                }

                                playerData.setLastWarpTime(System.currentTimeMillis());
                                return playerRepository.updateLastWarpTime(playerUuid, playerData.getLastWarpTime())
                                        .thenComposeAsync(updateSuccess -> {
                                            if (!updateSuccess) {
                                                 if (plugin.getLogger().isLoggable(Level.WARNING)) {
                                                    plugin.getLogger().log(Level.WARNING, "[DEBUG] Failed to update last warp time for {0}", player.getName());
                                                 }
                                            }
                                            if (plugin.getLogger().isLoggable(Level.FINE)) {
                                                plugin.getLogger().log(Level.FINE, "[DEBUG] Attempting teleport for {0} to {1}", new Object[]{player.getName(), location});
                                            }
                                            return CompletableFuture.supplyAsync(() -> player.teleport(location), Bukkit.getScheduler().getMainThreadExecutor(plugin))
                                                    .thenApplyAsync(teleportSuccess -> {
                                                        if (plugin.getLogger().isLoggable(Level.FINE)) {
                                                            plugin.getLogger().log(Level.FINE, "[DEBUG] Teleport call for {0} returned: {1}", new Object[]{player.getName(), teleportSuccess});
                                                        }
                                                        if (teleportSuccess) {
                                                            return Result.<Void>success();
                                                        } else {
                                                             if (plugin.getLogger().isLoggable(Level.WARNING)) {
                                                                plugin.getLogger().log(Level.WARNING, "[DEBUG] Teleport for {0} to warp ''{1}'' failed (returned false).", new Object[]{player.getName(), name});
                                                             }
                                                            return Result.<Void>error("teleport-failed");
                                                        }
                                                    }, executor);
                                        }, executor);
                            });
                }, executor)
                .exceptionally(ex -> {
                    plugin.getLogger().log(Level.SEVERE, "[DEBUG] Exception during teleportToWarp for player " + player.getName() + " to warp '" + name + "'", ex);
                    return Result.<Void>error("internal-error");
                });
    }

    /**
     * Transfers a warp from one player to another.
     *
     * @param sourceUuid The UUID of the source player
     * @param targetUuid The UUID of the target player
     * @param name The name of the warp
     * @return A CompletableFuture that completes with a Result indicating success or an error message
     */
    public CompletableFuture<Result<Void>> transferWarp(UUID sourceUuid, UUID targetUuid, String name) {
         // --- Pre-checks ---
         // Check if target player can receive the warp (limit)
         CompletableFuture<Result<Void>> preCheckFuture = getOrFetchPlayerData(targetUuid)
             .thenComposeAsync(targetPlayerData -> getWarpCount(targetUuid)
                 .thenComposeAsync(count -> {
                     if (count >= targetPlayerData.getWarpLimit()) {
                         return CompletableFuture.completedFuture(Result.<Void>error("target-warp-limit-reached", "limit", String.valueOf(targetPlayerData.getWarpLimit())));
                     }
                     // Check if target already has a warp with the same name
                     return getWarp(targetUuid, name).thenComposeAsync(optionalTargetWarp -> {
                         if (optionalTargetWarp.isPresent()) {
                             return CompletableFuture.completedFuture(Result.<Void>error("target-warp-already-exists", "name", name));
                         }
                         // Pre-checks passed
                         return CompletableFuture.completedFuture(Result.<Void>success());
                     }, executor);
                 }, executor), executor);

         // --- Transactional Part ---
         return preCheckFuture.thenComposeAsync(preCheckResult -> {
             if (!preCheckResult.isSuccess()) {
                 // If pre-checks failed, return the error result immediately
                 return CompletableFuture.completedFuture(preCheckResult);
             }

             // Fetch source warp details needed for creation
             return warpRepository.getWarpByOwnerAndName(sourceUuid, name)
                 .thenComposeAsync(optionalSourceWarp -> {
                     if (optionalSourceWarp.isEmpty()) {
                         return CompletableFuture.completedFuture(Result.<Void>error("warp-not-found", "name", name));
                     }
                     Warp sourceWarp = optionalSourceWarp.get();

                     // Execute the core logic within a transaction
                     return databaseManager.executeInTransaction(connection -> {
                         // Create the new warp using the transaction connection
                         Warp targetWarp = new Warp(
                                 0,
                                 targetUuid,
                                 name,
                                 sourceWarp.getWorldName(),
                                 sourceWarp.getX(),
                                 sourceWarp.getY(),
                                 sourceWarp.getZ(),
                                 sourceWarp.getYaw(),
                                 sourceWarp.getPitch(),
                                 System.currentTimeMillis()
                         );
                         warpRepository.createWarpTransactional(connection, targetWarp);

                         // Delete the old warp using the transaction connection
                         boolean deleted = warpRepository.deleteWarpTransactional(connection, sourceWarp.getId());

                         if (!deleted) {
                              throw new SQLException("Failed to delete source warp (ID: " + sourceWarp.getId() + ") during transfer, rolling back.");
                         }

                         return true;
                     })
                     .thenApplyAsync(transactionSuccess -> {
                           if (transactionSuccess) {
                               // Invalidate caches after successful commit
                               invalidatePlayerCaches(sourceUuid);
                               invalidatePlayerCaches(targetUuid);
                               return Result.<Void>success();
                           } else {
                               plugin.getLogger().warning("Warp transfer transaction reported failure unexpectedly.");
                               return Result.<Void>error("internal-error");
                           }
                     }, executor);
                 }, executor)
                 .exceptionally(ex -> {
                      // Handle exceptions from fetching source warp or the transaction itself
                      plugin.getLogger().log(Level.SEVERE, "Error during warp transfer for warp '" + name + "' from " + sourceUuid + " to " + targetUuid, ex);
                      return Result.<Void>error("internal-error");
                 });
         }, executor);
    }

    /**
     * Sets the warp limit for a player.
     *
     * @param playerUuid The UUID of the player
     * @param limit The new warp limit
     * @return A CompletableFuture that completes with a boolean indicating success
     */
    public CompletableFuture<Boolean> setWarpLimit(UUID playerUuid, int limit) {
        // Use cached player
        return getOrFetchPlayerData(playerUuid)
                .thenComposeAsync(playerData -> {
                    playerData.setWarpLimit(limit);
                    // Update DB and invalidate cache on success
                    return playerRepository.updateWarpLimit(playerUuid, limit)
                            .thenApplyAsync(success -> {
                                if (success) {
                                    // Update cached data directly or
                                    playerDataCache.put(playerUuid, playerData);
                                } else {
                                    // Invalidate if update failed
                                    playerDataCache.remove(playerUuid);
                                }
                                return success;
                            }, executor);
                }, executor);
    }

    /**
     * Gets the warp limit for a player, using cache.
     *
     * @param playerUuid The UUID of the player
     * @return A CompletableFuture that completes with the warp limit
     */
    public CompletableFuture<Integer> getWarpLimit(UUID playerUuid) {
        // Use cached player
        return getOrFetchPlayerData(playerUuid)
                .thenApplyAsync(PlayerData::getWarpLimit, executor);
    }

    /**
     * Gets the number of warps owned by a player, using cache.
     *
     * @param playerUuid The UUID of the player
     * @return A CompletableFuture that completes with the number of warps
     */
    public CompletableFuture<Integer> getWarpCount(UUID playerUuid) {
        // Check cache
        Integer cachedCount = warpCountCache.get(playerUuid);
        if (cachedCount != null) {
            return CompletableFuture.completedFuture(cachedCount);
        }

        // Try fetching from warp list cache
        List<Warp> cachedWarps = playerWarpsCache.get(playerUuid);
         if (cachedWarps != null) {
             int count = cachedWarps.size();
             warpCountCache.put(playerUuid, count);
             return CompletableFuture.completedFuture(count);
         }

        // If not in cache, fetch from repository and cache the result
        return warpRepository.getWarpCountByOwner(playerUuid)
               .thenApplyAsync(count -> {
                   warpCountCache.put(playerUuid, count);
                   return count;
               }, executor);
    }

    /**
     * Gets player data, using cache first.
     *
     * @param playerUuid The UUID of the player
     * @return A CompletableFuture that completes with the player data
     */
    private CompletableFuture<PlayerData> getOrFetchPlayerData(UUID playerUuid) {
        PlayerData cachedData = playerDataCache.get(playerUuid);
        if (cachedData != null) {
            return CompletableFuture.completedFuture(cachedData);
        }

        return playerRepository.getOrCreatePlayerData(playerUuid)
                .thenApplyAsync(playerData -> {
                    playerDataCache.put(playerUuid, playerData);
                    return playerData;
                }, executor);
    }

    /**
     * Invalidates all caches associated with a specific player.
     *
     * @param playerUuid The UUID of the player whose caches should be invalidated.
     */
    public void invalidatePlayerCaches(UUID playerUuid) {
        playerDataCache.remove(playerUuid);
        playerWarpsCache.remove(playerUuid);
        warpCountCache.remove(playerUuid);
        warpNameCache.remove(playerUuid);
        if (plugin.getLogger().isLoggable(Level.FINE)) {
             plugin.getLogger().log(Level.FINE, "Invalidated caches for player {0}", playerUuid);
        }
    }

    /**
     * Clears all internal caches.
     */
    public void clearAllCaches() {
        playerDataCache.clear();
        playerWarpsCache.clear();
        warpCountCache.clear();
        warpNameCache.clear();
        plugin.getLogger().info("Cleared all WarpService caches.");
    }

    /**
     * Gets a cached list of warp names for a player.
     * Returns an empty list if not cached, but triggers an asynchronous load if needed.
     *
     * @param playerUuid The UUID of the player.
     * @return A list of warp names, potentially empty.
     */
    public List<String> getCachedWarpNames(UUID playerUuid) {
        List<String> cachedNames = warpNameCache.get(playerUuid);

        // If cache is null, trigger background load
        if (cachedNames == null) {
            // Check if the full warp list is also not cached
            if (!playerWarpsCache.containsKey(playerUuid)) {
                 if (plugin.getLogger().isLoggable(Level.FINE)) {
                     plugin.getLogger().log(Level.FINE, "Warp name cache miss for {0}, triggering background load.", playerUuid);
                 }
                 getWarps(playerUuid).exceptionally(ex -> {
                     plugin.getLogger().log(Level.WARNING, "Background warp load failed for " + playerUuid, ex);
                     return null;
                 });
            }
            // Return empty list for this synchronous request
            return Collections.emptyList();
        }

        // Return the cached names
        return cachedNames;
    }

    /**
     * Creates a warp directly from a Warp object.
     * This is used by the API.
     *
     * @param warp The warp to create
     * @return A CompletableFuture that completes with the created warp
     */
    public CompletableFuture<Warp> createWarpDirect(Warp warp) {
        return warpRepository.createWarp(warp);
    }

    /**
     * Gets a list of all players who have warps.
     *
     * @return A CompletableFuture that completes with a list of player UUIDs
     */
    public CompletableFuture<List<UUID>> getPlayersWithWarps() {
        return warpRepository.getPlayersWithWarps();
    }

    /**
     * Represents the result of an operation.
     *
     * @param <T> The type of the result
     */
    public static class Result<T> {
        private final T value;
        private final String errorKey;
        private final String[] placeholders;

        private Result(T value, String errorKey, String... placeholders) {
            this.value = value;
            this.errorKey = errorKey;
            this.placeholders = placeholders;
        }

        /**
         * Creates a successful result.
         *
         * @param value The value
         * @param <T> The type of the value
         * @return The result
         */
        public static <T> Result<T> success(T value) {
            return new Result<>(value, null);
        }

        /**
         * Creates a successful result with no value.
         *
         * @param <T> The type of the value
         * @return The result
         */
        public static <T> Result<T> success() {
            return new Result<>(null, null);
        }

        /**
         * Creates an error result.
         *
         * @param errorKey The error key
         * @param placeholders The placeholders for the error message
         * @param <T> The type of the value
         * @return The result
         */
        public static <T> Result<T> error(String errorKey, String... placeholders) {
            return new Result<>(null, errorKey, placeholders);
        }

        /**
         * Checks if the result is successful.
         *
         * @return True if the result is successful, false otherwise
         */
        public boolean isSuccess() {
            return errorKey == null;
        }

        /**
         * Gets the value of the result.
         *
         * @return The value
         */
        public T getValue() {
            return value;
        }

        /**
         * Gets the error key of the result.
         *
         * @return The error key
         */
        public String getErrorKey() {
            return errorKey;
        }

        /**
         * Gets the placeholders for the error message.
         *
         * @return The placeholders
         */
        public String[] getPlaceholders() {
            return placeholders;
        }
    }
}
