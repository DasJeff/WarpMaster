package de.dasjeff.warpMaster.command;

import de.dasjeff.warpMaster.service.WarpService;
import de.dasjeff.warpMaster.util.ConfigUtil;
import de.dasjeff.warpMaster.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Command for admin functions.
 */
public class WarpMasterCommand extends BaseCommand {
    private final WarpService warpService;
    private final ConfigUtil configUtil;

    /**
     * Creates a new WarpMasterCommand instance.
     *
     * @param messageUtil The message utility
     * @param warpService The warp service
     * @param configUtil The configuration utility
     */
    public WarpMasterCommand(MessageUtil messageUtil, WarpService warpService, ConfigUtil configUtil) {
        super(messageUtil);
        this.warpService = warpService;
        this.configUtil = configUtil;
    }

    @Override
    protected boolean execute(CommandSender sender, String[] args) {
        if (args.length < 1) {
            sendHelp(sender);
            return true;
        }
        
        String subCommand = args[0].toLowerCase();
        
        switch (subCommand) {
            case "help":
                sendHelp(sender);
                break;
            case "reload":
                handleReload(sender);
                break;
            case "delete":
                handleDelete(sender, args);
                break;
            case "limit":
                handleLimit(sender, args);
                break;
            case "transfer":
                handleTransfer(sender, args);
                break;
            default:
                messageUtil.send(sender, "&cVerwendung: /warpmaster help");
                break;
        }
        
        return true;
    }

    @Override
    protected List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase();
            return Arrays.asList("help", "reload", "delete", "limit", "transfer").stream()
                    .filter(cmd -> cmd.startsWith(prefix))
                    .collect(Collectors.toList());
        } else if (args.length == 2) {
            String subCommand = args[0].toLowerCase();
            String prefix = args[1].toLowerCase();
            
            if (subCommand.equals("delete") || subCommand.equals("limit") || subCommand.equals("transfer")) {
                return Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(name -> name.toLowerCase().startsWith(prefix))
                        .collect(Collectors.toList());
            }
        } else if (args.length == 3) {
            String subCommand = args[0].toLowerCase();
            
            if (subCommand.equals("delete") || subCommand.equals("transfer")) {
                String playerName = args[1];
                Player player = Bukkit.getPlayer(playerName);
                
                if (player != null) {
                    String prefix = args[2].toLowerCase();
                    List<String> cachedNames = warpService.getCachedWarpNames(player.getUniqueId());
                    
                    return cachedNames.stream()
                            .filter(name -> name.toLowerCase().startsWith(prefix))
                            .collect(Collectors.toList());
                }
            }
        } else if (args.length == 4) {
            String subCommand = args[0].toLowerCase();
            
            if (subCommand.equals("transfer")) {
                String prefix = args[3].toLowerCase();
                return Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(name -> name.toLowerCase().startsWith(prefix))
                        .collect(Collectors.toList());
            }
        }
        
        return Collections.emptyList();
    }

    @Override
    protected boolean requiresPlayer() {
        return false;
    }

    @Override
    protected boolean requiresPermission() {
        return true;
    }

    @Override
    protected String getPermission() {
        return "warpmaster.admin";
    }
    
    /**
     * Sends help information to a command sender.
     *
     * @param sender The command sender
     */
    private void sendHelp(CommandSender sender) {
        messageUtil.send(sender, "&8=== &bWarpMaster Admin Commands &8===");
        messageUtil.send(sender, "&b/warpmaster help &7- Zeige diese Hilfe an");
        messageUtil.send(sender, "&b/warpmaster reload &7- Lade die Konfiguration neu");
        messageUtil.send(sender, "&b/warpmaster delete <player> <warp> &7- Lösche einen Warp");
        messageUtil.send(sender, "&b/warpmaster limit <player> <limit> &7- Setze das Warp-Limit für einen Spieler");
        messageUtil.send(sender, "&b/warpmaster transfer <source> <warp> <target> &7- Übertrage einen Warp von einem Spieler zu einem anderen");
    }
    
    /**
     * Handles the reload subcommand.
     *
     * @param sender The command sender
     */
    private void handleReload(CommandSender sender) {
        configUtil.reloadConfig();
        warpService.clearAllCaches();
        messageUtil.send(sender, "&aKonfiguration neu geladen. Service-Cache geleert.");
    }
    
    /**
     * Handles the delete subcommand.
     *
     * @param sender The command sender
     * @param args The command arguments
     */
    private void handleDelete(CommandSender sender, String[] args) {
        if (args.length < 3) {
            messageUtil.send(sender, "&cVerwendung: /warpmaster delete <player> <warp>");
            return;
        }
        
        String playerName = args[1];
        String warpName = args[2];
        
        if (!isValidWarpName(warpName)) {
             messageUtil.send(sender, "&cUngültiger Warp-Name. Verwende nur Buchstaben, Zahlen und Unterstriche (3-32 Zeichen).");
             return;
        }
        
        Player player = Bukkit.getPlayer(playerName);
        if (player == null) {
            messageUtil.sendConfigMessage(sender, "player-not-found", "player", playerName);
            return;
        }
        
        UUID playerUuid = player.getUniqueId();
        
        warpService.deleteWarp(playerUuid, warpName).thenAccept(deleted -> {
            if (deleted) {
                Map<String, String> placeholders = new HashMap<>();
                placeholders.put("name", warpName);
                messageUtil.sendConfigMessage(sender, "warp-deleted", placeholders);
            } else {
                messageUtil.sendConfigMessage(sender, "warp-not-found", "name", warpName);
            }
        });
    }
    
    /**
     * Handles the limit subcommand.
     *
     * @param sender The command sender
     * @param args The command arguments
     */
    private void handleLimit(CommandSender sender, String[] args) {
        if (args.length < 3) {
            messageUtil.send(sender, "&cVerwendung: /warpmaster limit <player> <limit>");
            return;
        }
        
        String playerName = args[1];
        
        Player player = Bukkit.getPlayer(playerName);
        if (player == null) {
            messageUtil.sendConfigMessage(sender, "player-not-found", "player", playerName);
            return;
        }
        
        int limit;
        try {
            limit = Integer.parseInt(args[2]);
            if (limit < 0) {
                messageUtil.send(sender, "&cLimit muss eine positive Zahl sein.");
                return;
            }
        } catch (NumberFormatException e) {
            messageUtil.send(sender, "&cLimit muss eine Zahl sein.");
            return;
        }
        
        UUID playerUuid = player.getUniqueId();
        
        warpService.setWarpLimit(playerUuid, limit).thenAccept(success -> {
            if (success) {
                messageUtil.send(sender, "&aSet warp limit for &e" + playerName + " &ato &e" + limit + "&a.");
            } else {
                messageUtil.send(sender, "&cWarp-Limit konnte nicht gesetzt werden.");
            }
        });
    }
    
    /**
     * Handles the transfer subcommand.
     *
     * @param sender The command sender
     * @param args The command arguments
     */
    private void handleTransfer(CommandSender sender, String[] args) {
        if (args.length < 4) {
            messageUtil.send(sender, "&cVerwendung: /warpmaster transfer <source> <warp> <target>");
            return;
        }
        
        String sourceName = args[1];
        String warpName = args[2];
        String targetName = args[3];
        
        if (!isValidWarpName(warpName)) {
             messageUtil.send(sender, "&cUngültiger Warp-Name. Verwende nur Buchstaben, Zahlen und Unterstriche (3-32 Zeichen).");
             return;
        }
        
        Player sourcePlayer = Bukkit.getPlayer(sourceName);
        if (sourcePlayer == null) {
            messageUtil.sendConfigMessage(sender, "player-not-found", "player", sourceName);
            return;
        }
        
        Player targetPlayer = Bukkit.getPlayer(targetName);
        if (targetPlayer == null) {
            messageUtil.sendConfigMessage(sender, "player-not-found", "player", targetName);
            return;
        }
        
        UUID sourceUuid = sourcePlayer.getUniqueId();
        UUID targetUuid = targetPlayer.getUniqueId();
        
        warpService.transferWarp(sourceUuid, targetUuid, warpName).thenAccept(result -> {
            if (result.isSuccess()) {
                messageUtil.send(sender, "&aWarp übertragen von &e" + sourceName + " &azu &e" + targetName + "&a.");
            } else {
                String errorKey = result.getErrorKey();
                String[] placeholders = result.getPlaceholders();
                
                if (placeholders.length >= 2) {
                    messageUtil.sendConfigMessage(sender, errorKey, placeholders[0], placeholders[1]);
                } else {
                    messageUtil.sendConfigMessage(sender, errorKey);
                }
            }
        });
    }

    /**
     * Validates a warp name (consistent with SetWarpCommand).
     *
     * @param name The name to validate
     * @return True if the name is valid, false otherwise
     */
    private boolean isValidWarpName(String name) {
        return name.matches("^[a-zA-Z0-9_]{3,32}$");
    }
}
