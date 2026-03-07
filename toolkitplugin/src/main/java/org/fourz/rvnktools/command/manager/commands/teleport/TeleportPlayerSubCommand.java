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
 * Subcommand for teleporting players.
 * Supports:
 * - /tp <player> - teleport sender to target player
 * - /tp <player> <target> - teleport first player to second player
 */
public class TeleportPlayerSubCommand extends BaseSubCommand {

    public TeleportPlayerSubCommand(RVNKCore plugin, RVNKCommand parent) {
        super(plugin, parent, "player", "Teleport to a player", "/tp <player> [target]", false);
    }

    @Override
    protected boolean executeSubCommand(CommandSender sender, String[] args) {
        boolean isConsole = !(sender instanceof Player);

        if (args.length == 0) {
            sender.sendMessage("§c▶ Usage: /tp <player> [target]");
            return false;
        }

        // Console requires target player argument
        if (isConsole && args.length < 2) {
            sender.sendMessage("§c▶ Console usage: /tp <player> <target>");
            return false;
        }

        // Get target player (where to teleport)
        String targetName = args[0];
        Player targetPlayer = Bukkit.getPlayer(targetName);

        if (targetPlayer == null) {
            sender.sendMessage("§c✖ Player not found: " + targetName);
            return false;
        }

        // Determine who to teleport
        Player playerToTeleport;

        if (args.length == 1) {
            // /tp <player> - sender teleports to target
            if (isConsole) {
                sender.sendMessage("§c▶ Console usage: /tp <player> <target>");
                return false;
            }
            playerToTeleport = (Player) sender;
        } else {
            // /tp <player> <target> - teleport first player to second
            String teleportPlayerName = args[0];
            playerToTeleport = Bukkit.getPlayer(teleportPlayerName);

            if (playerToTeleport == null) {
                sender.sendMessage("§c✖ Player not found: " + teleportPlayerName);
                return false;
            }

            // Permission check for teleporting others
            if (!isConsole && !sender.hasPermission("rvnktools.command.tp.others")) {
                sender.sendMessage("§c✖ You don't have permission to teleport other players");
                return false;
            }

            targetName = args[1];
            targetPlayer = Bukkit.getPlayer(targetName);

            if (targetPlayer == null) {
                sender.sendMessage("§c✖ Target player not found: " + targetName);
                return false;
            }
        }

        // Execute teleport
        playerToTeleport.teleport(targetPlayer.getLocation());

        // Feedback messages
        playerToTeleport.sendMessage("§a✓ Teleported to " + targetPlayer.getName());

        if (!playerToTeleport.equals(sender)) {
            sender.sendMessage("§a✓ Teleported " + playerToTeleport.getName() + " to " + targetPlayer.getName());
        }

        logger.debug(sender.getName() + " teleported " + playerToTeleport.getName() + " to " + targetPlayer.getName());

        return true;
    }

    @Override
    protected List<String> getTabCompletions(CommandSender sender, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1 || args.length == 2) {
            // Tab complete online player names
            String partial = args[args.length - 1].toLowerCase();
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getName().toLowerCase().startsWith(partial)) {
                    completions.add(player.getName());
                }
            }
        }

        return completions;
    }
}
