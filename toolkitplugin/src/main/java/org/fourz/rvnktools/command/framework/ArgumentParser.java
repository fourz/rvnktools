package org.fourz.rvnktools.command.framework;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Utility class for parsing and handling command arguments.
 * Provides common parsing methods for various data types and Bukkit objects.
 */
public class ArgumentParser {
    
    /**
     * Join arguments starting from a specific index into a single string.
     * 
     * @param args The arguments array
     * @param startIndex The index to start joining from
     * @return The joined string
     */
    public static String joinArgs(String[] args, int startIndex) {
        return joinArgs(args, startIndex, " ");
    }
    
    /**
     * Join arguments starting from a specific index with a custom separator.
     * 
     * @param args The arguments array
     * @param startIndex The index to start joining from
     * @param separator The separator to use between arguments
     * @return The joined string
     */
    public static String joinArgs(String[] args, int startIndex, String separator) {
        if (startIndex >= args.length) {
            return "";
        }
        
        StringBuilder result = new StringBuilder();
        for (int i = startIndex; i < args.length; i++) {
            if (i > startIndex) {
                result.append(separator);
            }
            result.append(args[i]);
        }
        return result.toString();
    }
    
    /**
     * Parse a player argument from a string.
     * 
     * @param sender The command sender (for error messages)
     * @param playerName The player name to parse
     * @param requireOnline Whether the player must be online
     * @return The Player object, or null if not found/invalid
     */
    public static Player parsePlayer(CommandSender sender, String playerName, boolean requireOnline) {
        Player target = Bukkit.getPlayer(playerName);
        
        if (target == null) {
            if (requireOnline) {
                sender.sendMessage("§cPlayer '" + playerName + "' is not online.");
            } else {
                sender.sendMessage("§cPlayer '" + playerName + "' not found.");
            }
            return null;
        }
        
        return target;
    }
    
    /**
     * Parse an offline player argument from a string.
     * 
     * @param sender The command sender (for error messages)
     * @param playerName The player name to parse
     * @return The OfflinePlayer object, or null if not found
     */
    public static OfflinePlayer parseOfflinePlayer(CommandSender sender, String playerName) {
        // First try to get online player
        Player onlinePlayer = Bukkit.getPlayer(playerName);
        if (onlinePlayer != null) {
            return onlinePlayer;
        }
        
        // Try to get offline player
        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(playerName);
        if (offlinePlayer != null && offlinePlayer.hasPlayedBefore()) {
            return offlinePlayer;
        }
        
        sender.sendMessage("§cPlayer '" + playerName + "' has never played on this server.");
        return null;
    }
    
    /**
     * Parse a world argument from a string.
     * 
     * @param sender The command sender (for error messages)
     * @param worldName The world name to parse
     * @return The World object, or null if not found
     */
    public static World parseWorld(CommandSender sender, String worldName) {
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            sender.sendMessage("§cWorld '" + worldName + "' not found.");
            return null;
        }
        return world;
    }
    
    /**
     * Parse an integer with validation and error handling.
     * 
     * @param sender The command sender (for error messages)
     * @param input The input string
     * @param fieldName The name of the field being parsed (for error messages)
     * @return The parsed integer, or null if invalid
     */
    public static Integer parseInt(CommandSender sender, String input, String fieldName) {
        try {
            return Integer.parseInt(input);
        } catch (NumberFormatException e) {
            sender.sendMessage("§c'" + input + "' is not a valid " + fieldName + ".");
            return null;
        }
    }
    
    /**
     * Parse an integer within a specific range.
     * 
     * @param sender The command sender (for error messages)
     * @param input The input string
     * @param fieldName The name of the field being parsed (for error messages)
     * @param min Minimum allowed value (inclusive)
     * @param max Maximum allowed value (inclusive)
     * @return The parsed integer, or null if invalid
     */
    public static Integer parseInt(CommandSender sender, String input, String fieldName, int min, int max) {
        Integer value = parseInt(sender, input, fieldName);
        if (value == null) {
            return null;
        }
        
        if (value < min || value > max) {
            sender.sendMessage("§c" + fieldName + " must be between " + min + " and " + max + ".");
            return null;
        }
        
        return value;
    }
    
    /**
     * Parse a double with validation and error handling.
     * 
     * @param sender The command sender (for error messages)
     * @param input The input string
     * @param fieldName The name of the field being parsed (for error messages)
     * @return The parsed double, or null if invalid
     */
    public static Double parseDouble(CommandSender sender, String input, String fieldName) {
        try {
            return Double.parseDouble(input);
        } catch (NumberFormatException e) {
            sender.sendMessage("§c'" + input + "' is not a valid " + fieldName + ".");
            return null;
        }
    }
    
    /**
     * Parse a double within a specific range.
     * 
     * @param sender The command sender (for error messages)
     * @param input The input string
     * @param fieldName The name of the field being parsed (for error messages)
     * @param min Minimum allowed value (inclusive)
     * @param max Maximum allowed value (inclusive)
     * @return The parsed double, or null if invalid
     */
    public static Double parseDouble(CommandSender sender, String input, String fieldName, double min, double max) {
        Double value = parseDouble(sender, input, fieldName);
        if (value == null) {
            return null;
        }
        
        if (value < min || value > max) {
            sender.sendMessage("§c" + fieldName + " must be between " + min + " and " + max + ".");
            return null;
        }
        
        return value;
    }
    
    /**
     * Parse a boolean from various string representations.
     * Accepts: true/false, yes/no, on/off, 1/0
     * 
     * @param sender The command sender (for error messages)
     * @param input The input string
     * @param fieldName The name of the field being parsed (for error messages)
     * @return The parsed boolean, or null if invalid
     */
    public static Boolean parseBoolean(CommandSender sender, String input, String fieldName) {
        String lower = input.toLowerCase();
        
        switch (lower) {
            case "true":
            case "yes":
            case "on":
            case "1":
                return true;
            case "false":
            case "no":
            case "off":
            case "0":
                return false;
            default:
                sender.sendMessage("§c'" + input + "' is not a valid " + fieldName + ". Use true/false, yes/no, on/off, or 1/0.");
                return null;
        }
    }
    
    /**
     * Parse an enum value from a string.
     * 
     * @param sender The command sender (for error messages)
     * @param input The input string
     * @param enumClass The enum class
     * @param fieldName The name of the field being parsed (for error messages)
     * @param <T> The enum type
     * @return The parsed enum value, or null if invalid
     */
    public static <T extends Enum<T>> T parseEnum(CommandSender sender, String input, Class<T> enumClass, String fieldName) {
        try {
            return Enum.valueOf(enumClass, input.toUpperCase());
        } catch (IllegalArgumentException e) {
            String validValues = Arrays.stream(enumClass.getEnumConstants())
                    .map(Enum::name)
                    .map(String::toLowerCase)
                    .collect(Collectors.joining(", "));
            
            sender.sendMessage("§c'" + input + "' is not a valid " + fieldName + ". Valid values: " + validValues);
            return null;
        }
    }
    
    /**
     * Parse a time duration from a string.
     * Supports formats like: 30s, 5m, 2h, 1d
     * 
     * @param sender The command sender (for error messages)
     * @param input The input string
     * @return The duration in seconds, or null if invalid
     */
    public static Long parseTimeDuration(CommandSender sender, String input) {
        if (input == null || input.isEmpty()) {
            sender.sendMessage("§cTime duration cannot be empty.");
            return null;
        }
        
        String timeStr = input.toLowerCase().trim();
        char lastChar = timeStr.charAt(timeStr.length() - 1);
        
        long multiplier;
        String numberPart;
        
        switch (lastChar) {
            case 's': // seconds
                multiplier = 1;
                numberPart = timeStr.substring(0, timeStr.length() - 1);
                break;
            case 'm': // minutes
                multiplier = 60;
                numberPart = timeStr.substring(0, timeStr.length() - 1);
                break;
            case 'h': // hours
                multiplier = 3600;
                numberPart = timeStr.substring(0, timeStr.length() - 1);
                break;
            case 'd': // days
                multiplier = 86400;
                numberPart = timeStr.substring(0, timeStr.length() - 1);
                break;
            default:
                // No suffix, assume seconds
                multiplier = 1;
                numberPart = timeStr;
                break;
        }
        
        try {
            long number = Long.parseLong(numberPart);
            if (number < 0) {
                sender.sendMessage("§cTime duration must be positive.");
                return null;
            }
            return number * multiplier;
        } catch (NumberFormatException e) {
            sender.sendMessage("§c'" + input + "' is not a valid time duration. Use formats like: 30s, 5m, 2h, 1d");
            return null;
        }
    }
    
    /**
     * Get a list of online player names for tab completion.
     * 
     * @param prefix The prefix to filter by (can be empty)
     * @return List of matching player names
     */
    public static List<String> getOnlinePlayerNames(String prefix) {
        String lowerPrefix = prefix.toLowerCase();
        return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(name -> name.toLowerCase().startsWith(lowerPrefix))
                .collect(Collectors.toList());
    }
    
    /**
     * Get a list of world names for tab completion.
     * 
     * @param prefix The prefix to filter by (can be empty)
     * @return List of matching world names
     */
    public static List<String> getWorldNames(String prefix) {
        String lowerPrefix = prefix.toLowerCase();
        return Bukkit.getWorlds().stream()
                .map(World::getName)
                .filter(name -> name.toLowerCase().startsWith(lowerPrefix))
                .collect(Collectors.toList());
    }
    
    /**
     * Get a filtered list of strings for tab completion.
     * 
     * @param options The list of available options
     * @param prefix The prefix to filter by
     * @return List of matching options
     */
    public static List<String> filterTabComplete(List<String> options, String prefix) {
        if (prefix == null || prefix.isEmpty()) {
            return new ArrayList<>(options);
        }
        
        String lowerPrefix = prefix.toLowerCase();
        return options.stream()
                .filter(option -> option.toLowerCase().startsWith(lowerPrefix))
                .collect(Collectors.toList());
    }
    
    /**
     * Get a filtered list of strings for tab completion (case-insensitive).
     * 
     * @param options The array of available options
     * @param prefix The prefix to filter by
     * @return List of matching options
     */
    public static List<String> filterTabComplete(String[] options, String prefix) {
        return filterTabComplete(Arrays.asList(options), prefix);
    }
    
    /**
     * Create a map of arguments from a key-value format.
     * Expects arguments in the format: key1:value1 key2:value2
     * 
     * @param args The arguments to parse
     * @param startIndex The index to start parsing from
     * @return Map of key-value pairs
     */
    public static Map<String, String> parseKeyValueArgs(String[] args, int startIndex) {
        Map<String, String> result = new HashMap<>();
        
        for (int i = startIndex; i < args.length; i++) {
            String arg = args[i];
            int colonIndex = arg.indexOf(':');
            
            if (colonIndex > 0 && colonIndex < arg.length() - 1) {
                String key = arg.substring(0, colonIndex);
                String value = arg.substring(colonIndex + 1);
                result.put(key, value);
            }
        }
        
        return result;
    }
}
