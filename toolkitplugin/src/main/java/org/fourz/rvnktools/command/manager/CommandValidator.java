package org.fourz.rvnktools.command.manager;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.regex.Pattern;

/**
 * Utility class for common command input validation.
 * Provides standard validation methods that can be used across all commands.
 */
public class CommandValidator {
    
    // Common regex patterns for validation
    private static final Pattern ALPHANUMERIC_PATTERN = Pattern.compile("^[a-zA-Z0-9_]+$");
    private static final Pattern NUMBER_PATTERN = Pattern.compile("^-?\\d+$");
    private static final Pattern DECIMAL_PATTERN = Pattern.compile("^-?\\d+(\\.\\d+)?$");
    private static final Pattern BOOLEAN_PATTERN = Pattern.compile("^(true|false|yes|no|on|off|1|0)$", Pattern.CASE_INSENSITIVE);
    
    /**
     * Validate that the sender is a player.
     * 
     * @param sender The command sender
     * @return The sender as a Player, or null if not a player
     */
    public static Player validatePlayer(CommandSender sender) {
        if (sender instanceof Player) {
            return (Player) sender;
        }
        return null;
    }
    
    /**
     * Validate that the sender is a player and send error message if not.
     * 
     * @param sender The command sender
     * @param errorMessage Custom error message, or null for default
     * @return The sender as a Player, or null if not a player
     */
    public static Player validatePlayer(CommandSender sender, String errorMessage) {
        Player player = validatePlayer(sender);
        if (player == null) {
            String message = errorMessage != null ? errorMessage : "This command can only be used by players.";
            sender.sendMessage("§c" + message);
        }
        return player;
    }
    
    /**
     * Validate that enough arguments are provided.
     * 
     * @param args The command arguments
     * @param minArgs Minimum number of arguments required
     * @return true if validation passes
     */
    public static boolean validateArgsCount(String[] args, int minArgs) {
        return args.length >= minArgs;
    }
    
    /**
     * Validate that enough arguments are provided and send error message if not.
     * 
     * @param sender The command sender
     * @param args The command arguments
     * @param minArgs Minimum number of arguments required
     * @param usage Usage string to display if validation fails
     * @return true if validation passes
     */
    public static boolean validateArgsCount(CommandSender sender, String[] args, int minArgs, String usage) {
        if (!validateArgsCount(args, minArgs)) {
            sender.sendMessage("§cUsage: " + usage);
            return false;
        }
        return true;
    }
    
    /**
     * Validate that the argument count is within a specific range.
     * 
     * @param sender The command sender
     * @param args The command arguments
     * @param minArgs Minimum number of arguments required
     * @param maxArgs Maximum number of arguments allowed
     * @param usage Usage string to display if validation fails
     * @return true if validation passes
     */
    public static boolean validateArgsRange(CommandSender sender, String[] args, int minArgs, int maxArgs, String usage) {
        if (args.length < minArgs || args.length > maxArgs) {
            sender.sendMessage("§cUsage: " + usage);
            return false;
        }
        return true;
    }
    
    /**
     * Validate that a string contains only alphanumeric characters and underscores.
     * 
     * @param input The input string
     * @return true if validation passes
     */
    public static boolean isAlphanumeric(String input) {
        return input != null && ALPHANUMERIC_PATTERN.matcher(input).matches();
    }
    
    /**
     * Validate that a string contains only alphanumeric characters and underscores.
     * 
     * @param sender The command sender
     * @param input The input string
     * @param fieldName Name of the field being validated (for error message)
     * @return true if validation passes
     */
    public static boolean validateAlphanumeric(CommandSender sender, String input, String fieldName) {
        if (!isAlphanumeric(input)) {
            sender.sendMessage("§c" + fieldName + " must contain only letters, numbers, and underscores.");
            return false;
        }
        return true;
    }
    
    /**
     * Validate that a string represents a valid integer.
     * 
     * @param input The input string
     * @return true if validation passes
     */
    public static boolean isInteger(String input) {
        return input != null && NUMBER_PATTERN.matcher(input).matches();
    }
    
    /**
     * Parse and validate an integer argument.
     * 
     * @param sender The command sender
     * @param input The input string
     * @param fieldName Name of the field being validated (for error message)
     * @return The parsed integer, or null if invalid
     */
    public static Integer validateInteger(CommandSender sender, String input, String fieldName) {
        if (!isInteger(input)) {
            sender.sendMessage("§c" + fieldName + " must be a valid integer.");
            return null;
        }
        
        try {
            return Integer.parseInt(input);
        } catch (NumberFormatException e) {
            sender.sendMessage("§c" + fieldName + " must be a valid integer.");
            return null;
        }
    }
    
    /**
     * Parse and validate an integer argument within a specific range.
     * 
     * @param sender The command sender
     * @param input The input string
     * @param fieldName Name of the field being validated (for error message)
     * @param min Minimum allowed value (inclusive)
     * @param max Maximum allowed value (inclusive)
     * @return The parsed integer, or null if invalid
     */
    public static Integer validateIntegerRange(CommandSender sender, String input, String fieldName, int min, int max) {
        Integer value = validateInteger(sender, input, fieldName);
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
     * Validate that a string represents a valid decimal number.
     * 
     * @param input The input string
     * @return true if validation passes
     */
    public static boolean isDecimal(String input) {
        return input != null && DECIMAL_PATTERN.matcher(input).matches();
    }
    
    /**
     * Parse and validate a double argument.
     * 
     * @param sender The command sender
     * @param input The input string
     * @param fieldName Name of the field being validated (for error message)
     * @return The parsed double, or null if invalid
     */
    public static Double validateDouble(CommandSender sender, String input, String fieldName) {
        if (!isDecimal(input)) {
            sender.sendMessage("§c" + fieldName + " must be a valid number.");
            return null;
        }
        
        try {
            return Double.parseDouble(input);
        } catch (NumberFormatException e) {
            sender.sendMessage("§c" + fieldName + " must be a valid number.");
            return null;
        }
    }
    
    /**
     * Parse and validate a double argument within a specific range.
     * 
     * @param sender The command sender
     * @param input The input string
     * @param fieldName Name of the field being validated (for error message)
     * @param min Minimum allowed value (inclusive)
     * @param max Maximum allowed value (inclusive)
     * @return The parsed double, or null if invalid
     */
    public static Double validateDoubleRange(CommandSender sender, String input, String fieldName, double min, double max) {
        Double value = validateDouble(sender, input, fieldName);
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
     * Validate that a string represents a valid boolean value.
     * 
     * @param input The input string
     * @return true if validation passes
     */
    public static boolean isBoolean(String input) {
        return input != null && BOOLEAN_PATTERN.matcher(input).matches();
    }
    
    /**
     * Parse and validate a boolean argument.
     * Accepts: true/false, yes/no, on/off, 1/0
     * 
     * @param sender The command sender
     * @param input The input string
     * @param fieldName Name of the field being validated (for error message)
     * @return The parsed boolean, or null if invalid
     */
    public static Boolean validateBoolean(CommandSender sender, String input, String fieldName) {
        if (!isBoolean(input)) {
            sender.sendMessage("§c" + fieldName + " must be true/false, yes/no, on/off, or 1/0.");
            return null;
        }
        
        String lower = input.toLowerCase();
        return lower.equals("true") || lower.equals("yes") || lower.equals("on") || lower.equals("1");
    }
    
    /**
     * Validate that a string is not null or empty.
     * 
     * @param input The input string
     * @return true if validation passes
     */
    public static boolean isNotEmpty(String input) {
        return input != null && !input.trim().isEmpty();
    }
    
    /**
     * Validate that a string is not null or empty.
     * 
     * @param sender The command sender
     * @param input The input string
     * @param fieldName Name of the field being validated (for error message)
     * @return true if validation passes
     */
    public static boolean validateNotEmpty(CommandSender sender, String input, String fieldName) {
        if (!isNotEmpty(input)) {
            sender.sendMessage("§c" + fieldName + " cannot be empty.");
            return false;
        }
        return true;
    }
    
    /**
     * Validate that a string meets length requirements.
     * 
     * @param sender The command sender
     * @param input The input string
     * @param fieldName Name of the field being validated (for error message)
     * @param minLength Minimum length required
     * @param maxLength Maximum length allowed
     * @return true if validation passes
     */
    public static boolean validateLength(CommandSender sender, String input, String fieldName, int minLength, int maxLength) {
        if (input == null) {
            sender.sendMessage("§c" + fieldName + " cannot be null.");
            return false;
        }
        
        int length = input.length();
        if (length < minLength || length > maxLength) {
            sender.sendMessage("§c" + fieldName + " must be between " + minLength + " and " + maxLength + " characters long.");
            return false;
        }
        
        return true;
    }
    
    /**
     * Validate that the sender has a specific permission.
     * 
     * @param sender The command sender
     * @param permission The permission to check
     * @return true if the sender has permission
     */
    public static boolean validatePermission(CommandSender sender, String permission) {
        return permission == null || permission.isEmpty() || sender.hasPermission(permission);
    }
    
    /**
     * Validate that the sender has a specific permission and send error message if not.
     * 
     * @param sender The command sender
     * @param permission The permission to check
     * @param errorMessage Custom error message, or null for default
     * @return true if the sender has permission
     */
    public static boolean validatePermission(CommandSender sender, String permission, String errorMessage) {
        if (!validatePermission(sender, permission)) {
            String message = errorMessage != null ? errorMessage : "You don't have permission to do that.";
            sender.sendMessage("§c" + message);
            return false;
        }
        return true;
    }
}
