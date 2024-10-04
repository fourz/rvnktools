package org.fourz.rvnktools.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

@SuppressWarnings("unused")
public class ToggleCommand implements CommandExecutor {

    private final JavaPlugin plugin;
    private final String commandName;
    private final List<String> toggleOnCommands;
    private final List<String> toggleOffCommands;
    private final String toggleOnMessage;
    private final String toggleOffMessage;
    private final String permissionNode;
    private boolean toggleState = false;

    public ToggleCommand (JavaPlugin plugin, String commandName, List<String> toggleOnCommands, List<String> toggleOffCommands, String toggleOnMessage, String toggleOffMessage, String permissionNode) {
        this.plugin = plugin;
        this.commandName = commandName;
        this.toggleOnCommands = toggleOnCommands;
        this.toggleOffCommands = toggleOffCommands;
        this.toggleOnMessage = toggleOnMessage;
        this.toggleOffMessage = toggleOffMessage;
        this.permissionNode = permissionNode;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("This command can only be run by a player.");
            return true;
        }

        Player player = (Player) sender;

        if (!player.hasPermission(permissionNode)) {
            player.sendMessage("You do not have permission to use this command.");
            return true;
        }

        toggleState = !toggleState;

        if (toggleState) {
            player.sendMessage(toggleOnMessage);
            for (String cmd : toggleOnCommands) {
                plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(), cmd.replace("{player}", player.getName()));
            }
        } else {
            player.sendMessage(toggleOffMessage);
            for (String cmd : toggleOffCommands) {
                plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(), cmd.replace("{player}", player.getName()));
            }
        }

        return true;
    }
}