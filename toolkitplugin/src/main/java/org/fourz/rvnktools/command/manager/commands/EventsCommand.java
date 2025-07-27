package org.fourz.rvnktools.command.manager.commands;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.fourz.rvnktools.RVNKTools;
import org.fourz.rvnktools.command.manager.BaseCommand;

import java.util.Collections;
import java.util.List;

public class EventsCommand extends BaseCommand {
    
    public EventsCommand(RVNKTools plugin) {
        super(plugin, "events", 
              "Provides information about scheduled events", 
              "/events", 
              null);  // No special permission required
    }

    @Override
    protected boolean executeCommand(CommandSender sender, String[] args) {
        if (sender instanceof Player) {
            Player player = (Player) sender;
            player.sendMessage("§a ---- §cEvent Info §fPage I §a----");
            player.sendMessage("§e Events Are scheduled every other weekend");
            player.sendMessage("§e Starting at 7:00PM CST.");
            player.sendMessage("§e Event Details will be posted on discord in advance.");
            player.sendMessage("§e ");
            player.sendMessage("§e https://discord.gg/5PvjD44Ba5");
        } else {
            sender.sendMessage("This command can only be used by players.");
        }
        return true;            
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        return Collections.emptyList();  // No tab completion for now
    }
}
