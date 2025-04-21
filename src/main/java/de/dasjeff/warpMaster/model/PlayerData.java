package de.dasjeff.warpMaster.model;

import java.util.UUID;

/**
 * Represents player-specific data for the WarpMaster plugin.
 */
public class PlayerData {
    private final UUID uuid;
    private int warpLimit;
    private long lastWarpTime;

    /**
     * Creates a new PlayerData instance.
     *
     * @param uuid        The UUID of the player
     * @param warpLimit   The maximum number of warps this player can have
     * @param lastWarpTime The timestamp of the last warp usage
     */
    public PlayerData(UUID uuid, int warpLimit, long lastWarpTime) {
        this.uuid = uuid;
        this.warpLimit = warpLimit;
        this.lastWarpTime = lastWarpTime;
    }

    /**
     * Gets the UUID of the player.
     *
     * @return The player's UUID
     */
    public UUID getUuid() {
        return uuid;
    }

    /**
     * Gets the maximum number of warps this player can have.
     *
     * @return The warp limit
     */
    public int getWarpLimit() {
        return warpLimit;
    }

    /**
     * Sets the maximum number of warps this player can have.
     *
     * @param warpLimit The new warp limit
     */
    public void setWarpLimit(int warpLimit) {
        this.warpLimit = warpLimit;
    }

    /**
     * Gets the timestamp of the last warp usage.
     *
     * @return The last warp timestamp
     */
    public long getLastWarpTime() {
        return lastWarpTime;
    }

    /**
     * Sets the timestamp of the last warp usage.
     *
     * @param lastWarpTime The new last warp timestamp
     */
    public void setLastWarpTime(long lastWarpTime) {
        this.lastWarpTime = lastWarpTime;
    }

    /**
     * Checks if the player is on cooldown for warping.
     *
     * @param cooldownSeconds The cooldown period in seconds
     * @return True if the player is on cooldown, false otherwise
     */
    public boolean isOnCooldown(int cooldownSeconds) {
        long currentTime = System.currentTimeMillis();
        long cooldownMillis = cooldownSeconds * 1000L;
        return (currentTime - lastWarpTime) < cooldownMillis;
    }

    /**
     * Gets the remaining cooldown time in seconds.
     *
     * @param cooldownSeconds The cooldown period in seconds
     * @return The remaining cooldown time in seconds, or 0 if not on cooldown
     */
    public int getRemainingCooldown(int cooldownSeconds) {
        if (!isOnCooldown(cooldownSeconds)) {
            return 0;
        }
        
        long currentTime = System.currentTimeMillis();
        long cooldownMillis = cooldownSeconds * 1000L;
        long elapsedMillis = currentTime - lastWarpTime;
        long remainingMillis = cooldownMillis - elapsedMillis;
        
        return (int) Math.ceil(remainingMillis / 1000.0);
    }
}
