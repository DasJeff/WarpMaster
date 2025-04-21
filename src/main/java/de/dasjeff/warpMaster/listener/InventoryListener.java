package de.dasjeff.warpMaster.listener;

import de.dasjeff.warpMaster.service.WarpService;
import de.dasjeff.warpMaster.util.MessageUtil;
import de.dasjeff.warpMaster.WarpMaster;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

/**
 * Listener for inventory events.
 */
public class InventoryListener implements Listener {
    private final WarpService warpService;
    private final MessageUtil messageUtil;
    private final JavaPlugin plugin;
    // Store the expected title as a Component for efficient comparison
    private static final Component WARPS_INVENTORY_TITLE = MessageUtil.colorize("&8Warps");

    /**
     * Creates a new InventoryListener instance.
     *
     * @param warpService The warp service
     * @param messageUtil The message utility
     */
    public InventoryListener(WarpService warpService, MessageUtil messageUtil, JavaPlugin plugin) {
        this.warpService = warpService;
        this.messageUtil = messageUtil;
        this.plugin = plugin;
    }

    /**
     * Handles inventory click events.
     *
     * @param event The event
     */
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        InventoryView view = event.getView();
        Component title = view.title();

        // Compare the Component title directly
        if (WARPS_INVENTORY_TITLE.equals(title)) {
            event.setCancelled(true);

            ItemStack currentItem = event.getCurrentItem();
            if (currentItem == null || !currentItem.hasItemMeta()) {
                return;
            }

            Player player = (Player) event.getWhoClicked();
            ItemMeta meta = currentItem.getItemMeta();

            // --- Start PDC Logic ---
            if (meta != null) {
                PersistentDataContainer pdc = meta.getPersistentDataContainer();
                NamespacedKey key = WarpMaster.getWarpNameKey();

                if (pdc.has(key, PersistentDataType.STRING)) {
                    String warpName = pdc.get(key, PersistentDataType.STRING);

                    if (warpName != null && !warpName.isEmpty()) {
                        // Attempt teleportation
                        warpService.teleportToWarp(player, player.getUniqueId(), warpName).thenAccept(result -> {
                            if (result.isSuccess()) {
                                Map<String, String> placeholders = new HashMap<>();
                                placeholders.put("name", warpName);
                                messageUtil.sendConfigMessage(player, "warp-teleported", placeholders);
                            } else {
                                String errorKey = result.getErrorKey();
                                String[] placeholderArgs = result.getPlaceholders();

                                Map<String, String> errorPlaceholders = new HashMap<>();
                                if (placeholderArgs.length >= 1) errorPlaceholders.put("arg1", placeholderArgs[0]);
                                if (placeholderArgs.length >= 2) errorPlaceholders.put("arg2", placeholderArgs[1]);

                                messageUtil.sendConfigMessage(player, errorKey, errorPlaceholders);
                            }
                        });
                        player.closeInventory();
                    } else {
                        plugin.getLogger().log(Level.WARNING, "Clicked warp item for player " + player.getName() + " has empty PDC value.");
                        messageUtil.send(player, "&cError: Warp-Daten konnten nicht gelesen werden.");
                        player.closeInventory();
                    }
                } else {
                    // Item doesn't have the key - maybe an old item or from another plugin?
                    plugin.getLogger().log(Level.WARNING, "Clicked warp item for player " + player.getName() + " is missing PDC key ('"+key.toString()+"').");
                    messageUtil.send(player, "&cError: Warp-Daten konnten nicht gelesen werden.");
                    player.closeInventory();
                }
            } else {
                 plugin.getLogger().log(Level.WARNING, "Clicked warp item for player " + player.getName() + " has no ItemMeta.");
                 messageUtil.send(player, "&cError: Item-Daten konnten nicht gelesen werden.");
                 player.closeInventory();
            }
            // --- End PDC Logic ---
        }
    }
}
