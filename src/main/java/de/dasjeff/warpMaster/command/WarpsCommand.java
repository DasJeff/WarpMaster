package de.dasjeff.warpMaster.command;

import de.dasjeff.warpMaster.model.Warp;
import de.dasjeff.warpMaster.service.WarpService;
import de.dasjeff.warpMaster.util.MessageUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;
import de.dasjeff.warpMaster.WarpMaster;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Command for listing warps.
 */
public class WarpsCommand extends BaseCommand {
    private final WarpService warpService;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private static final Component INVENTORY_TITLE = MessageUtil.colorize("&8Warps");

    /**
     * Creates a new WarpsCommand instance.
     *
     * @param messageUtil The message utility
     * @param warpService The warp service
     */
    public WarpsCommand(MessageUtil messageUtil, WarpService warpService) {
        super(messageUtil);
        this.warpService = warpService;
    }

    @Override
    protected boolean execute(CommandSender sender, String[] args) {
        Player player = (Player) sender;
        UUID targetUuid;
        
        if (args.length >= 1 && sender.hasPermission("warpmaster.admin")) {
            // Admin is trying to view another player's warps
            String targetPlayerName = args[0];
            Player targetPlayer = Bukkit.getPlayer(targetPlayerName);
            
            if (targetPlayer == null) {
                messageUtil.sendConfigMessage(sender, "player-not-found", "player", targetPlayerName);
                return true;
            }
            
            targetUuid = targetPlayer.getUniqueId();
        } else {
            // Player is viewing their own warps
            targetUuid = player.getUniqueId();
        }
        
        warpService.getWarps(targetUuid).thenAccept(warps -> {
            if (warps.isEmpty()) {
                messageUtil.send(player, "&cDu hast keine Warps.");
                return;
            }
            
            // Calculate inventory size (multiples of 9, max 54)
            int size = Math.min(54, (int) Math.ceil(warps.size() / 9.0) * 9);
            if (size == 0) size = 9; // Ensure minimum size
            
            // Create a GUI inventory using Component title
            Inventory inventory = Bukkit.createInventory(null, size, INVENTORY_TITLE);
            
            for (int i = 0; i < warps.size() && i < size; i++) {
                Warp warp = warps.get(i);
                ItemStack item = createWarpItem(warp);
                inventory.setItem(i, item);
            }
            
            // Open the inventory for the player
            Bukkit.getScheduler().runTask(Bukkit.getPluginManager().getPlugin("WarpMaster"), () -> {
                player.openInventory(inventory);
            });
        });
        
        return true;
    }

    @Override
    protected List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1 && sender.hasPermission("warpmaster.admin")) {
            String prefix = args[0].toLowerCase();
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(prefix))
                    .collect(Collectors.toList());
        }
        
        return new ArrayList<>();
    }

    @Override
    protected boolean requiresPlayer() {
        return true;
    }

    @Override
    protected boolean requiresPermission() {
        return true;
    }

    @Override
    protected String getPermission() {
        return "warpmaster.warp.list";
    }
    
    /**
     * Creates an item stack for a warp using Components and PersistentDataContainer.
     *
     * @param warp The warp
     * @return The item stack
     */
    private ItemStack createWarpItem(Warp warp) {
        ItemStack item = new ItemStack(Material.ENDER_PEARL);
        ItemMeta meta = item.getItemMeta();
        
        if (meta != null) {
            // Set display name using Component
            meta.displayName(MessageUtil.colorize("&b" + warp.getName()));
            
            // Create lore strings
            List<String> loreStrings = new ArrayList<>();
            loreStrings.add("&7Welt: &f" + warp.getWorldName());
            loreStrings.add("&7Position: &f" + String.format("%.1f, %.1f, %.1f", warp.getX(), warp.getY(), warp.getZ()));
            loreStrings.add("&7Erstellt: &f" + dateFormat.format(new Date(warp.getCreatedAt())));
            loreStrings.add("");
            loreStrings.add("&eKlicke um zu teleportieren");
            
            // Convert lore strings to Components and set lore
            meta.lore(MessageUtil.colorizeList(loreStrings));
            
            // Store the warp name in PersistentDataContainer
            NamespacedKey key = WarpMaster.getWarpNameKey();
            meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, warp.getName());
            
            item.setItemMeta(meta);
        }
        
        return item;
    }
}
