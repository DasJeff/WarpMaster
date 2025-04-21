package de.dasjeff.warpMaster.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Utility class for handling messages in the plugin using Adventure Components.
 */
public class MessageUtil {
    private final FileConfiguration config;
    private final Component prefix;

    /**
     * Creates a new MessageUtil instance.
     *
     * @param config The plugin configuration
     */
    public MessageUtil(FileConfiguration config) {
        this.config = config;
        this.prefix = colorize(config.getString("messages.prefix", "&8[&bWarpMaster&8] &7"));
    }

    /**
     * Sends a message component to a command sender..
     *
     * @param sender  The command sender to send the message to
     * @param messageComponent The message component to send
     */
    public void sendComponent(CommandSender sender, Component messageComponent) {
        Component fullMessage = prefix.append(messageComponent);

        if (sender instanceof Player player) {
            player.sendMessage(fullMessage);
        } else {
            sender.sendMessage(LegacyComponentSerializer.legacySection().serialize(fullMessage));
        }
    }

    /**
     * Sends a legacy message string to a command sender.
     *
     * @param sender  The command sender to send the message to
     * @param message The legacy message string to send
     */
    public void send(CommandSender sender, String message) {
        sendComponent(sender, colorize(message));
    }

    /**
     * Sends a message from the configuration to a command sender.
     *
     * @param sender      The command sender to send the message to
     * @param configKey   The key of the message in the configuration
     * @param placeholders The placeholders to replace in the message
     */
    public void sendConfigMessage(CommandSender sender, String configKey, Map<String, String> placeholders) {
        String rawMessage = config.getString("messages." + configKey, "Message not found: " + configKey);
        
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            rawMessage = rawMessage.replace("%" + entry.getKey() + "%", entry.getValue());
        }
        
        sendComponent(sender, colorize(rawMessage));
    }

    /**
     * Sends a message from the configuration to a command sender.
     *
     * @param sender    The command sender to send the message to
     * @param configKey The key of the message in the configuration
     */
    public void sendConfigMessage(CommandSender sender, String configKey) {
        sendConfigMessage(sender, configKey, new HashMap<>());
    }

    /**
     * Sends a message from the configuration to a command sender with a single placeholder.
     *
     * @param sender         The command sender to send the message to
     * @param configKey      The key of the message in the configuration
     * @param placeholderKey The key of the placeholder
     * @param placeholderValue The value to replace the placeholder with
     */
    public void sendConfigMessage(CommandSender sender, String configKey, String placeholderKey, String placeholderValue) {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put(placeholderKey, placeholderValue);
        sendConfigMessage(sender, configKey, placeholders);
    }

    /**
     * Converts a legacy string with '&' color codes into an Adventure Component.
     *
     * @param text The legacy string to colorize
     * @return The resulting Adventure Component
     */
    public static Component colorize(String text) {
        if (text == null || text.isEmpty()) {
            return Component.empty();
        }
        return LegacyComponentSerializer.legacyAmpersand().deserialize(text);
    }

    /**
    * Converts a list of legacy strings with '&' color codes into a List of Adventure Components.
    *
    * @param lines The list of legacy strings
    * @return The list of resulting Adventure Components
    */
    public static java.util.List<Component> colorizeList(java.util.List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        return lines.stream()
                    .map(MessageUtil::colorize)
                    .collect(Collectors.toList());
    }

    /**
     * Serializes an Adventure Component back into a legacy string with '&' color codes.
     *
     * @param component The component to serialize
     * @return The legacy string representation
     */
    public static String serialize(Component component) {
        if (component == null) {
            return "";
        }
        return LegacyComponentSerializer.legacyAmpersand().serialize(component);
    }

    /**
    * Strips formatting and color codes from a legacy string using Adventure serializers.
    *
    * @param text The legacy string with color codes
    * @return The plain text string
    */
    public static String stripColors(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        // Deserialize the legacy text into a Component
        Component component = LegacyComponentSerializer.legacyAmpersand().deserialize(text);
        // Serialize the Component to plain text, effectively stripping all formatting
        return PlainTextComponentSerializer.plainText().serialize(component);
    }
}
