package org.fourz.rvnktools.announceManager;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import java.util.ArrayList;
import java.util.List;
import java.util.Arrays;
import java.util.stream.Collectors;

public class AnnounceTabCompleter implements TabCompleter {
    private final AnnounceManager announceManager;
    private final List<String> subcommands = Arrays.asList("toggle", "status", "list", "add", "delete", "now", "help", "types", "set");
    private final List<String> setProperties = Arrays.asList("recurrence", "date", "type", "permission");

    public AnnounceTabCompleter(AnnounceManager announceManager) {
        this.announceManager = announceManager;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        
        if (args.length == 1) {
            List<String> availableCommands = new ArrayList<>();
            for (String cmd : subcommands) {
                String permission = switch (cmd) {
                    case "add" -> "rvnktools.command.announce.add";
                    case "delete" -> "rvnktools.command.announce.delete";
                    case "now" -> "rvnktools.command.announce.now";
                    case "set" -> "rvnktools.command.announce.set";
                    default -> "rvnktools.command.announce";
                };
                if (sender.hasPermission(permission)) {
                    availableCommands.add(cmd);
                }
            }
            return filterCompletions(availableCommands, args[0]);
        }

        if (args.length == 2) {
            String permission = switch (args[0].toLowerCase()) {
                case "add" -> "rvnktools.command.announce.add";
                case "delete" -> "rvnktools.command.announce.delete";
                case "now" -> "rvnktools.command.announce.now";
                case "set" -> "rvnktools.command.announce.set";
                default -> "rvnktools.command.announce";
            };
            
            if (!sender.hasPermission(permission)) {
                return completions;
            }

            switch (args[0].toLowerCase()) {
                case "help":
                    List<String> availableHelpTopics = new ArrayList<>();
                    for (String cmd : subcommands) {
                        permission = switch (cmd) {
                            case "add" -> "rvnktools.command.announce.add";
                            case "delete" -> "rvnktools.command.announce.delete";
                            case "now" -> "rvnktools.command.announce.now";
                            default -> "rvnktools.command.announce";
                        };
                        if (sender.hasPermission(permission)) {
                            availableHelpTopics.add(cmd);
                        }
                    }
                    return filterCompletions(availableHelpTopics, args[1]);
                case "toggle":
                    return filterCompletions(new ArrayList<>(announceManager.getAnnounceTypes()), args[1]);
                case "delete":
                case "now":
                    return filterCompletions(new ArrayList<>(announceManager.getAnnouncementIds()), args[1]);
                case "list":
                    List<String> listOptions = new ArrayList<>();
                    
                    // add types option
                    listOptions.add("types");

                    // add all types if the player has permission
                    if (sender.hasPermission("rvnktools.announce.type.*")) {
                        listOptions.add("all");
                    }

                    // add all types that the player has permission to
                    for (String type : announceManager.getAnnounceTypes()) {
                        if (sender.hasPermission("rvnktools.announce.type." + type.toLowerCase())) {
                            listOptions.add(type);
                        }
                    }
                    return filterCompletions(listOptions, args[1]);
                case "add":                    
                    return filterCompletions(new ArrayList<>(announceManager.getAnnounceTypes()), args[1]);
                case "set":
                    return filterCompletions(new ArrayList<>(announceManager.getAnnouncementIds()), args[1]);
            }
        }

        if (args.length == 3 && args[0].toLowerCase().equals("set")) {
            if (!sender.hasPermission("rvnktools.command.announce.set")) {
                return completions;
            }
            return filterCompletions(setProperties, args[2]);
        }

        if (args.length == 4 && args[0].toLowerCase().equals("set")) {
            if (!sender.hasPermission("rvnktools.command.announce.set")) {
                return completions;
            }
            
            switch (args[2].toLowerCase()) {
                case "recurrence":
                    return filterCompletions(Arrays.asList("none", "daily", "60m", "90m", "120m"), args[3]);
                case "type":
                    return filterCompletions(new ArrayList<>(announceManager.getAnnounceTypes()), args[3]);
                case "permission":
                    return filterCompletions(Arrays.asList("none"), args[3]);
                case "date":
                    // No specific completions for date as it requires a specific format
                    break;
            }
        }
        
        return completions;
    }

    private List<String> filterCompletions(List<String> options, String partial) {
        return options.stream()
            .filter(opt -> opt.toLowerCase().startsWith(partial.toLowerCase()))
            .collect(Collectors.toList());
    }
}
