package de.dasjeff.warpMaster.listener;

import de.dasjeff.warpMaster.service.WarpService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

/**
 * Listener to pre-cache player data on join.
 */
public class PlayerJoinListener implements Listener {

    private final WarpService warpService;
    private final JavaPlugin plugin;

    public PlayerJoinListener(WarpService warpService, JavaPlugin plugin) {
        this.warpService = warpService;
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        // Asynchronously load warps for the joining player to warm up the cache
        if (plugin.getLogger().isLoggable(Level.FINE)) {
             plugin.getLogger().log(Level.FINE, "Pre-caching warps for joining player: {0}", event.getPlayer().getName());
        }
        warpService.getWarps(event.getPlayer().getUniqueId())
            .exceptionally(ex -> {
                plugin.getLogger().log(Level.WARNING, "Failed to pre-cache warps for player " + event.getPlayer().getName(), ex);
                return null;
            });
    }
} 