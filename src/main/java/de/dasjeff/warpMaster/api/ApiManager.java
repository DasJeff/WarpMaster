package de.dasjeff.warpMaster.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.dasjeff.warpMaster.WarpMaster;
import de.dasjeff.warpMaster.model.Warp;
import de.dasjeff.warpMaster.service.WarpService;
import de.dasjeff.warpMaster.util.ConfigUtil;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.json.JavalinJackson;
import io.javalin.http.staticfiles.Location;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;

/**
 * Manages the REST API.
 */
public class ApiManager {
    private final WarpMaster plugin;
    private final ConfigUtil configUtil;
    private final WarpService warpService;
    private final ObjectMapper objectMapper;
    private final ExecutorService executor;
    private Javalin app;

    // Rate limiting
    private final ConcurrentHashMap<String, RateLimitInfo> rateLimitMap = new ConcurrentHashMap<>();
    private static final long RATE_LIMIT_CLEANUP_INTERVAL_MS = TimeUnit.MINUTES.toMillis(5);
    private long lastRateLimitCleanup = System.currentTimeMillis();

    /**
     * Creates a new ApiManager instance.
     *
     * @param plugin The plugin instance
     * @param configUtil The configuration utility
     * @param warpService The warp service
     * @param executor The executor service (can be used for async tasks off Netty threads)
     */
    public ApiManager(WarpMaster plugin, ConfigUtil configUtil, WarpService warpService, ExecutorService executor) {
        this.plugin = plugin;
        this.configUtil = configUtil;
        this.warpService = warpService;
        this.objectMapper = new ObjectMapper();
        this.executor = executor;
    }

    /**
     * Starts the API server.
     */
    public void start() {
        if (!configUtil.isApiEnabled()) {
            plugin.getLogger().info("API is disabled in the configuration.");
            return;
        }

        try {
            app = Javalin.create(config -> {
                config.jsonMapper(new JavalinJackson(objectMapper, false));
                config.showJavalinBanner = false;

                // Serve static files from the web directory
                config.staticFiles.add(staticFiles -> {
                    staticFiles.directory = "/web";
                    staticFiles.location = Location.CLASSPATH;
                    staticFiles.hostedPath = "/";
                });
            });

            // Configure security for API endpoints only
            app.before("/api/*", this::securityCheck);

            // Configure endpoints
            configureEndpoints();

            // Start the server
            app.start(configUtil.getApiHost(), configUtil.getApiPort());

            plugin.getLogger().info("API server started on " + configUtil.getApiHost() + ":" + configUtil.getApiPort());
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to start API server", e);
        }
    }

    /**
     * Stops the API server.
     */
    public void stop() {
        if (app != null) {
            app.stop();
            plugin.getLogger().info("API server stopped.");
        }
    }

    /**
     * Configures the API endpoints.
     */
    private void configureEndpoints() {
        // Web Dashboard Routes
        app.get("/", ctx -> ctx.redirect("/index.html"));

        // API Endpoints
        // Get all players with warps
        app.get("/api/players", this::getPlayers);

        // Get player data by UUID
        app.get("/api/player/{uuid}", this::getPlayerData);

        // Get all warps for a player
        app.get("/api/warps/{uuid}", this::getWarps);

        // Create a new warp
        app.post("/api/warp", this::createWarp);

        // Delete a warp
        app.delete("/api/warp/{uuid}/{name}", this::deleteWarp);

        // Get warp limit for a player
        app.get("/api/player/{uuid}/limit", this::getWarpLimit);

        // Set warp limit for a player
        app.put("/api/player/{uuid}/limit", this::setWarpLimit);
    }

    /**
     * Checks security for API requests.
     *
     * @param ctx The context
     */
    private void securityCheck(Context ctx) {
        // Check API key
        String apiKey = ctx.header("X-API-Key");
        if (apiKey == null || !apiKey.equals(configUtil.getApiKey())) {
            ctx.status(HttpStatus.UNAUTHORIZED).json(error("Invalid API key"));
            ctx.skipRemainingHandlers();
            return;
        }

        // Check IP whitelist
        String ip = ctx.ip();
        List<String> whitelist = configUtil.getApiIpWhitelist();
        if (!whitelist.isEmpty() && !whitelist.contains(ip) && !whitelist.contains("*")) {
            ctx.status(HttpStatus.FORBIDDEN).json(error("IP not whitelisted"));
            ctx.skipRemainingHandlers();
            return;
        }

        // Check rate limit
        if (configUtil.isApiRateLimitEnabled() && !checkAndIncrementRateLimit(ip)) {
            ctx.status(HttpStatus.TOO_MANY_REQUESTS).json(error("Rate limit exceeded"));
            ctx.skipRemainingHandlers();
        }
    }

    /**
     * Checks authentication for dashboard access.
     * Redirects to login page if not authenticated.
     *
     * @param ctx The context
     */
    private void dashboardAuthCheck(Context ctx) {
        String apiKey = ctx.header("X-API-Key");

        // If API key is valid, allow access to the dashboard
        if (apiKey != null && apiKey.equals(configUtil.getApiKey())) {
            return;
        }

        // No valid API key, redirect to login page
        ctx.redirect("/login.html");
    }

    /**
     * Checks and increments the rate limit for a given IP.
     * Also performs periodic cleanup of old entries.
     *
     * @param ip The IP address
     * @return True if the request is within the rate limit, false otherwise
     */
    private boolean checkAndIncrementRateLimit(String ip) {
        int requestsPerMinute = configUtil.getApiRequestsPerMinute();
        long now = System.currentTimeMillis();

        cleanupRateLimitMap(now);

        RateLimitInfo info = rateLimitMap.computeIfAbsent(ip, k -> new RateLimitInfo(now));

        long currentWindowStart = info.getWindowStart();
        // Check if the window needs to be reset
        if (now - currentWindowStart > TimeUnit.MINUTES.toMillis(1)) {
            if (info.tryResetWindow(currentWindowStart, now)) {
            } else {
                // Another thread already reset, re-fetch the updated info
                info = rateLimitMap.get(ip);
                // If somehow removed between get and check, create new
                if (info == null) {
                     info = rateLimitMap.computeIfAbsent(ip, k -> new RateLimitInfo(now));
                }
            }
        }

        // Increment and check if the limit has been reached
        return info.incrementRequestCount() < requestsPerMinute;
    }

    /**
     * Periodically removes old entries from the rate limit map.
     *
     * @param now Current time in milliseconds
     */
    private void cleanupRateLimitMap(long now) {
        if (now - lastRateLimitCleanup > RATE_LIMIT_CLEANUP_INTERVAL_MS) {
            // Perform cleanup asynchronously
            executor.submit(() -> {
                 plugin.getLogger().fine("Performing rate limit map cleanup...");
                 long cleanupThreshold = now - TimeUnit.MINUTES.toMillis(5);
                 rateLimitMap.entrySet().removeIf(entry -> entry.getValue().getWindowStart() < cleanupThreshold);
                 lastRateLimitCleanup = now;
                 plugin.getLogger().fine("Rate limit map cleanup finished.");
             });
        }
    }

    /**
     * Handles the GET /api/players endpoint.
     * Returns a list of all players who have warps.
     *
     * @param ctx The context
     */
    private void getPlayers(Context ctx) {
        ctx.future(() -> warpService.getPlayersWithWarps()
            .thenApply(players -> {
                List<Map<String, Object>> result = new ArrayList<>();
                for (UUID playerUuid : players) {
                    Map<String, Object> playerData = new HashMap<>();
                    playerData.put("uuid", playerUuid.toString());

                    // Get player name if online
                    Player player = Bukkit.getPlayer(playerUuid);
                    String playerName = player != null ? player.getName() : Bukkit.getOfflinePlayer(playerUuid).getName();
                    playerData.put("name", playerName != null ? playerName : "Unbekannt");

                    result.add(playerData);
                }
                return result;
            })
            .thenAccept(result -> ctx.json(result))
            .exceptionally(ex -> {
                plugin.getLogger().log(Level.SEVERE, "Error getting players with warps", ex);
                ctx.status(HttpStatus.INTERNAL_SERVER_ERROR).json(error("Internal server error"));
                return null;
            }));
    }

    /**
     * Handles the GET /api/player/{uuid} endpoint.
     * Returns detailed information about a player.
     *
     * @param ctx The context
     */
    private void getPlayerData(Context ctx) {
        String uuidString = ctx.pathParam("uuid");
        UUID uuid;
        try {
            uuid = UUID.fromString(uuidString);
        } catch (IllegalArgumentException e) {
            ctx.status(HttpStatus.BAD_REQUEST).json(error("Invalid UUID format"));
            return;
        }

        // Get player name
        Player player = Bukkit.getPlayer(uuid);
        String playerName = player != null ? player.getName() : Bukkit.getOfflinePlayer(uuid).getName();

        // Combine player data with warp limit
        ctx.future(() -> warpService.getWarpLimit(uuid)
            .thenCompose(limit -> warpService.getWarpCount(uuid)
                .thenApply(count -> {
                    Map<String, Object> result = new HashMap<>();
                    result.put("uuid", uuidString);
                    result.put("name", playerName != null ? playerName : "Unbekannt");
                    result.put("warpLimit", limit);
                    result.put("warpCount", count);
                    return result;
                }))
            .thenAccept(result -> ctx.json(result))
            .exceptionally(ex -> {
                plugin.getLogger().log(Level.SEVERE, "Error getting player data for UUID: " + uuidString, ex);
                ctx.status(HttpStatus.INTERNAL_SERVER_ERROR).json(error("Internal server error"));
                return null;
            }));
    }

    /**
     * Handles the GET /api/warps/{uuid} endpoint.
     *
     * @param ctx The context
     */
    private void getWarps(Context ctx) {
        String uuidString = ctx.pathParam("uuid");
        UUID uuid;
        try {
            uuid = UUID.fromString(uuidString);
        } catch (IllegalArgumentException e) {
            ctx.status(HttpStatus.BAD_REQUEST).json(error("Invalid UUID format"));
            return;
        }

        // Use ctx.future() to handle the CompletableFuture asynchronously
        ctx.future(() -> warpService.getWarps(uuid)
            .thenAccept(warps -> ctx.json(warps))
            .exceptionally(ex -> {
                plugin.getLogger().log(Level.SEVERE, "Error getting warps for UUID: " + uuidString, ex);
                ctx.status(HttpStatus.INTERNAL_SERVER_ERROR).json(error("Internal server error"));
                return null;
            }));
    }

    /**
     * Handles the POST /api/warp endpoint.
     *
     * @param ctx The context
     */
    private void createWarp(Context ctx) {
        WarpRequest request;
        try {
            request = ctx.bodyAsClass(WarpRequest.class);
            if (request.getUuid() == null || request.getName() == null || request.getWorldName() == null) {
                ctx.status(HttpStatus.BAD_REQUEST).json(error("Missing required fields"));
                return;
            }
             // Basic validation (consider more robust validation)
             if (!request.getName().matches("^[a-zA-Z0-9_]{3,32}$")) {
                 ctx.status(HttpStatus.BAD_REQUEST).json(error("Invalid warp name format (3-32 chars, a-z, A-Z, 0-9, _)"));
                 return;
             }
        } catch (Exception e) {
            ctx.status(HttpStatus.BAD_REQUEST).json(error("Invalid request body format"));
            return;
        }

        UUID uuid;
        try {
            uuid = UUID.fromString(request.getUuid());
        } catch (IllegalArgumentException e) {
            ctx.status(HttpStatus.BAD_REQUEST).json(error("Invalid UUID format"));
            return;
        }

        // Chain async operations and handle with ctx.future()
        ctx.future(() ->
            // Check limit and count first
            warpService.getWarpLimit(uuid)
                .thenComposeAsync(limit -> warpService.getWarpCount(uuid)
                        .thenComposeAsync(count -> {
                            if (count >= limit) {
                                // Use a completed future with an exception to signal handled error
                                return CompletableFuture.failedFuture(new ApiException("Warp limit reached", HttpStatus.BAD_REQUEST));
                            }

                            // Create the warp object (sync)
                            Warp warp = new Warp(
                                    0,
                                    uuid,
                                    request.getName(),
                                    request.getWorldName(),
                                    request.getX(),
                                    request.getY(),
                                    request.getZ(),
                                    request.getYaw(),
                                    request.getPitch(),
                                    System.currentTimeMillis()
                            );
                            // Call the async service method
                            return warpService.createWarpDirect(warp);
                        }, executor), executor)
                .thenAccept(createdWarp -> ctx.status(HttpStatus.CREATED).json(createdWarp))
                .exceptionally(ex -> {
                    handleApiException(ctx, ex, "Error creating warp");
                    return null;
                })
        );
    }

    /**
     * Handles the DELETE /api/warp/{uuid}/{name} endpoint.
     *
     * @param ctx The context
     */
    private void deleteWarp(Context ctx) {
        String uuidString = ctx.pathParam("uuid");
        String name = ctx.pathParam("name");
        UUID uuid;
        try {
            uuid = UUID.fromString(uuidString);
        } catch (IllegalArgumentException e) {
            ctx.status(HttpStatus.BAD_REQUEST).json(error("Invalid UUID format"));
            return;
        }

        // Validate warp name format
        if (name == null || !name.matches("^[a-zA-Z0-9_]{3,32}$")) {
            ctx.status(HttpStatus.BAD_REQUEST).json(error("Invalid warp name format (3-32 chars, a-z, A-Z, 0-9, _)"));
            return;
        }

        ctx.future(() -> warpService.deleteWarp(uuid, name)
            .thenAccept(deleted -> {
                if (deleted) {
                    ctx.status(HttpStatus.NO_CONTENT);
                } else {
                    ctx.status(HttpStatus.NOT_FOUND).json(error("Warp not found or could not be deleted"));
                }
            })
            .exceptionally(ex -> {
                handleApiException(ctx, ex, "Error deleting warp");
                return null;
            }));
    }

    /**
     * Handles the GET /api/player/{uuid}/limit endpoint.
     *
     * @param ctx The context
     */
    private void getWarpLimit(Context ctx) {
        String uuidString = ctx.pathParam("uuid");
        UUID uuid;
        try {
            uuid = UUID.fromString(uuidString);
        } catch (IllegalArgumentException e) {
            ctx.status(HttpStatus.BAD_REQUEST).json(error("Invalid UUID format"));
            return;
        }

        ctx.future(() -> warpService.getWarpLimit(uuid)
            .thenAccept(limit -> {
                 ctx.json(Map.of("uuid", uuidString, "limit", limit));
            })
            .exceptionally(ex -> {
                 handleApiException(ctx, ex, "Error getting warp limit");
                 return null;
            }));
    }

    /**
     * Handles the PUT /api/player/{uuid}/limit endpoint.
     *
     * @param ctx The context
     */
    private void setWarpLimit(Context ctx) {
        String uuidString = ctx.pathParam("uuid");
        UUID uuid;
        LimitRequest request;
        try {
            uuid = UUID.fromString(uuidString);
            request = ctx.bodyAsClass(LimitRequest.class);
             if (request.getLimit() < 0) {
                 ctx.status(HttpStatus.BAD_REQUEST).json(error("Limit must be a non-negative number"));
                 return;
             }
        } catch (IllegalArgumentException e) {
            ctx.status(HttpStatus.BAD_REQUEST).json(error("Invalid UUID format"));
            return;
        } catch (Exception e) {
            ctx.status(HttpStatus.BAD_REQUEST).json(error("Invalid request body format or limit value"));
            return;
        }

        ctx.future(() -> warpService.setWarpLimit(uuid, request.getLimit())
            .thenAccept(success -> {
                if (success) {
                    ctx.json(Map.of("uuid", uuidString, "limit", request.getLimit()));
                } else {
                    // If setWarpLimit fails internally without exception
                    ctx.status(HttpStatus.INTERNAL_SERVER_ERROR).json(error("Failed to set warp limit"));
                }
            })
             .exceptionally(ex -> {
                 handleApiException(ctx, ex, "Error setting warp limit");
                 return null;
            }));
    }

    /**
     * Centralized handler for exceptions within CompletableFuture chains for API endpoints.
     *
     * @param ctx The Javalin context.
     * @param throwable The caught exception.
     * @param defaultMessage The default message to log if it's not an ApiException.
     */
     private void handleApiException(Context ctx, Throwable throwable, String defaultMessage) {
         // Unwrap CompletionException if necessary
         Throwable cause = (throwable instanceof java.util.concurrent.CompletionException && throwable.getCause() != null)
                         ? throwable.getCause() : throwable;

         if (cause instanceof ApiException apiException) {
             ctx.status(apiException.getStatusCode()).json(error(apiException.getMessage()));
         } else {
             plugin.getLogger().log(Level.SEVERE, defaultMessage, cause);
             ctx.status(HttpStatus.INTERNAL_SERVER_ERROR).json(error("Internal server error"));
         }
     }

    /**
     * Creates an error response.
     *
     * @param message The error message
     * @return The error response
     */
    private Map<String, String> error(String message) {
        return new HashMap<>() {{ put("error", message); }};
    }

    /**
     * Custom exception class for API-specific errors with HTTP status codes.
     */
    private static class ApiException extends RuntimeException {
        private final HttpStatus statusCode;

        public ApiException(String message, HttpStatus statusCode) {
            super(message);
            this.statusCode = statusCode;
        }

        public HttpStatus getStatusCode() {
            return statusCode;
        }
    }

    /**
     * Helper class for rate limiting information.
     * Uses Atomic types for thread safety.
     */
    private static class RateLimitInfo {
        private final AtomicLong windowStart = new AtomicLong();
        private final AtomicInteger requestCount = new AtomicInteger(0);

        public RateLimitInfo(long initialWindowStart) {
            this.windowStart.set(initialWindowStart);
        }

        /**
         * Gets the start time of the current window.
         */
        public long getWindowStart() {
            return windowStart.get();
        }

        /**
         * Atomically increments the request count for the current window.
         *
         * @return The request count *before* incrementing.
         */
        public int incrementRequestCount() {
            return requestCount.getAndIncrement();
        }

        /**
         * Attempts to reset the window start time and counter if the expected current start time matches.
         *
         * @param expectedWindowStart The expected current window start time.
         * @param newWindowStart The new window start time.
         * @return True if the window was successfully reset, false otherwise.
         */
        public boolean tryResetWindow(long expectedWindowStart, long newWindowStart) {
            if (windowStart.compareAndSet(expectedWindowStart, newWindowStart)) {
                requestCount.set(0); // Reset counter after successfully setting new window
                return true;
            }
            return false;
        }
    }

    /**
     * Class for warp creation requests.
     */
    public static class WarpRequest {
        private String uuid;
        private String name;
        private String worldName;
        private double x;
        private double y;
        private double z;
        private float yaw;
        private float pitch;

        public String getUuid() {
            return uuid;
        }

        public void setUuid(String uuid) {
            this.uuid = uuid;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getWorldName() {
            return worldName;
        }

        public void setWorldName(String worldName) {
            this.worldName = worldName;
        }

        public double getX() {
            return x;
        }

        public void setX(double x) {
            this.x = x;
        }

        public double getY() {
            return y;
        }

        public void setY(double y) {
            this.y = y;
        }

        public double getZ() {
            return z;
        }

        public void setZ(double z) {
            this.z = z;
        }

        public float getYaw() {
            return yaw;
        }

        public void setYaw(float yaw) {
            this.yaw = yaw;
        }

        public float getPitch() {
            return pitch;
        }

        public void setPitch(float pitch) {
            this.pitch = pitch;
        }
    }

    /**
     * Class for warp limit requests.
     */
    public static class LimitRequest {
        private int limit;

        public int getLimit() {
            return limit;
        }

        public void setLimit(int limit) {
            this.limit = limit;
        }
    }
}
