package org.fourz.rvnkcore.util.chat;

import java.util.Map;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.fourz.rvnktools.linkMaker.LinkMaker;

/**
 * Interface for chat messaging services with formatting, PlaceholderAPI, and semantic methods.
 *
 * <p>Defines the contract for message formatting services across the RVNK plugin ecosystem.
 * Implementations should provide color code translation, PlaceholderAPI integration, and
 * semantic convenience methods with standard icon prefixes.</p>
 *
 * @since 1.0.0
 */
public interface ChatServiceInterface {

    // ========== Basic Messaging ==========

    /**
     * Sends a formatted message to a player.
     *
     * @param player the player to send the message to
     * @param message the message with '&' color codes
     */
    void sendMessage(Player player, String message);

    /**
     * Sends a formatted message with clickable links to a player.
     *
     * @param player the player to send the message to
     * @param message the message with '&' color codes
     * @param linkMaker the LinkMaker to generate clickable links
     */
    void sendMessage(Player player, String message, LinkMaker linkMaker);

    /**
     * Sends a formatted message with placeholder replacements to a player.
     *
     * @param player the player to send the message to
     * @param message the message with '&' color codes and placeholders
     * @param placeholders map of placeholder keys to replacement values
     */
    void sendMessage(Player player, String message, Map<String, String> placeholders);

    /**
     * Broadcasts a formatted message to all online players.
     *
     * @param message the message with '&' color codes
     */
    void broadcastMessage(String message);

    /**
     * Formats a message with color codes and optional PlaceholderAPI placeholders.
     *
     * @param player the player for PlaceholderAPI context (can be null)
     * @param message the message with '&' color codes
     * @return the formatted message with color codes and placeholders resolved
     */
    String formatMessage(Player player, String message);

    // ========== Semantic Methods ==========

    /**
     * Sends a success message with green checkmark prefix (&a✓).
     *
     * @param sender the command sender to send the message to
     * @param message the success message (without prefix)
     */
    void sendSuccess(CommandSender sender, String message);

    /**
     * Sends an error message with red X prefix (&c✖).
     *
     * @param sender the command sender to send the message to
     * @param message the error message (without prefix)
     */
    void sendError(CommandSender sender, String message);

    /**
     * Sends a warning message with yellow warning prefix (&e⚠).
     *
     * @param sender the command sender to send the message to
     * @param message the warning message (without prefix)
     */
    void sendWarning(CommandSender sender, String message);

    /**
     * Sends an info/processing message with gold gear prefix (&6⚙).
     *
     * @param sender the command sender to send the message to
     * @param message the info message (without prefix)
     */
    void sendInfo(CommandSender sender, String message);

    /**
     * Sends a usage/help message with red arrow prefix (&c▶).
     *
     * @param sender the command sender to send the message to
     * @param message the usage message (without prefix)
     */
    void sendUsage(CommandSender sender, String message);

    /**
     * Sends a tip/hint message with gray prefix (&7).
     *
     * @param sender the command sender to send the message to
     * @param message the tip message (without prefix)
     */
    void sendTip(CommandSender sender, String message);

    // ========== Special Message Types ==========

    /**
     * Sends a title message to a player.
     *
     * @param player the player to send the title to
     * @param message the title message with '&' color codes
     */
    void sendTitle(Player player, String message);

    /**
     * Sends an action bar message to a player.
     *
     * @param player the player to send the action bar to
     * @param message the action bar message with '&' color codes
     */
    void sendActionBar(Player player, String message);

    /**
     * Parses an action bar message with formatting.
     *
     * @param message the action bar message with '&' color codes
     * @return an array of BaseComponents for action bar display
     */
    net.md_5.bungee.api.chat.BaseComponent[] parseActionBar(String message);

    /**
     * Parses a title message with formatting.
     *
     * @param message the title message with '&' color codes
     * @return the formatted title message
     */
    String parseTitle(String message);
}
