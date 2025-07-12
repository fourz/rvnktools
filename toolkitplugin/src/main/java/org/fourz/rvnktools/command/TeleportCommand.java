package org.fourz.rvnktools.command;

import org.bukkit.command.CommandSender;
import org.fourz.rvnktools.RVNKTools;

import java.util.Map;

/**
 * Teleport command that serves as a container for teleport-related subcommands
 */
public class TeleportCommand extends RVNKToolsSubCommand {
    
    private final Map<String, RVNKToolsSubCommand> teleportSubcommands;

    public TeleportCommand(RVNKTools plugin, Map<String, RVNKToolsSubCommand> teleportSubcommands) {
        super(plugin);
        this.teleportSubcommands = teleportSubcommands;
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length == 0) {
            showHelp(sender);
            return true;
        }

        RVNKToolsSubCommand subCommand = teleportSubcommands.get(args[0].toLowerCase());
        if (subCommand == null) {
            showHelp(sender);
            return true;
        }

        String[] subCommandArgs = new String[args.length - 1];
        System.arraycopy(args, 1, subCommandArgs, 0, args.length - 1);
        
        return subCommand.execute(sender, subCommandArgs);
    }

    private void showHelp(CommandSender sender) {
        sender.sendMessage("§6=== RVNKTools Teleport Commands ===");
        sender.sendMessage("§f/rvnktools teleport worldswap [world] §7- Teleport between worlds preserving locations");
    }
}
