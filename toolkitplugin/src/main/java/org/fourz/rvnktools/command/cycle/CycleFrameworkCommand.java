package org.fourz.rvnktools.command.cycle;

import org.bukkit.command.CommandSender;
import org.fourz.rvnktools.RVNKTools;
import org.fourz.rvnktools.command.manager.BaseCommand;
import org.bukkit.entity.Player;
import org.bukkit.configuration.ConfigurationSection;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Framework-based wrapper for cycle commands.
 */
public class CycleFrameworkCommand extends BaseCommand {
    private final String commandKey;
    private final ConfigurationSection commandConfig;
    private final CycleCommands cycleCommands;

    public CycleFrameworkCommand(RVNKTools plugin, String commandKey, ConfigurationSection commandConfig, CycleCommands cycleCommands) {
        super(plugin, commandKey, commandConfig.getString("description", "Cycle command"), "/" + commandKey, commandConfig.getString("permission", null));
        this.commandKey = commandKey;
        this.commandConfig = commandConfig;
        this.cycleCommands = cycleCommands;
    }

    @Override
    protected boolean executeCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }
        Player player = (Player) sender;
        UUID playerId = player.getUniqueId();
        String instructionKey = cycleCommands.getNextInstructionKey(commandKey, playerId);
        ConfigurationSection instructions = commandConfig.getConfigurationSection("instructions");
        if (getPermission() != null && !player.hasPermission(getPermission())) {
            player.sendMessage("You do not have permission to use this command.");
            return true;
        }
        if (instructions != null) {
            // For simplicity, just send the instruction key (real logic can be ported from CycleCommandExecutor)
            player.sendMessage("Next instruction: " + instructionKey);
        } else {
            player.sendMessage("No instruction set found for key: " + instructionKey);
        }
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        return Collections.emptyList();
    }
}
