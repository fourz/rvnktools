package org.fourz.rvnktools.command.manager.commands;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.fourz.rvnkcore.RVNKCore;
import org.fourz.rvnkcore.api.service.ITeleportService;
import org.fourz.rvnkcore.service.registry.ServiceRegistry;
import org.fourz.rvnkcore.service.teleport.BackLocationService;
import org.fourz.rvnkcore.service.teleport.TpaRequest;
import org.fourz.rvnkcore.service.teleport.TpaRequestService;
import org.fourz.rvnkcore.util.ChatFormat;
import org.fourz.rvnktools.command.manager.BaseCommand;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * /tpaccept — Accept a pending teleport request.
 */
public class TpAcceptCommand extends BaseCommand {

    private final TpaRequestService tpaService;
    private final BackLocationService backService;

    public TpAcceptCommand(RVNKCore plugin, TpaRequestService tpaService, BackLocationService backService) {
        super(plugin, "tpaccept", "Accept a pending teleport request", "/tpaccept");
        this.tpaService = tpaService;
        this.backService = backService;
    }

    @Override
    protected boolean executeCommand(CommandSender sender, String[] args) {
        if (!validatePlayer(sender)) return true;

        Player player = (Player) sender;
        Optional<TpaRequest> optRequest = tpaService.acceptRequest(player.getUniqueId());

        if (optRequest.isEmpty()) {
            sender.sendMessage(ChatFormat.colorize("&c✖ You have no pending teleport requests."));
            return true;
        }

        TpaRequest request = optRequest.get();
        Player senderPlayer = Bukkit.getPlayer(request.getSender());
        Player targetPlayer = Bukkit.getPlayer(request.getTarget());

        if (senderPlayer == null || !senderPlayer.isOnline() ||
            targetPlayer == null || !targetPlayer.isOnline()) {
            sender.sendMessage(ChatFormat.colorize("&c✖ The other player is no longer online."));
            return true;
        }

        // Determine who teleports where
        Player teleporting;
        Player destination;
        if (request.getType() == TpaRequest.Type.TPA) {
            // TPA: sender teleports to target (the acceptor)
            teleporting = senderPlayer;
            destination = targetPlayer;
        } else {
            // TPAHERE: target (the acceptor) teleports to sender
            teleporting = targetPlayer;
            destination = senderPlayer;
        }

        // Store back location before teleporting
        backService.setBackLocation(teleporting.getUniqueId(), teleporting.getLocation());

        // Execute teleport with warmup
        tpaService.startWarmup(teleporting, () -> {
            // Use ITeleportService for the actual teleport (gets RVNKWorlds handling if available)
            ServiceRegistry registry = plugin.getServiceRegistry();
            if (registry.hasService(ITeleportService.class)) {
                ITeleportService teleportService = registry.getService(ITeleportService.class);
                teleportService.teleportToPlayer(teleporting, destination).thenAccept(success -> {
                    if (success) {
                        teleporting.sendMessage(ChatFormat.colorize("&a✓ Teleported to &f" + destination.getName()));
                        destination.sendMessage(ChatFormat.colorize("&a✓ &f" + teleporting.getName() + " &ateleported to you."));
                    } else {
                        teleporting.sendMessage(ChatFormat.colorize("&c✖ Teleport failed."));
                    }
                });
            } else {
                // Fallback: direct Bukkit teleport
                Bukkit.getScheduler().runTask(plugin, () -> {
                    teleporting.teleport(destination.getLocation());
                    teleporting.sendMessage(ChatFormat.colorize("&a✓ Teleported to &f" + destination.getName()));
                    destination.sendMessage(ChatFormat.colorize("&a✓ &f" + teleporting.getName() + " &ateleported to you."));
                });
            }
        });

        // Notify both players
        if (tpaService.getWarmupSeconds() > 0 && !teleporting.hasPermission(TpaRequestService.BYPASS_WARMUP_PERM)) {
            destination.sendMessage(ChatFormat.colorize("&a✓ Teleport request accepted. &f" +
                teleporting.getName() + " &ais warming up..."));
        } else {
            player.sendMessage(ChatFormat.colorize("&a✓ Teleport request accepted."));
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
