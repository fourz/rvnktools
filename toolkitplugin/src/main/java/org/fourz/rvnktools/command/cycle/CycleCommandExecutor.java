package org.fourz.rvnktools.command.cycle;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Iterator;

/**
 * Executor for cycle commands. Handles the execution of cycle commands with their
 * different instruction sets.
 */
public class CycleCommandExecutor implements CommandExecutor {
    private final String commandKey;
    private final ConfigurationSection commandConfig;
    private final CycleCommands cycleCommands;

    public CycleCommandExecutor(String commandKey, ConfigurationSection commandConfig, CycleCommands cycleCommands) {
        this.commandKey = commandKey;
        this.commandConfig = commandConfig;
        this.cycleCommands = cycleCommands;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }
        
        Player player = (Player) sender;
        UUID playerId = player.getUniqueId();
        String instructionKey = cycleCommands.getNextInstructionKey(commandKey, playerId);
        ConfigurationSection instructions = commandConfig.getConfigurationSection("instructions");

        if (commandConfig.contains("permission")) {                
            String permissionsString = commandConfig.getString("permission").toLowerCase();     
            if (!player.hasPermission(permissionsString)) {
                player.sendMessage("You do not have permission to use this command.");
                return true;
            }
        }

        if (instructions != null) {
            List<Map<?, ?>> instructionList = instructions.getMapList(instructionKey);
            executeInstructions(player, instructionList.iterator());
        } else {
            player.sendMessage("No instruction set found for key: " + instructionKey);
        }

        return true;
    }

    private void executeInstructions(Player player, Iterator<Map<?, ?>> iterator) {
        if (!iterator.hasNext()) return;

        Map<?, ?> instruction = iterator.next();            
        String key = instruction.keySet().iterator().next().toString();
        String value = instruction.get(key).toString();

        switch (key) {
            case "run_command_as_player":
                player.performCommand(value);
                executeInstructions(player, iterator);
                break;
            case "run_command_as_server":
                String serverCommand = value.replace("$player", player.getName());
                cycleCommands.executeCommandAsync(serverCommand);
                executeInstructions(player, iterator);
                break;
            case "send_message_to_player":
                value = value.replace("$player", player.getName());
                cycleCommands.messagePlayer(player, value);
                executeInstructions(player, iterator);
                break;
            case "send_message_to_all_players":
                value = value.replace("$player", player.getName());
                cycleCommands.messageAllPlayers(value);
                executeInstructions(player, iterator);
                break;
            case "set_permission":
                cycleCommands.getPlugin().permissionService.addPermission(player.getUniqueId(), value);
                executeInstructions(player, iterator);
                break;
            case "unset_permission":
                cycleCommands.getPlugin().permissionService.removePermission(player.getUniqueId(), value);
                executeInstructions(player, iterator);
                break;
            case "wait":
                long delay = cycleCommands.parseTime(value);
                Bukkit.getScheduler().runTaskLater(cycleCommands.getPlugin(), () -> executeInstructions(player, iterator), delay);
                break;
            default:
                cycleCommands.getPlugin().getLogger().warning("Unknown instruction: " + key);
                executeInstructions(player, iterator);
                break;
        }
    }
}
