package org.fourz.rvnktools.command.manager.commands;

import org.bukkit.command.CommandSender;
import org.fourz.rvnktools.RVNKTools;
import org.fourz.rvnktools.command.manager.BaseCommand;
import org.fourz.rvnktools.command.manager.BaseSubCommand;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Subcommand for managing cycle commands configuration.
 * Provides functionality to reload cycle command settings and display help.
 */
public class CycleSubCommand extends BaseSubCommand {
    
    public CycleSubCommand(RVNKTools plugin, BaseCommand parent) {
        super(plugin, parent, "cycle", 
              "Manage cycle commands configuration", 
              "/rvnktools cycle <reload|help>",
              "rvnktools.cycle", false);
    }
    
    @Override
    protected boolean executeSubCommand(CommandSender sender, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            showHelp(sender);
            return true;
        }
        
        if (args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("rvnktools.cycle.reload")) {
                sender.sendMessage("§c✖ You don't have permission to reload cycle commands configuration.");
                return true;
            }
            
            try {
                plugin.getCycleCommands().loadConfig();
                sender.sendMessage("§a✓ Cycle commands configuration reloaded successfully!");
                logger.info("Cycle commands configuration reloaded by " + sender.getName());
            } catch (Exception e) {
                sender.sendMessage("§c✖ Failed to reload cycle commands configuration. Check console for errors.");
                logger.error("Failed to reload cycle commands configuration", e);
            }
            return true;
        }
        
        showHelp(sender);
        return true;
    }
    
    private void showHelp(CommandSender sender) {
        sender.sendMessage("§c▶ §6Cycle Commands Help");
        sender.sendMessage("§f/rvnktools cycle reload §7- Reload cycle commands configuration");
        sender.sendMessage("§f/rvnktools cycle help §7- Display this help message");
        sender.sendMessage("§7   Cycle commands allow for automated server tasks and player instructions");
    }
    
    @Override
    protected List<String> getTabCompletions(CommandSender sender, String[] args) {
        if (args.length == 1) {
            List<String> completions = Arrays.asList("help");
            if (sender.hasPermission("rvnktools.cycle.reload")) {
                return Arrays.asList("reload", "help");
            }
            return completions;
        }
        return Collections.emptyList();
    }
}
