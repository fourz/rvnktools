package org.fourz.rvnktools.util;

import org.bukkit.ChatColor;

import net.md_5.bungee.api.chat.TextComponent;

public class ChatFormat {  

        // Convert '&' color codes to Minecraft ChatColor
        public static String colorize(String message) {
            return ChatColor.translateAlternateColorCodes('&', message);
        }        
        public static String colorize(TextComponent message) {
            return ChatColor.translateAlternateColorCodes('&', message.toLegacyText());            
        }

        // Strips colors if needed
        public static String stripColors(String message) {
            return ChatColor.stripColor(message);
        }    

        //generate parseMarkdown method ability to craft a hyperlinks,bold,italics,underline using Markdown
        public static TextComponent parseMarkdown(String message) {
            TextComponent textComponent = new TextComponent();
            
            //implement markdown-parsing logic:
            // 1. Hyperlinks
            // 2. Bold
            // 3. Italics
            // 4. Underline
            // 5. Strikethrough
            // 6. Obfuscated
            
            return textComponent;
        }
        
    }
