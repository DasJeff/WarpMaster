package de.dasjeff.warpMaster.command;

import de.dasjeff.warpMaster.service.WarpService;
import de.dasjeff.warpMaster.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Command for teleporting to warps.
 */
public class WarpCommand extends BaseCommand {
    private final WarpService warpService;

    /**
     * Creates a new WarpCommand instance.
     *
     * @param messageUtil The message utility
     * @param warpService The warp service
     */
    public WarpCommand(MessageUtil messageUtil, WarpService warpService) {
        super(messageUtil);
        this.warpService = warpService;
    }

    @Override
    protected boolean execute(CommandSender sender, String[] args) {
        if (args.length < 1) {
            messageUtil.send(sender, "&cVerwendung: /warp <name> [player]");
            return true;
        }
        
        if (!(sender instanceof Player player)) {
            messageUtil.send(sender, "&cThis command can only be used by players.");
            return true;
        }
        
        String name = args[0];
        UUID ownerUuid;
        
        if (args.length >= 2 && sender.hasPermission("warpmaster.admin")) {
            // Admin is trying to teleport to another player's warp
            String targetPlayerName = args[1];
            Player targetPlayer = Bukkit.getPlayer(targetPlayerName);
            
            if (targetPlayer == null) {
                messageUtil.sendConfigMessage(sender, "player-not-found", "player", targetPlayerName);
                return true;
            }
            
            ownerUuid = targetPlayer.getUniqueId();
        } else {
            // Player is teleporting to their own warp
            ownerUuid = player.getUniqueId();
        }
        
        warpService.teleportToWarp(player, ownerUuid, name).thenAccept(result -> {
            if (result.isSuccess()) {
                Map<String, String> placeholders = new HashMap<>();
                placeholders.put("name", name);
                messageUtil.sendConfigMessage(player, "warp-teleported", placeholders);
            } else {
                String errorKey = result.getErrorKey();
                String[] placeholders = result.getPlaceholders();
                
                if (placeholders.length >= 2) {
                    messageUtil.sendConfigMessage(player, errorKey, placeholders[0], placeholders[1]);
                } else {
                    messageUtil.sendConfigMessage(player, errorKey);
                }
            }
        });
        
        return true;
    }

    @Override
    protected List<String> tabComplete(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            return new ArrayList<>();
        }
        
        Player player = (Player) sender;
        
        if (args.length == 1) {
            String prefix = args[0].toLowerCase();
            // Use cached warp names for synchronous tab completion
            List<String> cachedNames = warpService.getCachedWarpNames(player.getUniqueId());
            
            return cachedNames.stream()
                    .filter(name -> name.toLowerCase().startsWith(prefix))
                    .collect(Collectors.toList());
        } else if (args.length == 2 && sender.hasPermission("warpmaster.admin")) {
            String prefix = args[1].toLowerCase();
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(prefix))
                    .collect(Collectors.toList());
        }
        
        return Collections.emptyList();
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
        return "warpmaster.warp.use";
    }
}
