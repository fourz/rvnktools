package org.fourz.rvnkcore.util;

import org.fourz.rvnktools.linkMaker.LinkMaker;
import org.bukkit.ChatColor;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.ChatMessageType;

/**
 * Utility class for formatting chat messages with color codes, links, titles, and action bars.
 *
 * <p>This class provides centralized message formatting functionality for the RVNK plugin ecosystem.
 * It handles Minecraft color code translation, clickable links, and special message types like
 * titles and action bars.</p>
 *
 * <p><b>Color Code Format:</b> Uses '&' as color code prefix (e.g., "&a" for green)</p>
 *
 * @since 1.0.0
 */
public class ChatFormat {

    /**
     * Converts '&' color codes in a string to Minecraft ChatColor.
     *
     * @param message the message with '&' color codes
     * @return the message with Minecraft color codes applied
     */
    public static String colorize(String message) {
        return ChatColor.translateAlternateColorCodes('&', message);
    }

    /**
     * Converts '&' color codes in a TextComponent to Minecraft ChatColor.
     *
     * @param message the TextComponent with '&' color codes
     * @return the legacy text with Minecraft color codes applied
     */
    public static String colorize(TextComponent message) {
        return ChatColor.translateAlternateColorCodes('&', message.toLegacyText());
    }

    /**
     * Removes all color codes from the given message.
     *
     * @param message the message with color codes
     * @return the message with all color codes removed
     */
    public static String stripColors(String message) {
        return ChatColor.stripColor(message);
    }

    /**
     * Creates a clickable link using the provided LinkMaker.
     *
     * @param text the text to display
     * @param linkMaker the LinkMaker to generate clickable links
     * @return a TextComponent with clickable link
     */
    public static TextComponent makeLink(String text, LinkMaker linkMaker) {
        return new TextComponent(linkMaker.replacePlaceholders(text));
    }

    /**
     * Parses the text and returns a clickable link with colorized text.
     * If linkMaker is null, returns a plain colorized TextComponent.
     *
     * @param text the text with '&' color codes
     * @param linkMaker the LinkMaker to generate clickable links (can be null)
     * @return a TextComponent with colorized text and optional clickable link
     */
    public static TextComponent parse(String text, LinkMaker linkMaker) {
        String colorized = colorize(text);
        if (linkMaker == null) {
            return new TextComponent(colorized);
        }
        return makeLink(colorized, linkMaker);
    }

    /**
     * Parses a TextComponent and returns a clickable link with colorized text.
     *
     * @param text the TextComponent with '&' color codes
     * @param linkMaker the LinkMaker to generate clickable links
     * @return a TextComponent with colorized text and clickable link
     */
    public static TextComponent parse(TextComponent text, LinkMaker linkMaker) {
        return makeLink(colorize(text), linkMaker);
    }

    /**
     * Creates a title message component.
     *
     * @param text the title text with '&' color codes
     * @return the colorized title text
     */
    public static String parseTitle(String text) {
        return colorize(text);
    }

    /**
     * Creates a title message component with link support.
     *
     * @param text the title text with '&' color codes
     * @param linkMaker the LinkMaker to generate clickable links
     * @return the legacy text with colorized title and clickable link
     */
    public static String parseTitle(String text, LinkMaker linkMaker) {
        return parse(text, linkMaker).toLegacyText();
    }

    /**
     * Creates a TextComponent for action bar messages.
     *
     * @param text the action bar text with '&' color codes
     * @return an array of BaseComponents for action bar display
     */
    public static net.md_5.bungee.api.chat.BaseComponent[] parseActionBar(String text) {
        return net.md_5.bungee.api.chat.TextComponent.fromLegacyText(colorize(text));
    }

    /**
     * Creates a TextComponent for action bar messages with link support.
     *
     * @param text the action bar text with '&' color codes
     * @param linkMaker the LinkMaker to generate clickable links
     * @return an array of BaseComponents for action bar display with clickable link
     */
    public static net.md_5.bungee.api.chat.BaseComponent[] parseActionBar(String text, LinkMaker linkMaker) {
        return net.md_5.bungee.api.chat.TextComponent.fromLegacyText(parse(text, linkMaker).toLegacyText());
    }

    /**
     * Parses MOTD (Message of the Day) text with line break support.
     * Converts "\n" escape sequences to actual line breaks and applies color codes.
     *
     * @param message the MOTD text with '&' color codes and "\n" for line breaks
     * @return the colorized MOTD text with line breaks
     */
    public static String parseMotd(String message) {
        // Insert line breaks
        message = message.replace("\\n", "\n");
        return colorize(message);
    }
}
