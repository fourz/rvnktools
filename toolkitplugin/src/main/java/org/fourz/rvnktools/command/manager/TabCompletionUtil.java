package org.fourz.rvnktools.command.manager;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Utility class for common tab completion patterns.
 * Provides standardized tab completion for common command arguments.
 */
public class TabCompletionUtil {
    
    // Common boolean values for tab completion
    private static final List<String> BOOLEAN_VALUES = Arrays.asList("true", "false", "yes", "no", "on", "off");
    
    // Common time units for tab completion
    private static final List<String> TIME_UNITS = Arrays.asList("1s", "30s", "1m", "5m", "10m", "30m", "1h", "2h", "12h", "1d");
    
    /**
     * Get basic boolean completions.
     * 
     * @param prefix The current argument being typed
     * @return List of matching boolean values
     */
    public static List<String> getBooleanCompletions(String prefix) {
        return filterCompletions(BOOLEAN_VALUES, prefix);
    }
    
    /**
     * Get time unit completions.
     * 
     * @param prefix The current argument being typed
     * @return List of matching time units
     */
    public static List<String> getTimeUnitCompletions(String prefix) {
        return filterCompletions(TIME_UNITS, prefix);
    }
    
    /**
     * Get online player name completions.
     * 
     * @param prefix The current argument being typed
     * @return List of matching player names
     */
    public static List<String> getPlayerCompletions(String prefix) {
        return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(name -> startsWithIgnoreCase(name, prefix))
                .sorted()
                .collect(Collectors.toList());
    }
    
    /**
     * Get world name completions.
     * 
     * @param prefix The current argument being typed
     * @return List of matching world names
     */
    public static List<String> getWorldCompletions(String prefix) {
        return Bukkit.getWorlds().stream()
                .map(World::getName)
                .filter(name -> startsWithIgnoreCase(name, prefix))
                .sorted()
                .collect(Collectors.toList());
    }
    
    /**
     * Get number completions within a range.
     * 
     * @param prefix The current argument being typed
     * @param min Minimum value
     * @param max Maximum value
     * @param step Step size between values
     * @return List of matching numbers as strings
     */
    public static List<String> getNumberCompletions(String prefix, int min, int max, int step) {
        List<String> completions = new ArrayList<>();
        
        for (int i = min; i <= max; i += step) {
            String value = String.valueOf(i);
            if (value.startsWith(prefix)) {
                completions.add(value);
            }
        }
        
        return completions;
    }
    
    /**
     * Get enum value completions.
     * 
     * @param enumClass The enum class
     * @param prefix The current argument being typed
     * @param <T> The enum type
     * @return List of matching enum values
     */
    public static <T extends Enum<T>> List<String> getEnumCompletions(Class<T> enumClass, String prefix) {
        return Arrays.stream(enumClass.getEnumConstants())
                .map(Enum::name)
                .map(String::toLowerCase)
                .filter(name -> startsWithIgnoreCase(name, prefix))
                .sorted()
                .collect(Collectors.toList());
    }
    
    /**
     * Get subcommand completions for a command.
     * 
     * @param command The parent command
     * @param sender The command sender
     * @param prefix The current argument being typed
     * @return List of matching subcommand names
     */
    public static List<String> getSubCommandCompletions(RVNKCommand command, CommandSender sender, String prefix) {
        List<String> completions = new ArrayList<>();
        
        if (command instanceof BaseCommand) {
            BaseCommand baseCommand = (BaseCommand) command;
            completions.addAll(baseCommand.getMatchingSubCommands(sender, prefix));
        }
        
        return completions;
    }
    
    /**
     * Create completions for a help command pattern.
     * Returns "help" if it matches the prefix.
     * 
     * @param prefix The current argument being typed
     * @return List containing "help" if it matches
     */
    public static List<String> getHelpCompletion(String prefix) {
        List<String> help = Arrays.asList("help");
        return filterCompletions(help, prefix);
    }
    
    /**
     * Create completions for common command actions.
     * 
     * @param prefix The current argument being typed
     * @return List of matching action names
     */
    public static List<String> getCommonActionCompletions(String prefix) {
        List<String> actions = Arrays.asList(
            "add", "remove", "delete", "list", "show", "get", "set", 
            "enable", "disable", "toggle", "reload", "help", "info", "status"
        );
        return filterCompletions(actions, prefix);
    }
    
    /**
     * Create completions for announcement types.
     * 
     * @param prefix The current argument being typed
     * @return List of matching announcement types
     */
    public static List<String> getAnnouncementTypeCompletions(String prefix) {
        List<String> types = Arrays.asList(
            "broadcast", "motd", "join", "scheduled", "welcome", "tips"
        );
        return filterCompletions(types, prefix);
    }
    
    /**
     * Create completions for common permission levels.
     * 
     * @param prefix The current argument being typed
     * @return List of matching permission levels
     */
    public static List<String> getPermissionLevelCompletions(String prefix) {
        List<String> levels = Arrays.asList(
            "default", "member", "trusted", "moderator", "admin", "owner"
        );
        return filterCompletions(levels, prefix);
    }
    
    /**
     * Filter a list of completions based on a prefix.
     * 
     * @param completions The list of possible completions
     * @param prefix The prefix to filter by
     * @return List of matching completions
     */
    public static List<String> filterCompletions(List<String> completions, String prefix) {
        if (prefix == null || prefix.isEmpty()) {
            return new ArrayList<>(completions);
        }
        
        return completions.stream()
                .filter(completion -> startsWithIgnoreCase(completion, prefix))
                .sorted()
                .collect(Collectors.toList());
    }
    
    /**
     * Filter an array of completions based on a prefix.
     * 
     * @param completions The array of possible completions
     * @param prefix The prefix to filter by
     * @return List of matching completions
     */
    public static List<String> filterCompletions(String[] completions, String prefix) {
        return filterCompletions(Arrays.asList(completions), prefix);
    }
    
    /**
     * Combine multiple completion lists into one.
     * 
     * @param completionLists The lists to combine
     * @return Combined list with duplicates removed and sorted
     */
    @SafeVarargs
    public static List<String> combineCompletions(List<String>... completionLists) {
        Set<String> combined = new LinkedHashSet<>();
        
        for (List<String> list : completionLists) {
            combined.addAll(list);
        }
        
        return new ArrayList<>(combined);
    }
    
    /**
     * Create conditional completions based on argument position and previous arguments.
     * 
     * @param args The current arguments
     * @param argIndex The index of the argument being completed
     * @param conditionalCompletions Map of conditions to completion lists
     * @param defaultCompletions Default completions if no condition matches
     * @return List of appropriate completions
     */
    public static List<String> getConditionalCompletions(String[] args, int argIndex, 
                                                        Map<String, List<String>> conditionalCompletions,
                                                        List<String> defaultCompletions) {
        // Check previous arguments for conditions
        for (int i = 0; i < argIndex && i < args.length; i++) {
            String arg = args[i].toLowerCase();
            if (conditionalCompletions.containsKey(arg)) {
                String prefix = argIndex < args.length ? args[argIndex] : "";
                return filterCompletions(conditionalCompletions.get(arg), prefix);
            }
        }
        
        String prefix = argIndex < args.length ? args[argIndex] : "";
        return filterCompletions(defaultCompletions, prefix);
    }
    
    /**
     * Create position-based completions where different argument positions have different options.
     * 
     * @param argIndex The index of the argument being completed
     * @param prefix The current argument being typed
     * @param positionCompletions Map of position indices to completion lists
     * @return List of appropriate completions
     */
    public static List<String> getPositionalCompletions(int argIndex, String prefix,
                                                       Map<Integer, List<String>> positionCompletions) {
        List<String> completions = positionCompletions.get(argIndex);
        if (completions == null) {
            return Collections.emptyList();
        }
        
        return filterCompletions(completions, prefix);
    }
    
    /**
     * Limit the number of completions returned.
     * Useful for preventing overwhelming tab completion lists.
     * 
     * @param completions The list of completions
     * @param maxResults Maximum number of results to return
     * @return Limited list of completions
     */
    public static List<String> limitCompletions(List<String> completions, int maxResults) {
        if (completions.size() <= maxResults) {
            return completions;
        }
        
        return completions.subList(0, maxResults);
    }
    
    /**
     * Check if a string starts with a prefix, ignoring case.
     * 
     * @param str The string to check
     * @param prefix The prefix to check for
     * @return true if the string starts with the prefix (case-insensitive)
     */
    private static boolean startsWithIgnoreCase(String str, String prefix) {
        if (prefix == null || prefix.isEmpty()) {
            return true;
        }
        return str.toLowerCase().startsWith(prefix.toLowerCase());
    }
    
    /**
     * Create a builder for complex tab completion scenarios.
     * 
     * @return A new TabCompletionBuilder
     */
    public static TabCompletionBuilder builder() {
        return new TabCompletionBuilder();
    }
    
    /**
     * Builder class for creating complex tab completion logic.
     */
    public static class TabCompletionBuilder {
        private final Map<Integer, List<String>> positionCompletions = new HashMap<>();
        private final Map<String, List<String>> conditionalCompletions = new HashMap<>();
        private List<String> defaultCompletions = Collections.emptyList();
        private int maxResults = Integer.MAX_VALUE;
        
        /**
         * Set completions for a specific argument position.
         * 
         * @param position The argument position (0-based)
         * @param completions The completions for that position
         * @return This builder
         */
        public TabCompletionBuilder atPosition(int position, List<String> completions) {
            positionCompletions.put(position, completions);
            return this;
        }
        
        /**
         * Set completions for a specific argument position.
         * 
         * @param position The argument position (0-based)
         * @param completions The completions for that position
         * @return This builder
         */
        public TabCompletionBuilder atPosition(int position, String... completions) {
            return atPosition(position, Arrays.asList(completions));
        }
        
        /**
         * Set conditional completions based on previous arguments.
         * 
         * @param condition The condition (previous argument value)
         * @param completions The completions to use if condition is met
         * @return This builder
         */
        public TabCompletionBuilder whenPrevious(String condition, List<String> completions) {
            conditionalCompletions.put(condition.toLowerCase(), completions);
            return this;
        }
        
        /**
         * Set conditional completions based on previous arguments.
         * 
         * @param condition The condition (previous argument value)
         * @param completions The completions to use if condition is met
         * @return This builder
         */
        public TabCompletionBuilder whenPrevious(String condition, String... completions) {
            return whenPrevious(condition, Arrays.asList(completions));
        }
        
        /**
         * Set default completions to use when no other conditions match.
         * 
         * @param completions The default completions
         * @return This builder
         */
        public TabCompletionBuilder defaultTo(List<String> completions) {
            this.defaultCompletions = completions;
            return this;
        }
        
        /**
         * Set default completions to use when no other conditions match.
         * 
         * @param completions The default completions
         * @return This builder
         */
        public TabCompletionBuilder defaultTo(String... completions) {
            return defaultTo(Arrays.asList(completions));
        }
        
        /**
         * Limit the maximum number of results returned.
         * 
         * @param maxResults Maximum number of results
         * @return This builder
         */
        public TabCompletionBuilder limitTo(int maxResults) {
            this.maxResults = maxResults;
            return this;
        }
        
        /**
         * Build the completions for the given arguments.
         * 
         * @param args The current arguments
         * @param argIndex The index of the argument being completed
         * @return List of appropriate completions
         */
        public List<String> build(String[] args, int argIndex) {
            String prefix = argIndex < args.length ? args[argIndex] : "";
            
            // Check positional completions first
            if (positionCompletions.containsKey(argIndex)) {
                List<String> completions = filterCompletions(positionCompletions.get(argIndex), prefix);
                return limitCompletions(completions, maxResults);
            }
            
            // Check conditional completions
            List<String> conditionalResult = getConditionalCompletions(args, argIndex, conditionalCompletions, defaultCompletions);
            return limitCompletions(conditionalResult, maxResults);
        }
    }
}
