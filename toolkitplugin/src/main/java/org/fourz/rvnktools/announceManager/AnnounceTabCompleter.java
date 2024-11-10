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
    private final List<String> subcommands = Arrays.asList("toggle", "status", "list", "add", "remove", "now", "help", "types");

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
                    case "remove" -> "rvnktools.command.announce.remove";
                    case "now" -> "rvnktools.command.announce.now";
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
                case "remove" -> "rvnktools.command.announce.remove";
                case "now" -> "rvnktools.command.announce.now";
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
                            case "remove" -> "rvnktools.command.announce.remove";
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
                case "remove":
                case "now":
                    return filterCompletions(new ArrayList<>(announceManager.getAnnouncementIds()), args[1]);
                case "list":
                    List<String> listOptions = new ArrayList<>();
                    if (sender.hasPermission("rvnktools.announce.type.*")) {
                        listOptions.add("all");
                    }
                    for (String type : announceManager.getAnnounceTypes()) {
                        if (sender.hasPermission("rvnktools.announce.type." + type.toLowerCase())) {
                            listOptions.add(type);
                        }
                    }
                    return filterCompletions(listOptions, args[1]);
                case "add":                    
                    return filterCompletions(new ArrayList<>(announceManager.getAnnounceTypes()), args[1]);
                    
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
