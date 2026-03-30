package org.fourz.rvnktools.command.manager.commands;

import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.fourz.rvnkcore.RVNKCore;
import org.fourz.rvnkcore.service.teleport.TpaRequest;
import org.fourz.rvnkcore.service.teleport.TpaRequestService;
import org.fourz.rvnkcore.util.ChatFormat;
import org.fourz.rvnktools.command.manager.BaseCommand;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * /tpa &lt;player&gt; — Request to teleport to another player.
 * Cross-world supported; uses ITeleportService (gets RVNKWorlds handling if available).
 */
public class TpaCommand extends BaseCommand {

    private final TpaRequestService tpaService;
    private final TpaRequest.Type requestType;

    /**
     * @param requestType TPA (sender goes to target) or TPAHERE (target comes to sender)
     */
    public TpaCommand(RVNKCore plugin, TpaRequestService tpaService,
                      String name, String description, String usage,
                      TpaRequest.Type requestType) {
        super(plugin, name, description, usage);
        this.tpaService = tpaService;
        this.requestType = requestType;
    }

    @Override
    protected boolean executeCommand(CommandSender sender, String[] args) {
        if (!validatePlayer(sender)) return true;
        if (!validateArgs(sender, args, 1, "§c▶ Usage: §e/" + getName() + " <player>")) return true;

        Player player = (Player) sender;
        Player target = Bukkit.getPlayer(args[0]);

        if (target == null || !target.isOnline()) {
            sender.sendMessage(ChatFormat.colorize("&c✖ Player '" + args[0] + "' is not online."));
            return true;
        }

        Optional<String> error = tpaService.sendRequest(player, target, requestType);
        if (error.isPresent()) {
            sender.sendMessage(ChatFormat.colorize("&c✖ " + error.get()));
            return true;
        }

        // Notify sender
        sender.sendMessage(ChatFormat.colorize("&a✓ Teleport request sent to &f" + target.getName()));

        // Notify target with clickable accept/deny
        String label = requestType == TpaRequest.Type.TPA
            ? player.getName() + " wants to teleport to you."
            : player.getName() + " wants you to teleport to them.";

        target.sendMessage(ChatFormat.colorize("&e⚠ " + label));

        TextComponent accept = new TextComponent(ChatFormat.colorize("&a&l[Accept]"));
        accept.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tpaccept"));

        TextComponent space = new TextComponent("  ");

        TextComponent deny = new TextComponent(ChatFormat.colorize("&c&l[Deny]"));
        deny.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tpdeny"));

        target.spigot().sendMessage(accept, space, deny);

        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (!hasPermission(sender)) return Collections.emptyList();
        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            List<String> matches = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(partial) && !p.equals(sender)) {
                    matches.add(p.getName());
                }
            }
            return matches;
        }
        return Collections.emptyList();
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!hasPermission(sender)) {
            sendNoPermissionMessage(sender);
            return true;
        }
        return executeCommand(sender, args);
    }
}
