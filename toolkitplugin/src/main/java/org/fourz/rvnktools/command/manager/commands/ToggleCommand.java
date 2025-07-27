package org.fourz.rvnktools.command.manager.commands;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.fourz.rvnktools.RVNKTools;
import org.fourz.rvnktools.command.manager.BaseCommand;
import java.util.List;
import java.util.Collections;

/**
 * Command to toggle a set of server commands on or off for a player.
 * Uses CommandManager framework for registration and permission handling.
 */
public class ToggleCommand extends BaseCommand {
    private final List<String> toggleOnCommands;
    private final List<String> toggleOffCommands;
    private final String toggleOnMessage;
    private final String toggleOffMessage;
    private final String permissionNode;
    private boolean toggleState = false;

    /**
     * @param plugin The plugin instance
     * @param toggleOnCommands Commands to run when toggled on
     * @param toggleOffCommands Commands to run when toggled off
     * @param toggleOnMessage Message to show when toggled on
     * @param toggleOffMessage Message to show when toggled off
     * @param permissionNode Permission required to use the command
     */
    public ToggleCommand(RVNKTools plugin, List<String> toggleOnCommands, List<String> toggleOffCommands, String toggleOnMessage, String toggleOffMessage, String permissionNode) {
        super(plugin, "toggle", "Toggle a set of server commands on/off", "/toggle", permissionNode);
        this.toggleOnCommands = toggleOnCommands;
        this.toggleOffCommands = toggleOffCommands;
        this.toggleOnMessage = toggleOnMessage;
        this.toggleOffMessage = toggleOffMessage;
        this.permissionNode = permissionNode;
    }

    @Override
    protected boolean executeCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§c✖ This command can only be run by a player.");
            return true;
        }
        Player player = (Player) sender;
        if (!player.hasPermission(permissionNode)) {
            player.sendMessage("§c✖ You do not have permission to use this command.");
            return true;
        }
        toggleState = !toggleState;
        if (toggleState) {
            player.sendMessage("§a✓ " + toggleOnMessage);
            for (String cmd : toggleOnCommands) {
                plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(), cmd.replace("{player}", player.getName()));
            }
        } else {
            player.sendMessage("§e⚠ " + toggleOffMessage);
            for (String cmd : toggleOffCommands) {
                plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(), cmd.replace("{player}", player.getName()));
            }
        }
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        return Collections.emptyList();
    }
}
