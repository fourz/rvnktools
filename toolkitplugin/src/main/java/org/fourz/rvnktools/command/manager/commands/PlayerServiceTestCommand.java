package org.fourz.rvnktools.command.manager.commands;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.fourz.rvnkcore.RVNKCore;
import org.fourz.rvnkcore.api.model.PlayerDTO;
import org.fourz.rvnkcore.api.service.PlayerService;
import org.fourz.rvnktools.RVNKTools;
import org.fourz.rvnktools.command.manager.BaseCommand;
import org.fourz.rvnktools.listener.LuckPermsIntegrationListener;
import org.fourz.rvnktools.util.ChatFormat;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Debug command for testing PlayerService functionality.
 * 
 * Provides commands to test recent players lookup and LuckPerms integration.
 * 
 * @since 1.0.0
 */
public class PlayerServiceTestCommand extends BaseCommand {
    
    private final RVNKCore rvnkCore;
    
    public PlayerServiceTestCommand(RVNKTools plugin) {
        super(plugin, "pstest", 
              "Test PlayerService functionality", 
              "/pstest <recent|groups|syncgroups> [args...]",
              "rvnktools.admin.pstest");
        this.rvnkCore = plugin.getRVNKCore();
    }
    
    @Override
    protected boolean executeCommand(CommandSender sender, String[] args) {
        if (!rvnkCore.isInitialized()) {
            sender.sendMessage(ChatFormat.colorize("&c✖ RVNKCore is not initialized"));
            return true;
        }
        
        if (args.length == 0) {
            sender.sendMessage(ChatFormat.colorize("&c▶ Usage: /pstest <recent|groups|syncgroups> [args...]"));
            sender.sendMessage(ChatFormat.colorize("&7␣␣␣ recent [hours] - Show recent players"));
            sender.sendMessage(ChatFormat.colorize("&7␣␣␣ groups [groupname] - Show players in group"));
            sender.sendMessage(ChatFormat.colorize("&7␣␣␣ syncgroups [player] - Sync LuckPerms groups for player"));
            return true;
        }
        
        String subCommand = args[0].toLowerCase();
        
        switch (subCommand) {
            case "recent":
                handleRecentCommand(sender, args);
                break;
            case "groups":
                handleGroupsCommand(sender, args);
                break;
            case "syncgroups":
                handleSyncGroupsCommand(sender, args);
                break;
            default:
                sender.sendMessage(ChatFormat.colorize("&c✖ Unknown subcommand: " + subCommand));
                break;
        }
        
        return true;
    }
    
    private void handleRecentCommand(CommandSender sender, String[] args) {
        int hours = 24; // Default to 24 hours
        
        if (args.length > 1) {
            try {
                hours = Integer.parseInt(args[1]);
                if (hours <= 0) {
                    sender.sendMessage(ChatFormat.colorize("&c✖ Hours must be a positive number"));
                    return;
                }
            } catch (NumberFormatException e) {
                sender.sendMessage(ChatFormat.colorize("&c✖ Invalid number: " + args[1]));
                return;
            }
        }
        
        final int finalHours = hours; // Make it effectively final for lambda
        sender.sendMessage(ChatFormat.colorize("&6⚙ Retrieving players active in the last " + finalHours + " hours..."));
        
        try {
            PlayerService playerService = rvnkCore.getService(PlayerService.class);
            
            playerService.getRecentPlayers(finalHours)
                .thenAccept(recentPlayers -> {
                    if (recentPlayers.isEmpty()) {
                        sender.sendMessage(ChatFormat.colorize("&e⚠ No players found active in the last " + finalHours + " hours"));
                        return;
                    }
                    
                    sender.sendMessage(ChatFormat.colorize("&a✓ Found " + recentPlayers.size() + " recent player(s):"));
                    
                    for (PlayerDTO player : recentPlayers) {
                        String status = Bukkit.getPlayer(player.getId()) != null ? "&aonline" : "&7offline";
                        sender.sendMessage(ChatFormat.colorize("&7   • &f" + player.getCurrentName() + 
                                         " &7(" + status + "&7, last seen: " + player.getLastSeen() + ")"));
                    }
                })
                .exceptionally(throwable -> {
                    sender.sendMessage(ChatFormat.colorize("&c✖ Error retrieving recent players: " + throwable.getMessage()));
                    return null;
                });
                
        } catch (Exception e) {
            sender.sendMessage(ChatFormat.colorize("&c✖ Failed to get PlayerService: " + e.getMessage()));
        }
    }
    
    private void handleGroupsCommand(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatFormat.colorize("&c▶ Usage: /pstest groups <groupname>"));
            return;
        }
        
        String groupName = args[1];
        sender.sendMessage(ChatFormat.colorize("&6⚙ Retrieving players in group: " + groupName + "..."));
        
        try {
            PlayerService playerService = rvnkCore.getService(PlayerService.class);
            
            playerService.getPlayersByGroup(groupName)
                .thenAccept(groupPlayers -> {
                    if (groupPlayers.isEmpty()) {
                        sender.sendMessage(ChatFormat.colorize("&e⚠ No players found in group: " + groupName));
                        return;
                    }
                    
                    sender.sendMessage(ChatFormat.colorize("&a✓ Found " + groupPlayers.size() + " player(s) in group '" + groupName + "':"));
                    
                    for (PlayerDTO player : groupPlayers) {
                        String status = Bukkit.getPlayer(player.getId()) != null ? "&aonline" : "&7offline";
                        sender.sendMessage(ChatFormat.colorize("&7   • &f" + player.getCurrentName() + 
                                         " &7(" + status + "&7, groups: " + player.getGroups() + ")"));
                    }
                })
                .exceptionally(throwable -> {
                    sender.sendMessage(ChatFormat.colorize("&c✖ Error retrieving players by group: " + throwable.getMessage()));
                    return null;
                });
                
        } catch (Exception e) {
            sender.sendMessage(ChatFormat.colorize("&c✖ Failed to get PlayerService: " + e.getMessage()));
        }
    }
    
    private void handleSyncGroupsCommand(CommandSender sender, String[] args) {
        Player targetPlayer = null;
        
        if (args.length > 1) {
            targetPlayer = Bukkit.getPlayer(args[1]);
            if (targetPlayer == null) {
                sender.sendMessage(ChatFormat.colorize("&c✖ Player not found: " + args[1]));
                return;
            }
        } else if (sender instanceof Player) {
            targetPlayer = (Player) sender;
        } else {
            sender.sendMessage(ChatFormat.colorize("&c▶ Usage: /pstest syncgroups <playername>"));
            return;
        }
        
        UUID playerId = targetPlayer.getUniqueId();
        String playerName = targetPlayer.getName();
        
        sender.sendMessage(ChatFormat.colorize("&6⚙ Synchronizing LuckPerms groups for: " + playerName + "..."));
        
        // Get the LuckPerms listener from the plugin
        LuckPermsIntegrationListener luckPermsListener = plugin.getLuckPermsListener();
        if (luckPermsListener == null) {
            sender.sendMessage(ChatFormat.colorize("&c✖ LuckPerms integration is not available"));
            return;
        }
        
        luckPermsListener.updatePlayerGroups(playerId)
            .thenRun(() -> {
                sender.sendMessage(ChatFormat.colorize("&a✓ Successfully synchronized groups for: " + playerName));
            })
            .exceptionally(throwable -> {
                sender.sendMessage(ChatFormat.colorize("&c✖ Error synchronizing groups: " + throwable.getMessage()));
                return null;
            });
    }
    
    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("recent", "groups", "syncgroups");
        } else if (args.length == 2) {
            switch (args[0].toLowerCase()) {
                case "recent":
                    return Arrays.asList("1", "6", "12", "24", "72", "168");
                case "groups":
                    return Arrays.asList("default", "vip", "admin", "staff");
                case "syncgroups":
                    return null; // Return null to show online player names
            }
        }
        
        return Collections.emptyList();
    }
}
