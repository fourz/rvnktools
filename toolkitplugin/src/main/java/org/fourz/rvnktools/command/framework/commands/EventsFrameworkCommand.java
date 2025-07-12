package org.fourz.rvnktools.command.framework.commands;

import org.bukkit.command.CommandSender;
import org.fourz.rvnktools.RVNKTools;
import org.fourz.rvnktools.command.framework.BaseCommand;
import java.util.Collections;
import java.util.List;

public class EventsFrameworkCommand extends BaseCommand {
    
    public EventsFrameworkCommand(RVNKTools plugin) {
        super(plugin, "events", 
              "Provides information about scheduled events", 
              "/events", 
              null);  // No special permission required
    }

    @Override
    protected boolean executeCommand(CommandSender sender, String[] args) {
        // TODO: Implement events command logic here
        sender.sendMessage("§aNo scheduled events at this time.");
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        return Collections.emptyList();  // No tab completion for now
    }
}
