package org.fourz.rvnktools.command.manager.commands;

import org.bukkit.command.CommandSender;
import org.fourz.rvnkcore.RVNKCore;
import org.fourz.rvnktools.command.manager.BaseCommand;
import java.util.List;

/**
 * Main RVNKTools command that serves as a container for administrative subcommands.
 * Provides access to plugin configuration, debugging, and utility functions.
 */
public class RVNKToolsCommand extends BaseCommand {

    public RVNKToolsCommand(RVNKCore plugin) {
        super(plugin, "rvnktools",
              "Main administrative command for RVNKTools plugin",
              "/rvnktools <subcommand> [args]",
              "rvnktools.command");

        // Register admin subcommands (these don't use world swap)
        registerSubCommand("links", new LinksSubCommand(plugin, this));
        registerSubCommand("cycle", new CycleSubCommand(plugin, this));
        registerSubCommand("reload", new ReloadSubCommand(plugin, this));
        registerSubCommand("debug", new DebugSubCommand(plugin, this));

        // Create teleport subcommands without world swap - they will get it from CommandManager
        registerSubCommand("teleport", new TeleportSubCommand(plugin, this));
    }

    @Override
    protected boolean executeCommand(CommandSender sender, String[] args) {
        // This will be called when no subcommands match
        sendHelp(sender);
        return true;
    }

    /**
     * The subcommand list is generated (#1981); only the standalone shortcuts are hand-written,
     * because no registry knows about them.
     *
     * <p>The previous hand-kept list advertised
     * {@code /rvnktools createtestdata [all|types|announcements]}. That command has no handler
     * anywhere in the plugin and never ran &mdash; the line was the only trace of it. That is the
     * cost of restating a registry by hand, so the list below comes from the registry.</p>
     */
    @Override
    public void sendHelp(CommandSender sender) {
        sender.sendMessage("§c▶ §6RVNKTools Administrative Commands");
        sender.sendMessage("§7   Use §f/rvnktools help <subcommand>§7 for usage and examples");
        sender.sendMessage("");
        sendSubCommandList(sender);
        sender.sendMessage("");
        sender.sendMessage("§e⚠ §7Standalone shortcuts (not subcommands):");
        sender.sendMessage("§f/worldswap [world] §7- Direct world swap command");
        sender.sendMessage("§f/event [world] §7- Event world shortcut command");
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length <= 1) {
            return getMatchingSubCommands(sender, args.length == 0 ? "" : args[0]);
        }

        return super.tabComplete(sender, args);
    }
}
