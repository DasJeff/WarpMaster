package de.dasjeff.warpMaster.model;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.Objects;
import java.util.UUID;

/**
 * Represents a warp point in the game.
 */
public class Warp {
    private final int id;
    private final UUID ownerUuid;
    private final String name;
    private final String worldName;
    private final double x;
    private final double y;
    private final double z;
    private final float yaw;
    private final float pitch;
    private final long createdAt;

    /**
     * Creates a new warp from a location.
     *
     * @param id        The warp ID
     * @param ownerUuid The UUID of the player who owns this warp
     * @param name      The name of the warp
     * @param location  The location of the warp
     * @param createdAt The timestamp when the warp was created
     */
    public Warp(int id, UUID ownerUuid, String name, Location location, long createdAt) {
        this.id = id;
        this.ownerUuid = ownerUuid;
        this.name = name;
        this.worldName = location.getWorld().getName();
        this.x = location.getX();
        this.y = location.getY();
        this.z = location.getZ();
        this.yaw = location.getYaw();
        this.pitch = location.getPitch();
        this.createdAt = createdAt;
    }

    /**
     * Creates a new warp from individual components.
     *
     * @param id        The warp ID
     * @param ownerUuid The UUID of the player who owns this warp
     * @param name      The name of the warp
     * @param worldName The name of the world
     * @param x         The x coordinate
     * @param y         The y coordinate
     * @param z         The z coordinate
     * @param yaw       The yaw (horizontal rotation)
     * @param pitch     The pitch (vertical rotation)
     * @param createdAt The timestamp when the warp was created
     */
    public Warp(int id, UUID ownerUuid, String name, String worldName, double x, double y, double z, float yaw, float pitch, long createdAt) {
        this.id = id;
        this.ownerUuid = ownerUuid;
        this.name = name;
        this.worldName = worldName;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
        this.createdAt = createdAt;
    }

    /**
     * Gets the ID of this warp.
     *
     * @return The warp ID
     */
    public int getId() {
        return id;
    }

    /**
     * Gets the UUID of the player who owns this warp.
     *
     * @return The owner's UUID
     */
    public UUID getOwnerUuid() {
        return ownerUuid;
    }

    /**
     * Gets the name of this warp.
     *
     * @return The warp name
     */
    public String getName() {
        return name;
    }

    /**
     * Gets the name of the world this warp is in.
     *
     * @return The world name
     */
    public String getWorldName() {
        return worldName;
    }

    /**
     * Gets the x coordinate of this warp.
     *
     * @return The x coordinate
     */
    public double getX() {
        return x;
    }

    /**
     * Gets the y coordinate of this warp.
     *
     * @return The y coordinate
     */
    public double getY() {
        return y;
    }

    /**
     * Gets the z coordinate of this warp.
     *
     * @return The z coordinate
     */
    public double getZ() {
        return z;
    }

    /**
     * Gets the yaw (horizontal rotation) of this warp.
     *
     * @return The yaw
     */
    public float getYaw() {
        return yaw;
    }

    /**
     * Gets the pitch (vertical rotation) of this warp.
     *
     * @return The pitch
     */
    public float getPitch() {
        return pitch;
    }

    /**
     * Gets the timestamp when this warp was created.
     *
     * @return The creation timestamp
     */
    public long getCreatedAt() {
        return createdAt;
    }

    /**
     * Converts this warp to a Bukkit Location.
     *
     * @return The location of this warp, or null if the world doesn't exist
     */
    public Location toLocation() {
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return null;
        }
        return new Location(world, x, y, z, yaw, pitch);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Warp warp = (Warp) o;
        return id == warp.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Warp{" +
                "id=" + id +
                ", ownerUuid=" + ownerUuid +
                ", name='" + name + '\'' +
                ", worldName='" + worldName + '\'' +
                ", x=" + x +
                ", y=" + y +
                ", z=" + z +
                ", yaw=" + yaw +
                ", pitch=" + pitch +
                ", createdAt=" + createdAt +
                '}';
    }
}
