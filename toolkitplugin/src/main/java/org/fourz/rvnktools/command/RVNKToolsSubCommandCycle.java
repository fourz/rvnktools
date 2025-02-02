package org.fourz.rvnktools.command;

import org.bukkit.command.CommandSender;
import org.fourz.rvnktools.RVNKTools;

public class RVNKToolsSubCommandCycle extends RVNKToolsSubCommand {
    
    public RVNKToolsSubCommandCycle(RVNKTools plugin) {
        super(plugin);
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!checkPermission(sender, "rvnktools.cycle")) {
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            showHelp(sender);
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            if (!checkPermission(sender, "rvnktools.cycle.reload")) {
                return true;
            }
            
            plugin.getCycleCommands().loadConfig();
            messageSender(sender, "&aCycle commands configuration reloaded successfully!");
            return true;
        }

        showHelp(sender);
        return true;
    }

    private void showHelp(CommandSender sender) {
        messageSender(sender, "&6=== Cycle Commands Help ===");
        messageSender(sender, "&f/rvnktools cycle reload &7- Reload cycle commands configuration");
        messageSender(sender, "&f/rvnktools cycle help &7- Display this help message");
    }
}
