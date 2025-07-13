package org.fourz.rvnktools.command.manager.examples;

import org.bukkit.command.CommandSender;
import org.fourz.rvnktools.RVNKTools;
import org.fourz.rvnktools.command.manager.BaseSubCommand;
import org.fourz.rvnktools.command.manager.RVNKCommand;

import java.util.Collections;
import java.util.List;

/**
 * Example subcommand for testing the framework.
 */
public class ExampleTestSubCommand extends BaseSubCommand {
    
    public ExampleTestSubCommand(RVNKTools plugin, RVNKCommand parent) {
        super(plugin, parent, "test", 
              "Test the command framework", 
              "/example test [message]");
    }
    
    @Override
    protected boolean executeSubCommand(CommandSender sender, String[] args) {
        if (args.length == 0) {
            sendSuccessMessage(sender, "Command framework test successful!");
            sendInfoMessage(sender, "You can also provide a custom message: /example test <message>");
        } else {
            String message = String.join(" ", args);
            sendSuccessMessage(sender, "Test message: " + message);
        }
        
        logger.info(sender.getName() + " executed test subcommand");
        return true;
    }
    
    @Override
    protected List<String> getTabCompletions(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return List.of("hello", "world", "framework", "test");
        }
        return Collections.emptyList();
    }
}
