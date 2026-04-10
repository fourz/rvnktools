package org.fourz.rvnktools.command.manager.commands.teleport;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.fourz.rvnkcore.RVNKCore;
import org.fourz.rvnkcore.api.service.ITeleportService;
import org.fourz.rvnktools.command.manager.BaseSubCommand;
import org.fourz.rvnktools.command.manager.RVNKCommand;

import java.util.ArrayList;
import java.util.List;

/**
 * Subcommand for teleporting to coordinates.
 * Supports:
 * - /tp <x> <y> <z> - teleport sender to coordinates
 * - /tp <player> <x> <y> <z> - teleport player to coordinates
 * Supports relative coordinates (~) and absolute coordinates
 */
public class TeleportCoordsSubCommand extends BaseSubCommand {

    public TeleportCoordsSubCommand(RVNKCore plugin, RVNKCommand parent) {
        super(plugin, parent, "coords", "Teleport to coordinates", "/tp <x> <y> <z>", false);
    }

    @Override
    protected boolean executeSubCommand(CommandSender sender, String[] args) {
        boolean isConsole = !(sender instanceof Player);

        if (args.length < 3) {
            sender.sendMessage("§c▶ Usage: /tp <x> <y> <z>");
            return false;
        }

        Player playerToTeleport;
        int coordStartIndex;

        // Determine if first arg is player name or coordinate
        if (args.length >= 4) {
            // /tp <player> <x> <y> <z>
            String playerName = args[0];
            playerToTeleport = Bukkit.getPlayer(playerName);

            if (playerToTeleport == null) {
                sender.sendMessage("§c✖ Player not found: " + playerName);
                return false;
            }

            // Permission check for teleporting others
            if (!isConsole && !sender.hasPermission(ITeleportService.PERM_TP_OTHERS)) {
                sender.sendMessage("§c✖ You don't have permission to teleport other players");
                return false;
            }

            coordStartIndex = 1;
        } else {
            // /tp <x> <y> <z> - teleport sender
            if (isConsole) {
                sender.sendMessage("§c▶ Console usage: /tp <player> <x> <y> <z>");
                return false;
            }
            playerToTeleport = (Player) sender;
            coordStartIndex = 0;
        }

        // Parse coordinates
        try {
            double x = parseCoordinate(args[coordStartIndex], playerToTeleport.getLocation().getX());
            double y = parseCoordinate(args[coordStartIndex + 1], playerToTeleport.getLocation().getY());
            double z = parseCoordinate(args[coordStartIndex + 2], playerToTeleport.getLocation().getZ());

            Location targetLocation = new Location(playerToTeleport.getWorld(), x, y, z);
            targetLocation.setPitch(playerToTeleport.getLocation().getPitch());
            targetLocation.setYaw(playerToTeleport.getLocation().getYaw());

            // COMMAND cause: WorldGuard treats this as op-level, bypasses region entry checks.
            playerToTeleport.teleport(targetLocation, TeleportCause.COMMAND);

            // Feedback messages
            String coordsText = String.format("%.1f, %.1f, %.1f", x, y, z);
            playerToTeleport.sendMessage("§a✓ Teleported to " + coordsText);

            if (!playerToTeleport.equals(sender)) {
                sender.sendMessage("§a✓ Teleported " + playerToTeleport.getName() + " to " + coordsText);
            }

            logger.debug(sender.getName() + " teleported " + playerToTeleport.getName() + " to " + coordsText);

            return true;

        } catch (NumberFormatException e) {
            sender.sendMessage("§c✖ Invalid coordinate format");
            return false;
        }
    }

    /**
     * Parse coordinate supporting relative (~) and absolute coordinates
     */
    private double parseCoordinate(String input, double current) throws NumberFormatException {
        if (input.startsWith("~")) {
            if (input.length() == 1) {
                return current; // ~ alone = current position
            }
            // ~5 = current + 5
            double offset = Double.parseDouble(input.substring(1));
            return current + offset;
        }
        // Absolute coordinate
        return Double.parseDouble(input);
    }

    @Override
    protected List<String> getTabCompletions(CommandSender sender, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            // First arg: player name or X coordinate
            String partial = args[0].toLowerCase();

            // Add online player names
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getName().toLowerCase().startsWith(partial)) {
                    completions.add(player.getName());
                }
            }

            // Add coordinate suggestions
            completions.add("~");  // Relative
            completions.add("0");  // Absolute
        } else if (args.length == 2 || args.length == 3) {
            // Y and Z coordinates
            completions.add("~");
            completions.add("0");
        }

        return completions;
    }
}
