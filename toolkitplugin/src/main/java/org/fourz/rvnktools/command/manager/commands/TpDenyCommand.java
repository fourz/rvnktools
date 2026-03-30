package org.fourz.rvnktools.command.manager.commands;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.fourz.rvnkcore.RVNKCore;
import org.fourz.rvnkcore.service.teleport.TpaRequest;
import org.fourz.rvnkcore.service.teleport.TpaRequestService;
import org.fourz.rvnkcore.util.ChatFormat;
import org.fourz.rvnktools.command.manager.BaseCommand;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * /tpdeny — Deny a pending teleport request.
 */
public class TpDenyCommand extends BaseCommand {

    private final TpaRequestService tpaService;

    public TpDenyCommand(RVNKCore plugin, TpaRequestService tpaService) {
        super(plugin, "tpdeny", "Deny a pending teleport request", "/tpdeny");
        this.tpaService = tpaService;
    }

    @Override
    protected boolean executeCommand(CommandSender sender, String[] args) {
        if (!validatePlayer(sender)) return true;

        Player player = (Player) sender;
        Optional<TpaRequest> optRequest = tpaService.denyRequest(player.getUniqueId());

        if (optRequest.isEmpty()) {
            sender.sendMessage(ChatFormat.colorize("&c✖ You have no pending teleport requests."));
            return true;
        }

        TpaRequest request = optRequest.get();
        Player requester = Bukkit.getPlayer(request.getSender());

        sender.sendMessage(ChatFormat.colorize("&c✖ Teleport request denied."));

        if (requester != null && requester.isOnline()) {
            requester.sendMessage(ChatFormat.colorize("&c✖ &f" + player.getName() + " &cdenied your teleport request."));
        }

        return true;
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!hasPermission(sender)) {
            sendNoPermissionMessage(sender);
            return true;
        }
        return executeCommand(sender, args);
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        return Collections.emptyList();
    }
}
