package org.fourz.rvnktools.command;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.LinkedList;
import java.util.ArrayList;
import java.util.AbstractMap;
import java.util.HashMap;

/**
 * CycleCommand Class
 * 
 * This class implements a cycle-based command handler for multiple subcommands.
 * Each subcommand can have its own set of instruction sets that are cycled through
 * each time the command is executed by a player.
 * 
 * Supported actions include:
 * - "send_message_to_player": Sends a message to the player.
 * - "send_message_to_all": Sends a message to all players on the server.
 * - "run_command_as_server": Executes a command as the server.
 * - "run_command_as_self": Executes a command as the player issuing the command.
 * 
 * Example Usage:
 * 
 * // In your main plugin class (e.g., RVNKTools.java)
 * public class RVNKTools extends JavaPlugin {
 *     private CycleCommand cycleCommand;
 * 
 *     @Override
 *     public void onEnable() {
 *         // Load configuration
 *         saveDefaultConfig();
 *         FileConfiguration config = getConfig();
 * 
 *         // Initialize CycleCommand
 *         cycleCommand = new CycleCommand(loadAllInstructionSets(config));
 * 
 *         // Register command executor
 *         this.getCommand("commandcycle").setExecutor(cycleCommand);
 *     }
 * }
 */
    // Start of Selection
    public class CycleCommand implements CommandExecutor {
    
        // Map to hold instruction queues for each subcommand
        private final Map<String, Queue<List<Map.Entry<String, String>>>> subcommandInstructions;
    
        /**
         * Constructor
         * 
         * Initializes the CycleCommand with instruction sets for each subcommand.
         *
         * @param config The plugin's configuration containing command instructions.
         */
        public CycleCommand(FileConfiguration config) {
            // Initialize the subcommandInstructions map
            subcommandInstructions = new HashMap<>();
            
            // Retrieve the 'commandcycle' section from the config
            ConfigurationSection commandCycleSection = config.getConfigurationSection("commands.commandcycle");
            if (commandCycleSection != null) {
                // Iterate over each subcommand defined under 'commandcycle'
                for (String subcommand : commandCycleSection.getKeys(false)) {
                    // Get the configuration section for the current subcommand
                    ConfigurationSection subcommandSection = commandCycleSection.getConfigurationSection(subcommand);
                    if (subcommandSection != null) {
                        // List to hold all instruction sets for the current subcommand
                        List<List<Map.Entry<String, String>>> instructionSets = new ArrayList<>();
                        
                        // Retrieve the 'sets' section containing instruction sets
                        ConfigurationSection setsSection = subcommandSection.getConfigurationSection("sets");
                        if (setsSection != null) {
                            // Iterate over each set within the 'sets' section
                            for (String setKey : setsSection.getKeys(false)) {
                                // List to hold individual action-detail pairs within a set
                                List<Map.Entry<String, String>> instructionSet = new ArrayList<>();
                                
                                // Get the list of actions for the current set
                                List<Map<?, ?>> actions = setsSection.getMapList(setKey);
                                
                                // Process each action in the current set
                                for (Map<?, ?> actionMap : actions) {
                                    String action = (String) actionMap.get("action");
                                    String detail = (String) actionMap.get("detail");
                                    
                                    // If both action and detail are present, add them to the instruction set
                                    if (action != null && detail != null) {
                                        instructionSet.add(new AbstractMap.SimpleEntry<>(action, detail));
                                    }
                                }
                                
                                // If the instruction set is not empty, add it to the instructionSets list
                                if (!instructionSet.isEmpty()) {
                                    instructionSets.add(instructionSet);
                                }
                            }
                        }
                        
                        // If there are any instruction sets, add them to the subcommandInstructions map
                        if (!instructionSets.isEmpty()) {
                            subcommandInstructions.put(subcommand, new LinkedList<>(instructionSets));
                        }
                    }
                }
            }
        }

    /**
     * Loads instruction sets for all subcommands from the plugin.yml configuration file.
     *
     * @param config The plugin.yml FileConfiguration object.
     * @return A map of subcommand names to their respective instruction sets.
     */
    public static Map<String, List<List<Map.Entry<String, String>>>> loadAllInstructionSets(FileConfiguration config) {
        Map<String, List<List<Map.Entry<String, String>>>> allInstructions = new HashMap<>();
        
        // Get the 'commandcycle' section from the config
        ConfigurationSection commandCycleSection = config.getConfigurationSection("commands.commandcycle");
    
        if (commandCycleSection != null) {
            // Iterate over each subcommand inside 'commandcycle'
            for (String subcommand : commandCycleSection.getKeys(false)) {
                ConfigurationSection subcommandSection = commandCycleSection.getConfigurationSection(subcommand);
                if (subcommandSection != null) {
                    List<List<Map.Entry<String, String>>> instructionSets = new ArrayList<>();
    
                    // Get the 'sets' section for each subcommand
                    ConfigurationSection setsSection = subcommandSection.getConfigurationSection("sets");
                    
                    if (setsSection != null) {
                        // Iterate over each set of instructions (e.g., 'creative', 'survival', 'trainmodeon', etc.)
                        for (String setKey : setsSection.getKeys(false)) {
                            List<Map.Entry<String, String>> instructionSet = new ArrayList<>();
                            List<Map<?, ?>> actions = setsSection.getMapList(setKey);
    
                            // Process each action-detail pair inside a set
                            for (Map<?, ?> actionMap : actions) {
                                String action = (String) actionMap.get("action");
                                String detail = (String) actionMap.get("detail");
    
                                // Ensure action and detail are not null
                                if (action != null && detail != null) {
                                    instructionSet.add(new AbstractMap.SimpleEntry<>(action, detail));
                                }
                            }
    
                            // If the set is not empty, add it to the list of instruction sets
                            if (!instructionSet.isEmpty()) {
                                instructionSets.add(instructionSet);
                            }
                        }
                    }
    
                    // Only add subcommand if it has valid instruction sets
                    if (!instructionSets.isEmpty()) {
                        allInstructions.put(subcommand, instructionSets);
                    }
                }
            }
        }
    
        return allInstructions;
    }    

    /**
     * Cycles through the instruction sets for a specific subcommand and executes the next set of instructions.
     *
     * @param player      The player executing the command.
     * @param subcommand  The subcommand to execute.
     */
    public void cycleAndRun(Player player, String subcommand) {
        Queue<List<Map.Entry<String, String>>> instructionQueue = subcommandInstructions.get(subcommand);

        if (instructionQueue == null || instructionQueue.isEmpty()) {
            player.sendMessage("No instruction sets available for this command.");
            return;
        }

        // Retrieve the next set of instructions
        List<Map.Entry<String, String>> instructionSet = instructionQueue.poll();
        instructionQueue.offer(instructionSet);  // Re-add to the end to continue cycling

        // Execute the instructions
        for (Map.Entry<String, String> instruction : instructionSet) {
            String action = instruction.getKey();
            String detail = instruction.getValue();

            switch (action.toLowerCase()) {
                case "send_message_to_player":
                    player.sendMessage(detail);
                    break;
                case "send_message_to_all":
                    Bukkit.broadcastMessage(detail);
                    break;
                case "run_command_as_server":
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), detail.replace("$player", player.getName()));
                    break;
                case "run_command_as_self":
                    player.performCommand(detail);
                    break;
                default:
                    player.sendMessage("Unknown action: " + action);
                    break;
            }
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can use this command.");
            return false;
        }

        Player player = (Player) sender;

        if (args.length == 0) {
            player.sendMessage("Please specify a subcommand.");
            return false;
        }

        String subcommand = args[0].toLowerCase();

        if (!subcommandInstructions.containsKey(subcommand)) {
            player.sendMessage("Unknown subcommand: " + subcommand);
            return false;
        }

        // Check permissions
        String permission = "rvnktools.command." + subcommand;
        if (!player.hasPermission(permission)) {
            player.sendMessage("You do not have permission to use this command.");
            return false;
        }

        // Trigger the cycle and execution of the next instruction set
        cycleAndRun(player, subcommand);

        return true;
    }
}