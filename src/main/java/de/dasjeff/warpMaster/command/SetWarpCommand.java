package de.dasjeff.warpMaster.command;

import de.dasjeff.warpMaster.service.WarpService;
import de.dasjeff.warpMaster.util.MessageUtil;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Command for setting warps.
 */
public class SetWarpCommand extends BaseCommand {
    private final WarpService warpService;

    /**
     * Creates a new SetWarpCommand instance.
     *
     * @param messageUtil The message utility
     * @param warpService The warp service
     */
    public SetWarpCommand(MessageUtil messageUtil, WarpService warpService) {
        super(messageUtil);
        this.warpService = warpService;
    }

    @Override
    protected boolean execute(CommandSender sender, String[] args) {
        if (args.length < 1) {
            messageUtil.send(sender, "&cVerwendung: /setwarp <name>");
            return true;
        }
        
        Player player = (Player) sender;
        String name = args[0];
        
        // Validate warp name length and characters
        if (!isValidWarpName(name)) {
            messageUtil.send(sender, "&cUngültiger Warp-Name. Verwende nur Buchstaben, Zahlen und Unterstriche (3-32 Zeichen).");
            return true;
        }
        
        warpService.createWarp(player, name).thenAccept(result -> {
            if (result.isSuccess()) {
                Map<String, String> placeholders = new HashMap<>();
                placeholders.put("name", name);
                messageUtil.sendConfigMessage(player, "warp-set", placeholders);
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
        return "warpmaster.warp.set";
    }
    
    /**
     * Validates a warp name.
     *
     * @param name The name to validate
     * @return True if the name is valid, false otherwise
     */
    private boolean isValidWarpName(String name) {
        // Allow 3 to 32 characters, letters, numbers, and underscore
        return name.matches("^[a-zA-Z0-9_]{3,32}$");
    }
}
