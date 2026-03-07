package org.fourz.rvnktools.command.manager.commands;

import org.bukkit.command.CommandSender;
import org.fourz.rvnkcore.RVNKCore;
import org.fourz.rvnktools.command.manager.BaseCommand;
import org.fourz.rvnktools.command.manager.BaseSubCommand;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Subcommand for teleport-related functionality.
 * Provides information about teleport commands and directs users to standalone commands.
 */
public class TeleportSubCommand extends BaseSubCommand {

    public TeleportSubCommand(RVNKCore plugin, BaseCommand parent) {
        super(plugin, parent, "teleport",
              "Teleport management commands",
              "/rvnktools teleport <worldswap> [args]",
              "rvnktools.command.teleport", false);

        // Note: This subcommand now only handles help - actual worldswap is done through standalone commands
    }

    @Override
    protected boolean executeSubCommand(CommandSender sender, String[] args) {
        if (args.length == 0) {
            showHelp(sender);
            return true;
        }

        if (args[0].equalsIgnoreCase("worldswap")) {
            // Inform user about the standalone command
            sender.sendMessage("§e⚠ Use /worldswap [world] or /teleport worldswap [world] for world swapping.");
            sender.sendMessage("§7Example: /worldswap event or /teleport worldswap survival");
            return true;
        }

        showHelp(sender);
        return true;
    }

    private void showHelp(CommandSender sender) {
        sender.sendMessage("§c▶ §6Teleport Commands");
        sender.sendMessage("§f/worldswap [world] §7- Teleport between worlds preserving locations");
        sender.sendMessage("§f/teleport worldswap [world] §7- Same as above with full command path");
        sender.sendMessage("§f/event [world] §7- Quick shortcut for event world");
        sender.sendMessage("§7   Use specific world name or default to 'event' world");
        sender.sendMessage("§7   Your location in the current world will be saved");
    }

    @Override
    protected List<String> getTabCompletions(CommandSender sender, String[] args) {
        if (args.length == 1) {
            if (sender.hasPermission("rvnktools.command.teleport.worldswap")) {
                return Arrays.asList("worldswap");
            }
        }
        return Collections.emptyList();
    }
}
