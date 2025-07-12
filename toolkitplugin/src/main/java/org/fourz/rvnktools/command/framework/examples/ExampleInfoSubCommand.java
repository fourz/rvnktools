package org.fourz.rvnktools.command.framework.examples;

import org.bukkit.command.CommandSender;
import org.fourz.rvnktools.RVNKTools;
import org.fourz.rvnktools.command.framework.BaseSubCommand;
import org.fourz.rvnktools.command.framework.CommandManager;
import org.fourz.rvnktools.command.framework.RVNKCommand;

import java.util.Collections;
import java.util.List;

/**
 * Example subcommand for displaying framework information.
 */
public class ExampleInfoSubCommand extends BaseSubCommand {
    
    public ExampleInfoSubCommand(RVNKTools plugin, RVNKCommand parent) {
        super(plugin, parent, "info", 
              "Show command framework information", 
              "/example info");
    }
    
    @Override
    protected boolean executeSubCommand(CommandSender sender, String[] args) {
        sendInfoMessage(sender, "=== Command Framework Information ===");
        sendMessage(sender, "&7Framework Version: &a1.0");
        sendMessage(sender, "&7Parent Command: &e" + getParent().getName());
        sendMessage(sender, "&7Permission: &e" + (getPermission() != null ? getPermission() : "None"));
        
        CommandManager commandManager = CommandManager.getInstance();
        if (commandManager != null) {
            sendMessage(sender, "&7Registered Commands: &a" + commandManager.getCommandCount());
        }
        
        sendMessage(sender, "&7Logging: &a" + (logger.isDebugEnabled() ? "Debug Enabled" : "Normal"));
        
        logger.debug("Info subcommand executed by " + sender.getName());
        return true;
    }
    
    @Override
    protected List<String> getTabCompletions(CommandSender sender, String[] args) {
        // No tab completions for info command
        return Collections.emptyList();
    }
}
