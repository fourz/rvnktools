package org.fourz.rvnktools.command.manager.commands;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.fourz.rvnktools.RVNKTools;
import org.fourz.rvnktools.command.manager.BaseCommand;

import java.util.Collections;
import java.util.List;
import java.util.Arrays;
import java.util.stream.Collectors;

public class TrainsCommand extends BaseCommand {
    private final RVNKTools plugin;
    private final List<String> subcommands = Arrays.asList("enable", "disable", "modes", "examples", "links");

    public TrainsCommand(RVNKTools plugin) {
        super(plugin, "trains", "Toggle TrainCart/Vanilla minecart modes and show help.", "/trains [enable|disable|modes|examples|links]", "rvnktools.command.trains");
        this.plugin = plugin;
    }

    @Override
    protected boolean executeCommand(CommandSender sender, String[] args) {
        if (!validatePlayer(sender)) {
            return true;
        }
        Player player = (Player) sender;

        if (args.length == 0) {
            showHelpPage(player, "");
            return true;
        }        

        String subCommand = args[0].toLowerCase();
        switch (subCommand) {
            case "enable":
                if (!player.hasPermission("rvnktools.command.trains.enable")) {
                    player.sendMessage("§cYou don't have permission to enable trains!");
                    return true;
                }
                plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(),
                        "lp user " + player.getName() + " permission set group.rail-trains");
                player.sendMessage("§aNewly placed minecarts will now create TrainCarts by linking carts together.");
                break;

            case "disable":
                if (!player.hasPermission("rvnktools.command.trains.disable")) {
                    player.sendMessage("§cYou don't have permission to disable trains!");
                    return true;
                }
                plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(),
                        "lp user " + player.getName() + " permission unset group.rail-trains");
                player.sendMessage("§aNewly placed minecarts will now be vanilla minecarts.");
                break;

            case "modes":
            case "examples":
            case "links":
                showHelpPage(player, subCommand);
                break;

            default:
                player.sendMessage("§cUnknown subcommand. Use /trains for help.");
                break;
        }
        return true;
    }

    private void showHelpPage(Player player, String page) {
        switch (page.toLowerCase()) {
            case "modes":
                player.sendMessage("§a ----------- §6TrainCarts Help §fModes §a------------");
                player.sendMessage("§e §c/trains enable §b-- §fset down TrainCarts");
                player.sendMessage("§e §c/trains disable §b-- §fset down vanilla carts");
                player.sendMessage("§e §c/railroad §b-- §ftoggle between modes");
                player.sendMessage("§e Use §6/trains examples §efor the next page");
                player.sendMessage("§e");
                break;

            case "examples":
                player.sendMessage("§a --- §6TrainCarts Help §fCommands and Properties§a ---");
                player.sendMessage("§e Punch a train to select it. Use these commands to modify it.");
                player.sendMessage("§e §c/train setname [name] §b-- §fSet name of train");
                player.sendMessage("§e §c/train info §b-- §fGet train information");
                player.sendMessage("§e §c/train setowner [names...] §b-- §fSet train owners");
                player.sendMessage("§e §c/train list §b-- §fGet list of trains");
                player.sendMessage("§e §c/train maxspeed [0.1 - 3] §b-- §fSet max speed of train");
                player.sendMessage("§e §c/train collision mobs damage §b-- §fSet collision modes");
                player.sendMessage("§e Use §6/trains links §efor the next page");
                player.sendMessage("§e");
                break;

            case "links":
                player.sendMessage("§a ---------- §6TrainCarts Help §fLinks §a-----------");
                player.sendMessage("§e §dDemo Video§b https://www.youtube.com/watch?v=XfCjDgMWogU&t=70s");
                player.sendMessage("§e §dDocumentation§b https://wiki.traincarts.net/p/TrainCarts");
                player.sendMessage("§b https://wiki.traincarts.net/p/TrainCarts/Signs/Property");
                player.sendMessage("§b https://wiki.traincarts.net/p/TrainCarts/TrainProperties");
                player.sendMessage("§e §dSigns Video§b https://www.youtube.com/watch?v=qPUiLJ5f2h8");
                player.sendMessage("§e");
                player.sendMessage("§e §c/train help §b-- §fTraincarts Default help.");
                break;
                
            case "default":
            default:
                player.sendMessage("§a -------------- §6TrainCarts Help §fWelcome §a-----------------");
                player.sendMessage("§e Ravenkraft has an opt-in, per-player Train plugin, allowing");
                player.sendMessage("§e players to toggle between TrainCart and Vanilla minecart modes.");
                player.sendMessage("§e Speed and collision behavior can be set on each train.");
                player.sendMessage("§e Use §6/trains modes §efor the next page");
                player.sendMessage("§e");
                break;
        }
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            String partialArg = args[0].toLowerCase();
            return subcommands.stream()
                    .filter(cmd -> cmd.startsWith(partialArg))
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}
