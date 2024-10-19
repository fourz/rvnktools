package org.fourz.rvnktools.util;

import org.bukkit.ChatColor;

public class ChatFormat {  

        // Convert '&' color codes to Minecraft ChatColor
        public static String colorize(String message) {
            return ChatColor.translateAlternateColorCodes('&', message);
        }

        // Strips colors if needed
        public static String stripColors(String message) {
            return ChatColor.stripColor(message);
        }
    }
