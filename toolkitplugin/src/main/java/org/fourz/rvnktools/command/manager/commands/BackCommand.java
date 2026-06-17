package org.fourz.rvnktools.command.manager.commands;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.fourz.rvnkcore.RVNKCore;
import org.fourz.rvnkcore.api.service.ITeleportService;
import org.fourz.rvnkcore.service.registry.ServiceRegistry;
import org.fourz.rvnkcore.service.teleport.BackLocationService;
import org.fourz.rvnkcore.service.teleport.TpaRequestService;
import org.fourz.rvnkcore.util.ChatFormat;
import org.fourz.rvnktools.command.manager.BaseCommand;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * /back — Teleport to your previous location (before last teleport).
 * Supports back-and-forth: current location becomes new back location.
 * Console: /back &lt;player&gt; to teleport a named player back.
 */
public class BackCommand extends BaseCommand {

    private final TpaRequestService tpaService;
    private final BackLocationService backService;

    public BackCommand(RVNKCore plugin, TpaRequestService tpaService, BackLocationService backService) {
        super(plugin, "back", "Teleport to your previous location", "/back");
        this.tpaService = tpaService;
        this.backService = backService;
    }

    @Override
    protected boolean executeCommand(CommandSender sender, String[] args) {
        Player player;

        // Console support: /back <player>
        if (!(sender instanceof Player)) {
            if (args.length < 1) {
                sender.sendMessage("Usage: /back <player>");
                return true;
            }
            player = Bukkit.getPlayer(args[0]);
            if (player == null || !player.isOnline()) {
                sender.sendMessage("Player '" + args[0] + "' is not online.");
                return true;
            }
        } else {
            player = (Player) sender;
        }

        Optional<Location> optBack = backService.getBackLocation(player.getUniqueId());
        if (optBack.isEmpty()) {
            sender.sendMessage(ChatFormat.colorize("&c✖ No previous location to return to."));
            return true;
        }

        Location backLoc = optBack.get();

        // Check cooldown (uses same system as TPA)
        if (sender instanceof Player && tpaService.isOnCooldown(player)) {
            sender.sendMessage(ChatFormat.colorize("&c✖ You must wait before using /back again."));
            return true;
        }

        // Store current location as new back location (enables back-and-forth)
        backService.setBackLocation(player.getUniqueId(), player.getLocation());

        // Execute with warmup
        tpaService.startWarmup(player, () -> {
            ServiceRegistry registry = plugin.getServiceRegistry();
            if (registry.hasService(ITeleportService.class)) {
                ITeleportService teleportService = registry.getService(ITeleportService.class);
                // Use teleportToPlayer approach for cross-world support
                Bukkit.getScheduler().runTask(plugin, () -> {
                    player.teleport(backLoc);
                    player.sendMessage(ChatFormat.colorize("&a✓ Teleported to your previous location."));
                });
            } else {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    player.teleport(backLoc);
                    player.sendMessage(ChatFormat.colorize("&a✓ Teleported to your previous location."));
                });
            }
        });

        if (sender != player) {
            sender.sendMessage("Teleporting " + player.getName() + " to their previous location.");
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
        if (!hasPermission(sender)) return Collections.emptyList();
        // Console: tab complete player names
        if (!(sender instanceof Player) && args.length == 1) {
            String partial = args[0].toLowerCase();
            List<String> matches = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(partial)) {
                    matches.add(p.getName());
                }
            }
            return matches;
        }
        return Collections.emptyList();
    }
}
