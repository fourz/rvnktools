package org.fourz.rvnktools.command.manager.commands;

import org.bukkit.command.CommandSender;
import org.fourz.rvnktools.RVNKTools;
import org.fourz.rvnktools.command.manager.BaseCommand;
import org.fourz.rvnktools.command.manager.BaseSubCommand;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Subcommand for managing link configurations.
 * Provides functionality to reload link settings and configurations.
 */
public class LinksSubCommand extends BaseSubCommand {
    
    public LinksSubCommand(RVNKTools plugin, BaseCommand parent) {
        super(plugin, parent, "links", 
              "Manage links configuration", 
              "/rvnktools links <reload>",
              "rvnktools.links", false);
    }
    
    @Override
    protected boolean executeSubCommand(CommandSender sender, String[] args) {
        if (args.length == 0 || !args[0].equalsIgnoreCase("reload")) {
            sender.sendMessage("§c▶ Usage: " + getUsage());
            return true;
        }
        
        if (!sender.hasPermission("rvnktools.links.reload")) {
            sender.sendMessage("§c✖ You don't have permission to reload links configuration.");
            return true;
        }
        
        try {
            plugin.linkMaker.reloadConfig();
            sender.sendMessage("§a✓ Links configuration reloaded successfully!");
            logger.info("Links configuration reloaded by " + sender.getName());
        } catch (Exception e) {
            sender.sendMessage("§c✖ Failed to reload links configuration. Check console for errors.");
            logger.error("Failed to reload links configuration", e);
        }
        
        return true;
    }
    
    @Override
    protected List<String> getTabCompletions(CommandSender sender, String[] args) {
        if (args.length == 1 && sender.hasPermission("rvnktools.links.reload")) {
            return Arrays.asList("reload");
        }
        return Collections.emptyList();
    }
}
