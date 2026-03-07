package org.fourz.rvnkcore.util.chat;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.fourz.rvnkcore.util.ChatFormat;
import org.fourz.rvnktools.linkMaker.LinkMaker;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;

import java.util.Map;

import me.clip.placeholderapi.PlaceholderAPI;

/**
 * Service for sending formatted messages to players and console with PlaceholderAPI integration.
 *
 * <p>ChatService provides a comprehensive API for message formatting with automatic color code
 * translation, PlaceholderAPI support, clickable links, and semantic convenience methods.</p>
 *
 * <p><b>Key Features:</b></p>
 * <ul>
 *   <li>Auto-detects PlaceholderAPI for placeholder replacement</li>
 *   <li>Semantic methods with automatic icon prefixes (✓, ✖, ⚙, ⚠, ▶)</li>
 *   <li>Support for titles, action bars, and broadcasts</li>
 *   <li>Console-compatible messaging (works with CommandSender)</li>
 * </ul>
 *
 * <p><b>Usage Example:</b></p>
 * <pre>
 * ChatService service = new ChatService();
 * service.sendSuccess(player, "Command executed successfully");
 * service.sendError(sender, "Invalid arguments");
 * service.sendInfo(console, "Processing data...");
 * </pre>
 *
 * @since 1.0.0
 */
public class ChatService implements ChatServiceInterface {
    private final boolean usingPlaceholderAPI;

    /**
     * Creates a new ChatService instance.
     * Auto-detects PlaceholderAPI availability for placeholder replacement.
     */
    public ChatService() {
        this.usingPlaceholderAPI = Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null;
    }

    // ========== Basic Messaging ==========

    /**
     * Sends a formatted message to a player.
     *
     * @param player the player to send the message to
     * @param message the message with '&' color codes
     */
    public void sendMessage(Player player, String message) {
        player.sendMessage(formatMessage(player, message));
    }

    /**
     * Sends a formatted message to a CommandSender (player or console).
     *
     * @param sender the command sender to send the message to
     * @param message the message with '&' color codes
     */
    public void sendMessage(CommandSender sender, String message) {
        if (sender instanceof Player) {
            sendMessage((Player) sender, message);
        } else {
            sender.sendMessage(ChatFormat.colorize(message));
        }
    }

    /**
     * Sends a formatted message with clickable links to a player.
     *
     * @param player the player to send the message to
     * @param message the message with '&' color codes
     * @param linkMaker the LinkMaker to generate clickable links
     */
    public void sendMessage(Player player, String message, LinkMaker linkMaker) {
        TextComponent textComponent = ChatFormat.parse(message, linkMaker);
        player.spigot().sendMessage(textComponent);
    }

    /**
     * Sends a formatted message with placeholder replacements to a player.
     *
     * @param player the player to send the message to
     * @param message the message with '&' color codes and placeholders
     * @param placeholders map of placeholder keys to replacement values
     */
    @Override
    public void sendMessage(Player player, String message, Map<String, String> placeholders) {
        String formattedMessage = message;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            formattedMessage = formattedMessage.replace(entry.getKey(), entry.getValue());
        }
        sendMessage(player, formattedMessage);
    }

    /**
     * Broadcasts a formatted message to all online players.
     *
     * @param message the message with '&' color codes
     */
    @Override
    public void broadcastMessage(String message) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            sendMessage(player, message);
        }
    }

    /**
     * Formats a message with color codes and optional PlaceholderAPI placeholders.
     *
     * @param player the player for PlaceholderAPI context (can be null)
     * @param message the message with '&' color codes
     * @return the formatted message with color codes and placeholders resolved
     */
    @Override
    public String formatMessage(Player player, String message) {
        String formattedMessage = ChatFormat.colorize(message);
        if (usingPlaceholderAPI && player != null) {
            formattedMessage = PlaceholderAPI.setPlaceholders(player, formattedMessage);
        }
        return formattedMessage;
    }

    // ========== Semantic Methods (NEW) ==========

    /**
     * Sends a success message with green checkmark prefix.
     * Prefix: &a✓
     *
     * @param sender the command sender to send the message to
     * @param message the success message (without prefix)
     */
    public void sendSuccess(CommandSender sender, String message) {
        sendMessage(sender, "&a✓ " + message);
    }

    /**
     * Sends an error message with red X prefix.
     * Prefix: &c✖
     *
     * @param sender the command sender to send the message to
     * @param message the error message (without prefix)
     */
    public void sendError(CommandSender sender, String message) {
        sendMessage(sender, "&c✖ " + message);
    }

    /**
     * Sends a warning message with yellow warning prefix.
     * Prefix: &e⚠
     *
     * @param sender the command sender to send the message to
     * @param message the warning message (without prefix)
     */
    public void sendWarning(CommandSender sender, String message) {
        sendMessage(sender, "&e⚠ " + message);
    }

    /**
     * Sends an info/processing message with gold gear prefix.
     * Prefix: &6⚙
     *
     * @param sender the command sender to send the message to
     * @param message the info message (without prefix)
     */
    public void sendInfo(CommandSender sender, String message) {
        sendMessage(sender, "&6⚙ " + message);
    }

    /**
     * Sends a usage/help message with red arrow prefix.
     * Prefix: &c▶
     *
     * @param sender the command sender to send the message to
     * @param message the usage message (without prefix)
     */
    public void sendUsage(CommandSender sender, String message) {
        sendMessage(sender, "&c▶ " + message);
    }

    /**
     * Sends a tip/hint message with gray prefix.
     * Prefix: &7
     *
     * @param sender the command sender to send the message to
     * @param message the tip message (without prefix)
     */
    public void sendTip(CommandSender sender, String message) {
        sendMessage(sender, "&7   " + message);
    }

    // ========== Special Message Types ==========

    /**
     * Sends a title message to a player.
     * Default timing: 10 ticks fade in, 100 ticks stay, 20 ticks fade out.
     *
     * @param player the player to send the title to
     * @param message the title message with '&' color codes
     */
    @Override
    public void sendTitle(Player player, String message) {
        player.sendTitle(ChatFormat.parseTitle(message), "", 10, 100, 20);
    }

    /**
     * Sends an action bar message to a player.
     *
     * @param player the player to send the action bar to
     * @param message the action bar message with '&' color codes
     */
    @Override
    public void sendActionBar(Player player, String message) {
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, ChatFormat.parseActionBar(message));
    }

    /**
     * Parses a title message with formatting.
     *
     * @param message the title message with '&' color codes
     * @return the formatted title message
     */
    public String parseTitle(String message) {
        return ChatFormat.parseTitle(formatMessage(null, message));
    }

    /**
     * Parses an action bar message with formatting.
     *
     * @param message the action bar message with '&' color codes
     * @return an array of BaseComponents for action bar display
     */
    public net.md_5.bungee.api.chat.BaseComponent[] parseActionBar(String message) {
        return ChatFormat.parseActionBar(formatMessage(null, message));
    }
}
