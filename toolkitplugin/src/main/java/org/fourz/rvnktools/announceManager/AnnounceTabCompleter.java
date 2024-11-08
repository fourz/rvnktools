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
    private final List<String> subcommands = Arrays.asList("toggle", "status", "list", "add", "remove", "now");

    public AnnounceTabCompleter(AnnounceManager announceManager) {
        this.announceManager = announceManager;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        
        if (args.length == 1) {
            return filterCompletions(subcommands, args[0]);
        }

        if (args.length == 2) {
            switch (args[0].toLowerCase()) {
                case "toggle":
                    return filterCompletions(new ArrayList<>(announceManager.getAnnounceTypes()), args[1]);
                case "remove":
                case "now":
                    return filterCompletions(new ArrayList<>(announceManager.getAnnouncementIds()), args[1]);
                case "list":
                    List<String> listOptions = new ArrayList<>();
                    listOptions.add("all");
                    listOptions.addAll(announceManager.getAnnounceTypes());
                    return filterCompletions(listOptions, args[1]);
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
