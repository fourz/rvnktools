package org.fourz.rvnktools.util;

import org.fourz.rvnktools.linkMaker.LinkMaker;
import org.bukkit.ChatColor;
import net.md_5.bungee.api.chat.TextComponent;

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
    public static TextComponent parse (String text, LinkMaker linkMaker) {
        return makeLink(colorize(text), linkMaker);            
    }        
}
