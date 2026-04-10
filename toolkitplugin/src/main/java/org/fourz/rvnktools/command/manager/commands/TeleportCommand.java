package org.fourz.rvnktools.command.manager.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.fourz.rvnkcore.RVNKCore;
import org.fourz.rvnkcore.api.service.ITeleportService;
import org.fourz.rvnktools.command.manager.BaseCommand;
import org.fourz.rvnktools.command.manager.commands.teleport.TeleportCoordsSubCommand;
import org.fourz.rvnktools.command.manager.commands.teleport.TeleportHereSubCommand;
import org.fourz.rvnktools.command.manager.commands.teleport.TeleportPlayerSubCommand;

import org.bukkit.Bukkit;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Main teleport command that serves as a container for teleportation-related subcommands.
 * Provides organized access to world swapping and other teleportation utilities.
 *
 * Supports both /teleport and /tp command routing:
 * - /teleport worldswap - classic world swap functionality
 * - /tp <player> - teleport to player (when override enabled)
 * - /tp <x> <y> <z> - teleport to coordinates (when override enabled)
 * - /tp here <player> - teleport player to sender (when override enabled)
 */
public class TeleportCommand extends BaseCommand {

    private final boolean overrideTp;

    public TeleportCommand(RVNKCore plugin, WorldSwapSubCommand sharedWorldSwap) {
        super(plugin, "teleport",
              "Teleportation utilities and world management",
              "/teleport <subcommand> [args]",
              "rvnktools.command.tp");

        this.overrideTp = plugin.getConfig().getBoolean("commands.override-vanilla-tp", false);

        // Register teleport subcommands
        registerSubCommand("worldswap", sharedWorldSwap);

        // Register vanilla teleport subcommands (only registered, may be overridden by TpCommand)
        registerSubCommand("player", new TeleportPlayerSubCommand(plugin, this));
        registerSubCommand("coords", new TeleportCoordsSubCommand(plugin, this));
        registerSubCommand("here", new TeleportHereSubCommand(plugin, this));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        logger.debug("Command executed: " + label + " with " + args.length + " arguments");

        // Check permission
        if (!hasPermission(sender)) {
            sendNoPermissionMessage(sender);
            return true;
        }

        // Smart routing for /tp — always active since /tp is registered in plugin.yml
        // For /teleport, smart routing requires the override-vanilla-tp config flag
        if (label.equalsIgnoreCase("tp") ||
                (label.equalsIgnoreCase("teleport") && overrideTp)) {
            return handleTpSmartRouting(sender, args);
        }

        // Default /teleport behavior
        return execute(sender, args);
    }

    /**
     * Smart routing for /tp command based on argument types.
     * Routes to appropriate subcommand based on what arguments look like.
     */
    private boolean handleTpSmartRouting(CommandSender sender, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        // Check if first arg is a known subcommand
        String firstArg = args[0].toLowerCase();
        if (getSubCommand(firstArg) != null) {
            // Route to subcommand: /tp worldswap, /tp player, /tp coords, /tp here
            return execute(sender, args);
        }

        // Smart routing for vanilla-style usage
        if (isCoordinate(args[0])) {
            // First arg is a number or ~ → coords
            return executeSubCommand("coords", sender, args);
        } else if (args.length >= 3 && isCoordinate(args[1])) {
            // /tp <player> <x> <y> <z> → coords
            return executeSubCommand("coords", sender, args);
        } else {
            // /tp <player> or /tp <player> <player> → player
            return executeSubCommand("player", sender, args);
        }
    }

    /**
     * Execute a specific subcommand with given arguments.
     */
    private boolean executeSubCommand(String subCommandName, CommandSender sender, String[] args) {
        var subCommand = getSubCommand(subCommandName);
        if (subCommand == null) {
            sender.sendMessage("§c✖ Teleport subcommand not found: " + subCommandName);
            return false;
        }

        // Execute subcommand with all arguments
        return subCommand.execute(sender, args);
    }

    /**
     * Check if a string looks like a coordinate (number or ~).
     */
    private boolean isCoordinate(String arg) {
        if (arg.startsWith("~")) return true;
        try {
            Double.parseDouble(arg);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    @Override
    protected boolean executeCommand(CommandSender sender, String[] args) {
        // This will be called when no subcommands match
        sendHelp(sender);
        return true;
    }

    @Override
    public void sendHelp(CommandSender sender) {
        sender.sendMessage("§c▶ §6Teleportation Commands");
        sender.sendMessage("");

        // Show /tp commands if override is enabled
        if (overrideTp) {
            sender.sendMessage("§f/tp <player> §7- Teleport to a player");
            sender.sendMessage("§f/tp <player> <target> §7- Teleport first player to second");
            sender.sendMessage("§f/tp <x> <y> <z> §7- Teleport to coordinates");
            sender.sendMessage("§f/tp <player> <x> <y> <z> §7- Teleport player to coordinates");
            sender.sendMessage("§f/tp here <player> §7- Teleport player to your location");
            sender.sendMessage("");
        }

        // Always show /teleport and /tp worldswap
        sender.sendMessage("§f/teleport worldswap [world] §7- Teleport between worlds preserving locations");
        sender.sendMessage("§7   Your location in each world will be saved and restored");
        sender.sendMessage("§7   Use specific world name or default to 'event' world");

        if (!overrideTp) {
            sender.sendMessage("");
            sender.sendMessage("§e⚠ Vanilla /tp override is disabled in config");
            sender.sendMessage("§7   Set 'commands.override-vanilla-tp: true' in config.yml to enable");
        }
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        // When /tp or /teleport override is active, mimic vanilla tab-complete:
        //   arg1 → online player names (or subcommand names for /teleport worldswap etc.)
        //   arg2 → online player names (second player for from/to)
        if (overrideTp && args.length >= 1) {
            String partial = args[args.length - 1].toLowerCase();
            if (args.length == 1 || args.length == 2) {
                List<String> completions = new ArrayList<>();
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.getName().toLowerCase().startsWith(partial)) {
                        completions.add(p.getName());
                    }
                }
                // Also suggest subcommands (worldswap, here) at arg1
                if (args.length == 1) {
                    getMatchingSubCommands(sender, partial).stream()
                        .filter(s -> !completions.contains(s))
                        .forEach(completions::add);
                }
                return completions;
            }
        }

        if (args.length <= 1) {
            return getMatchingSubCommands(sender, args.length == 0 ? "" : args[0]);
        }

        return super.tabComplete(sender, args);
    }
}
