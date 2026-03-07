package org.fourz.rvnktools.command.manager.commands.teleport;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.fourz.rvnkcore.RVNKCore;
import org.fourz.rvnktools.command.manager.BaseSubCommand;
import org.fourz.rvnktools.command.manager.RVNKCommand;

import java.util.ArrayList;
import java.util.List;

/**
 * Subcommand for teleporting a player to the sender's location.
 * Supports:
 * - /tp here <player> - teleport player to sender's location
 */
public class TeleportHereSubCommand extends BaseSubCommand {

    public TeleportHereSubCommand(RVNKCore plugin, RVNKCommand parent) {
        super(plugin, parent, "here", "Teleport a player to your location", "/tp here <player>",
              "rvnktools.command.tp.others", true);
    }

    @Override
    protected boolean executeSubCommand(CommandSender sender, String[] args) {
        if (args.length == 0) {
            sender.sendMessage("§c▶ Usage: /tp here <player>");
            return false;
        }

        Player senderPlayer = (Player) sender;
        String targetName = args[0];
        Player targetPlayer = Bukkit.getPlayer(targetName);

        if (targetPlayer == null) {
            sender.sendMessage("§c✖ Player not found: " + targetName);
            return false;
        }

        // Teleport target to sender
        targetPlayer.teleport(senderPlayer.getLocation());

        // Feedback messages
        targetPlayer.sendMessage("§a✓ Teleported to " + senderPlayer.getName());
        sender.sendMessage("§a✓ Teleported " + targetPlayer.getName() + " to your location");

        logger.debug(sender.getName() + " teleported " + targetPlayer.getName() + " to their location");

        return true;
    }

    @Override
    protected List<String> getTabCompletions(CommandSender sender, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getName().toLowerCase().startsWith(partial)) {
                    completions.add(player.getName());
                }
            }
        }

        return completions;
    }
}
