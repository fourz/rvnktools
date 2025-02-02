package org.fourz.rvnktools.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.fourz.rvnktools.RVNKTools;

import java.util.HashMap;
import java.util.Map;

public class RVNKToolsCommand implements CommandExecutor {
    private final RVNKTools plugin;
    private final Map<String, RVNKToolsSubCommand> subcommands = new HashMap<>();

    public RVNKToolsCommand(RVNKTools plugin) {
        this.plugin = plugin;
        registerSubCommands();
    }

    private void registerSubCommands() {
        subcommands.put("links", new RVNKToolsSubCommandLinks(plugin));
        subcommands.put("cycle", new RVNKToolsSubCommandCycle(plugin));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            showHelp(sender);
            return true;
        }

        RVNKToolsSubCommand subCommand = subcommands.get(args[0].toLowerCase());
        if (subCommand == null) {
            showHelp(sender);
            return true;
        }

        String[] subCommandArgs = new String[args.length - 1];
        System.arraycopy(args, 1, subCommandArgs, 0, args.length - 1);
        
        return subCommand.execute(sender, subCommandArgs);
    }

    private void showHelp(CommandSender sender) {
        sender.sendMessage("§6=== RVNKTools Commands ===");
        sender.sendMessage("§f/rvnktools links reload §7- Reload links configuration");
        sender.sendMessage("§f/rvnktools cycle reload §7- Reload cycle commands configuration");
        sender.sendMessage("§f/rvnktools cycle help §7- Display cycle commands help");
    }
}
