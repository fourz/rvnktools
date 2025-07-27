package org.fourz.rvnktools.util;

import org.fourz.rvnktools.linkMaker.LinkMaker;
import org.bukkit.ChatColor;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.ChatMessageType;

public class ChatFormat {  

    // Converts '&' color codes in a string to Minecraft ChatColor
    public static String colorize(String message) {
        return ChatColor.translateAlternateColorCodes('&', message);
    }        

    // Converts '&' color codes in a TextComponent to Minecraft ChatColor
    public static String colorize(TextComponent message) {
        return ChatColor.translateAlternateColorCodes('&', message.toLegacyText());            
    }

    // Removes all color codes from the given message
    public static String stripColors(String message) {
        return ChatColor.stripColor(message);
    }    

    // Creates a clickable link using the provided LinkMaker
    public static TextComponent makeLink(String text, LinkMaker linkMaker) {
        return new TextComponent(linkMaker.replacePlaceholders(text));            
    }

    // Parses the text and returns a clickable link with colorized text
    public static TextComponent parse(String text, LinkMaker linkMaker) {
        String colorized = colorize(text);
        if (linkMaker == null) {
            return new TextComponent(colorized);
        }
        return makeLink(colorized, linkMaker);
    }

    // Parses the text and returns a clickable link with colorized text
    public static TextComponent parse (TextComponent text, LinkMaker linkMaker) {
        return makeLink(colorize(text), linkMaker);            
    }

    // Creates a title message component
    public static String parseTitle(String text) {
        return colorize(text);
    }

    // Creates a title message component with link support
    public static String parseTitle(String text, LinkMaker linkMaker) {
        return parse(text, linkMaker).toLegacyText();
    }

    // Creates a TextComponent for action bar messages
    public static net.md_5.bungee.api.chat.BaseComponent[] parseActionBar(String text) {
        return net.md_5.bungee.api.chat.TextComponent.fromLegacyText(colorize(text));
    }

    // Creates a TextComponent for action bar messages with link support
    public static net.md_5.bungee.api.chat.BaseComponent[] parseActionBar(String text, LinkMaker linkMaker) {
        return net.md_5.bungee.api.chat.TextComponent.fromLegacyText(parse(text, linkMaker).toLegacyText());
    }

    public static String parseMotd(String message) {
        //insert line breaks
        message = message.replace("\\n", "\n");
        return colorize(message);
    }

    // Format method for convenience (alias for colorize)
    public static String format(String message) {
        return colorize(message);
    }
}
