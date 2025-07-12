package org.fourz.rvnktools.command.framework.examples;

import org.bukkit.command.CommandSender;
import org.fourz.rvnktools.RVNKTools;
import org.fourz.rvnktools.command.framework.BaseCommand;
import org.fourz.rvnktools.command.framework.TabCompletionUtil;

import java.util.List;

/**
 * Example command demonstrating the new command framework.
 * This will serve as a template for migrating other commands.
 */
public class ExampleCommand extends BaseCommand {
    
    public ExampleCommand(RVNKTools plugin) {
        super(plugin, "example", 
              "Example command demonstrating the new framework", 
              "/example <help|test|info>");
        
        // Register subcommands
        registerSubCommand("test", new ExampleTestSubCommand(plugin, this));
        registerSubCommand("info", new ExampleInfoSubCommand(plugin, this));
    }
    
    @Override
    protected boolean executeCommand(CommandSender sender, String[] args) {
        // This is called when no valid subcommand is found
        sendHelp(sender);
        return true;
    }
    
    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            // Use the utility to get common action completions plus our custom ones
            List<String> completions = TabCompletionUtil.getCommonActionCompletions(args[0]);
            completions.addAll(TabCompletionUtil.filterCompletions(
                new String[]{"test", "info"}, args[0]
            ));
            return completions;
        }
        
        // Delegate to parent for subcommand tab completion
        return super.tabComplete(sender, args);
    }
}
