package de.dasjeff.warpMaster.command;

import de.dasjeff.warpMaster.util.MessageUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;

/**
 * Base class for commands.
 */
public abstract class BaseCommand implements CommandExecutor, TabCompleter {
    protected final MessageUtil messageUtil;

    /**
     * Creates a new BaseCommand instance.
     *
     * @param messageUtil The message utility
     */
    public BaseCommand(MessageUtil messageUtil) {
        this.messageUtil = messageUtil;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (requiresPlayer() && !(sender instanceof Player)) {
            messageUtil.send(sender, "&cThis command can only be used by players.");
            return true;
        }
        
        if (requiresPermission() && !sender.hasPermission(getPermission())) {
            messageUtil.sendConfigMessage(sender, "no-permission");
            return true;
        }
        
        return execute(sender, args);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (requiresPlayer() && !(sender instanceof Player)) {
            return Collections.emptyList();
        }
        
        if (requiresPermission() && !sender.hasPermission(getPermission())) {
            return Collections.emptyList();
        }
        
        return tabComplete(sender, args);
    }

    /**
     * Executes the command.
     *
     * @param sender The command sender
     * @param args The command arguments
     * @return True if the command was executed successfully, false otherwise
     */
    protected abstract boolean execute(CommandSender sender, String[] args);

    /**
     * Provides tab completions for the command.
     *
     * @param sender The command sender
     * @param args The command arguments
     * @return A list of tab completions
     */
    protected abstract List<String> tabComplete(CommandSender sender, String[] args);

    /**
     * Checks if the command requires a player.
     *
     * @return True if the command requires a player, false otherwise
     */
    protected abstract boolean requiresPlayer();

    /**
     * Checks if the command requires a permission.
     *
     * @return True if the command requires a permission, false otherwise
     */
    protected abstract boolean requiresPermission();

    /**
     * Gets the permission required for the command.
     *
     * @return The permission
     */
    protected abstract String getPermission();
}
