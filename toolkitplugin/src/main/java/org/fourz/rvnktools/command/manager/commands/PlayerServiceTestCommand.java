package org.fourz.rvnktools.command.manager.commands;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.fourz.rvnkcore.RVNKCore;
import org.fourz.rvnkcore.api.model.PlayerDTO;
import org.fourz.rvnkcore.api.service.PlayerService;
import org.fourz.rvnktools.command.manager.BaseCommand;
import org.fourz.rvnktools.listener.LuckPermsIntegrationListener;
import org.fourz.rvnkcore.util.ChatFormat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
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
    
    public PlayerServiceTestCommand(RVNKCore plugin) {
        super(plugin, "pstest",
              "Test PlayerService functionality",
              "/pstest <recent|groups|syncgroups> [args...]",
              "rvnktools.admin.pstest");
        this.rvnkCore = plugin;
    }
    
    @Override
    protected boolean executeCommand(CommandSender sender, String[] args) {
        if (!rvnkCore.isInitialized()) {
            sender.sendMessage(ChatFormat.colorize("&c✖ RVNKCore is not initialized"));
            return true;
        }
        
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }
        
        String subCommand = args[0].toLowerCase();
        
        switch (subCommand) {
            case "all":
                handleAllPlayersCommand(sender, args);
                break;
            case "recent":
                handleRecentCommand(sender, args);
                break;
            case "online":
                handleOnlineCommand(sender, args);
                break;
            case "uuid":
                handleUuidCommand(sender, args);
                break;
            case "name":
                handleNameCommand(sender, args);
                break;
            case "groups":
                handleGroupsCommand(sender, args);
                break;
            case "search":
                handleSearchCommand(sender, args);
                break;
            case "count":
                handleCountCommand(sender, args);
                break;
            case "location":
                handleLocationCommand(sender, args);
                break;
            case "updategroups":
                handleUpdateGroupsCommand(sender, args);
                break;
            case "syncgroups":
                handleSyncGroupsCommand(sender, args);
                break;
            case "create":
                handleCreateCommand(sender, args);
                break;
            case "exists":
                handleExistsCommand(sender, args);
                break;
            default:
                sender.sendMessage(ChatFormat.colorize("&c✖ Unknown subcommand: " + subCommand));
                sendHelp(sender);
                break;
        }
        
        return true;
    }
    
    public void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatFormat.colorize("&c▶ &6PlayerService Test Commands"));
        sender.sendMessage(ChatFormat.colorize("&7␣␣␣ &f/pstest all &7- Get all players from database"));
        sender.sendMessage(ChatFormat.colorize("&7␣␣␣ &f/pstest recent [hours] &7- Show recent players"));
        sender.sendMessage(ChatFormat.colorize("&7␣␣␣ &f/pstest online &7- Get currently online players"));
        sender.sendMessage(ChatFormat.colorize("&7␣␣␣ &f/pstest uuid <uuid> &7- Get player by UUID"));
        sender.sendMessage(ChatFormat.colorize("&7␣␣␣ &f/pstest name <name> &7- Get player by name"));
        sender.sendMessage(ChatFormat.colorize("&7␣␣␣ &f/pstest groups <groupname> &7- Show players in group"));
        sender.sendMessage(ChatFormat.colorize("&7␣␣␣ &f/pstest search <pattern> &7- Search players by name"));
        sender.sendMessage(ChatFormat.colorize("&7␣␣␣ &f/pstest count &7- Get total player count"));
        sender.sendMessage(ChatFormat.colorize("&7␣␣␣ &f/pstest location <player> <world> <x> <y> <z> &7- Update location"));
        sender.sendMessage(ChatFormat.colorize("&7␣␣␣ &f/pstest updategroups <player> <primary> [groups...] &7- Update groups"));
        sender.sendMessage(ChatFormat.colorize("&7␣␣␣ &f/pstest syncgroups [player] &7- Sync LuckPerms groups"));
        sender.sendMessage(ChatFormat.colorize("&7␣␣␣ &f/pstest create <player> <world> <x> <y> <z> &7- Create player"));
        sender.sendMessage(ChatFormat.colorize("&7␣␣␣ &f/pstest exists <uuid> &7- Check if player exists"));
    }
    
    private void handleAllPlayersCommand(CommandSender sender, String[] args) {
        sender.sendMessage(ChatFormat.colorize("&6⚙ Retrieving all players from database..."));
        
        try {
            PlayerService playerService = rvnkCore.getService(PlayerService.class);
            
            playerService.getAllPlayers()
                .thenAccept(allPlayers -> {
                    if (allPlayers.isEmpty()) {
                        sender.sendMessage(ChatFormat.colorize("&e⚠ No players found in database"));
                        return;
                    }
                    
                    sender.sendMessage(ChatFormat.colorize("&a✓ Found " + allPlayers.size() + " total player(s):"));
                    
                    for (PlayerDTO player : allPlayers) {
                        String status = Bukkit.getPlayer(player.getId()) != null ? "&aonline" : "&7offline";
                        sender.sendMessage(ChatFormat.colorize("&7   • &f" + player.getCurrentName() + 
                                         " &7(" + status + "&7, UUID: " + player.getId() + ")"));
                        sender.sendMessage(ChatFormat.colorize("&7     Groups: " + player.getGroups() + 
                                         ", World: " + player.getCurrentWorld()));
                    }
                })
                .exceptionally(throwable -> {
                    sender.sendMessage(ChatFormat.colorize("&c✖ Error retrieving all players: " + throwable.getMessage()));
                    return null;
                });
                
        } catch (Exception e) {
            sender.sendMessage(ChatFormat.colorize("&c✖ Failed to get PlayerService: " + e.getMessage()));
        }
    }
    
    private void handleOnlineCommand(CommandSender sender, String[] args) {
        sender.sendMessage(ChatFormat.colorize("&6⚙ Retrieving online players..."));
        
        // Get online players directly from Bukkit (like the REST API does)
        Collection<? extends Player> onlinePlayers = Bukkit.getOnlinePlayers();
        
        if (onlinePlayers.isEmpty()) {
            sender.sendMessage(ChatFormat.colorize("&e⚠ No players currently online"));
            return;
        }
        
        sender.sendMessage(ChatFormat.colorize("&a✓ Found " + onlinePlayers.size() + " online player(s):"));
        
        for (Player player : onlinePlayers) {
            sender.sendMessage(ChatFormat.colorize("&7   • &f" + player.getName() + 
                             " &7(UUID: " + player.getUniqueId() + ")"));
            sender.sendMessage(ChatFormat.colorize("&7     World: " + player.getWorld().getName() + 
                             ", Location: " + (int)player.getLocation().getX() + 
                             ", " + (int)player.getLocation().getY() + 
                             ", " + (int)player.getLocation().getZ()));
        }
    }
    
    private void handleUuidCommand(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatFormat.colorize("&c▶ Usage: /pstest uuid <uuid>"));
            return;
        }
        
        UUID playerId;
        try {
            playerId = UUID.fromString(args[1]);
        } catch (IllegalArgumentException e) {
            sender.sendMessage(ChatFormat.colorize("&c✖ Invalid UUID format: " + args[1]));
            return;
        }
        
        sender.sendMessage(ChatFormat.colorize("&6⚙ Retrieving player by UUID: " + playerId + "..."));
        
        try {
            PlayerService playerService = rvnkCore.getService(PlayerService.class);
            
            playerService.getPlayer(playerId)
                .thenAccept(playerOpt -> {
                    if (playerOpt.isEmpty()) {
                        sender.sendMessage(ChatFormat.colorize("&e⚠ Player not found with UUID: " + playerId));
                        return;
                    }
                    
                    PlayerDTO player = playerOpt.get();
                    String status = Bukkit.getPlayer(player.getId()) != null ? "&aonline" : "&7offline";
                    sender.sendMessage(ChatFormat.colorize("&a✓ Found player:"));
                    sender.sendMessage(ChatFormat.colorize("&7   • &fName: " + player.getCurrentName() + " " + status));
                    sender.sendMessage(ChatFormat.colorize("&7   • &fUUID: " + player.getId()));
                    sender.sendMessage(ChatFormat.colorize("&7   • &fGroups: " + player.getGroups()));
                    sender.sendMessage(ChatFormat.colorize("&7   • &fWorld: " + player.getCurrentWorld()));
                    sender.sendMessage(ChatFormat.colorize("&7   • &fJoins: " + player.getTimesJoined()));
                    sender.sendMessage(ChatFormat.colorize("&7   • &fLast seen: " + player.getLastSeen()));
                })
                .exceptionally(throwable -> {
                    sender.sendMessage(ChatFormat.colorize("&c✖ Error retrieving player by UUID: " + throwable.getMessage()));
                    return null;
                });
                
        } catch (Exception e) {
            sender.sendMessage(ChatFormat.colorize("&c✖ Failed to get PlayerService: " + e.getMessage()));
        }
    }
    
    private void handleNameCommand(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatFormat.colorize("&c▶ Usage: /pstest name <playername>"));
            return;
        }
        
        String playerName = args[1];
        sender.sendMessage(ChatFormat.colorize("&6⚙ Retrieving player by name: " + playerName + "..."));
        
        try {
            PlayerService playerService = rvnkCore.getService(PlayerService.class);
            
            playerService.getPlayerByName(playerName)
                .thenAccept(playerOpt -> {
                    if (playerOpt.isEmpty()) {
                        sender.sendMessage(ChatFormat.colorize("&e⚠ Player not found with name: " + playerName));
                        return;
                    }
                    
                    PlayerDTO player = playerOpt.get();
                    String status = Bukkit.getPlayer(player.getId()) != null ? "&aonline" : "&7offline";
                    sender.sendMessage(ChatFormat.colorize("&a✓ Found player:"));
                    sender.sendMessage(ChatFormat.colorize("&7   • &fName: " + player.getCurrentName() + " " + status));
                    sender.sendMessage(ChatFormat.colorize("&7   • &fUUID: " + player.getId()));
                    sender.sendMessage(ChatFormat.colorize("&7   • &fGroups: " + player.getGroups()));
                    sender.sendMessage(ChatFormat.colorize("&7   • &fWorld: " + player.getCurrentWorld()));
                })
                .exceptionally(throwable -> {
                    sender.sendMessage(ChatFormat.colorize("&c✖ Error retrieving player by name: " + throwable.getMessage()));
                    return null;
                });
                
        } catch (Exception e) {
            sender.sendMessage(ChatFormat.colorize("&c✖ Failed to get PlayerService: " + e.getMessage()));
        }
    }
    
    private void handleSearchCommand(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatFormat.colorize("&c▶ Usage: /pstest search <pattern>"));
            return;
        }
        
        String searchPattern = args[1];
        sender.sendMessage(ChatFormat.colorize("&6⚙ Searching players with pattern: " + searchPattern + "..."));
        
        try {
            PlayerService playerService = rvnkCore.getService(PlayerService.class);
            
            playerService.searchPlayersByName(searchPattern)
                .thenAccept(foundPlayers -> {
                    if (foundPlayers.isEmpty()) {
                        sender.sendMessage(ChatFormat.colorize("&e⚠ No players found matching pattern: " + searchPattern));
                        return;
                    }
                    
                    sender.sendMessage(ChatFormat.colorize("&a✓ Found " + foundPlayers.size() + " player(s) matching '" + searchPattern + "':"));
                    
                    for (PlayerDTO player : foundPlayers) {
                        String status = Bukkit.getPlayer(player.getId()) != null ? "&aonline" : "&7offline";
                        sender.sendMessage(ChatFormat.colorize("&7   • &f" + player.getCurrentName() + 
                                         " &7(" + status + "&7, UUID: " + player.getId() + ")"));
                    }
                })
                .exceptionally(throwable -> {
                    sender.sendMessage(ChatFormat.colorize("&c✖ Error searching players: " + throwable.getMessage()));
                    return null;
                });
                
        } catch (Exception e) {
            sender.sendMessage(ChatFormat.colorize("&c✖ Failed to get PlayerService: " + e.getMessage()));
        }
    }
    
    private void handleCountCommand(CommandSender sender, String[] args) {
        sender.sendMessage(ChatFormat.colorize("&6⚙ Retrieving total player count..."));
        
        try {
            PlayerService playerService = rvnkCore.getService(PlayerService.class);
            
            playerService.getPlayerCount()
                .thenAccept(count -> {
                    sender.sendMessage(ChatFormat.colorize("&a✓ Total players in database: " + count));
                })
                .exceptionally(throwable -> {
                    sender.sendMessage(ChatFormat.colorize("&c✖ Error retrieving player count: " + throwable.getMessage()));
                    return null;
                });
                
        } catch (Exception e) {
            sender.sendMessage(ChatFormat.colorize("&c✖ Failed to get PlayerService: " + e.getMessage()));
        }
    }
    
    private void handleLocationCommand(CommandSender sender, String[] args) {
        if (args.length < 6) {
            sender.sendMessage(ChatFormat.colorize("&c▶ Usage: /pstest location <player> <world> <x> <y> <z>"));
            return;
        }
        
        Player targetPlayer = Bukkit.getPlayer(args[1]);
        if (targetPlayer == null) {
            sender.sendMessage(ChatFormat.colorize("&c✖ Player not found: " + args[1]));
            return;
        }
        
        String world = args[2];
        double x, y, z;
        
        try {
            x = Double.parseDouble(args[3]);
            y = Double.parseDouble(args[4]);
            z = Double.parseDouble(args[5]);
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatFormat.colorize("&c✖ Invalid coordinates"));
            return;
        }
        
        UUID playerId = targetPlayer.getUniqueId();
        sender.sendMessage(ChatFormat.colorize("&6⚙ Updating location for " + targetPlayer.getName() + "..."));
        
        try {
            PlayerService playerService = rvnkCore.getService(PlayerService.class);
            
            playerService.updatePlayerLocation(playerId, world, x, y, z)
                .thenRun(() -> {
                    sender.sendMessage(ChatFormat.colorize("&a✓ Successfully updated location for " + targetPlayer.getName()));
                    sender.sendMessage(ChatFormat.colorize("&7   World: " + world + ", X: " + x + ", Y: " + y + ", Z: " + z));
                })
                .exceptionally(throwable -> {
                    sender.sendMessage(ChatFormat.colorize("&c✖ Error updating location: " + throwable.getMessage()));
                    return null;
                });
                
        } catch (Exception e) {
            sender.sendMessage(ChatFormat.colorize("&c✖ Failed to get PlayerService: " + e.getMessage()));
        }
    }
    
    private void handleUpdateGroupsCommand(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(ChatFormat.colorize("&c▶ Usage: /pstest updategroups <player> <primary> [additional_groups...]"));
            return;
        }
        
        Player targetPlayer = Bukkit.getPlayer(args[1]);
        if (targetPlayer == null) {
            sender.sendMessage(ChatFormat.colorize("&c✖ Player not found: " + args[1]));
            return;
        }
        
        String primaryGroup = args[2];
        List<String> allGroups = new ArrayList<>();
        allGroups.add(primaryGroup);
        
        // Add additional groups if provided
        for (int i = 3; i < args.length; i++) {
            allGroups.add(args[i]);
        }
        
        UUID playerId = targetPlayer.getUniqueId();
        sender.sendMessage(ChatFormat.colorize("&6⚙ Updating groups for " + targetPlayer.getName() + "..."));
        
        try {
            PlayerService playerService = rvnkCore.getService(PlayerService.class);
            
            playerService.updatePlayerGroups(playerId, primaryGroup, allGroups)
                .thenRun(() -> {
                    sender.sendMessage(ChatFormat.colorize("&a✓ Successfully updated groups for " + targetPlayer.getName()));
                    sender.sendMessage(ChatFormat.colorize("&7   Primary: " + primaryGroup + ", All: " + allGroups));
                })
                .exceptionally(throwable -> {
                    sender.sendMessage(ChatFormat.colorize("&c✖ Error updating groups: " + throwable.getMessage()));
                    return null;
                });
                
        } catch (Exception e) {
            sender.sendMessage(ChatFormat.colorize("&c✖ Failed to get PlayerService: " + e.getMessage()));
        }
    }
    
    private void handleCreateCommand(CommandSender sender, String[] args) {
        if (args.length < 6) {
            sender.sendMessage(ChatFormat.colorize("&c▶ Usage: /pstest create <player> <world> <x> <y> <z>"));
            return;
        }
        
        Player targetPlayer = Bukkit.getPlayer(args[1]);
        if (targetPlayer == null) {
            sender.sendMessage(ChatFormat.colorize("&c✖ Player not found: " + args[1]));
            return;
        }
        
        String world = args[2];
        double x, y, z;
        
        try {
            x = Double.parseDouble(args[3]);
            y = Double.parseDouble(args[4]);
            z = Double.parseDouble(args[5]);
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatFormat.colorize("&c✖ Invalid coordinates"));
            return;
        }
        
        UUID playerId = targetPlayer.getUniqueId();
        String playerName = targetPlayer.getName();
        sender.sendMessage(ChatFormat.colorize("&6⚙ Creating player record for " + playerName + "..."));
        
        try {
            PlayerService playerService = rvnkCore.getService(PlayerService.class);
            
            playerService.createPlayer(playerId, playerName, world, x, y, z)
                .thenAccept(createdPlayer -> {
                    sender.sendMessage(ChatFormat.colorize("&a✓ Successfully created player record:"));
                    sender.sendMessage(ChatFormat.colorize("&7   Name: " + createdPlayer.getCurrentName()));
                    sender.sendMessage(ChatFormat.colorize("&7   UUID: " + createdPlayer.getId()));
                    sender.sendMessage(ChatFormat.colorize("&7   World: " + createdPlayer.getCurrentWorld()));
                })
                .exceptionally(throwable -> {
                    sender.sendMessage(ChatFormat.colorize("&c✖ Error creating player: " + throwable.getMessage()));
                    return null;
                });
                
        } catch (Exception e) {
            sender.sendMessage(ChatFormat.colorize("&c✖ Failed to get PlayerService: " + e.getMessage()));
        }
    }
    
    private void handleExistsCommand(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatFormat.colorize("&c▶ Usage: /pstest exists <uuid>"));
            return;
        }
        
        UUID playerId;
        try {
            playerId = UUID.fromString(args[1]);
        } catch (IllegalArgumentException e) {
            sender.sendMessage(ChatFormat.colorize("&c✖ Invalid UUID format: " + args[1]));
            return;
        }
        
        sender.sendMessage(ChatFormat.colorize("&6⚙ Checking if player exists: " + playerId + "..."));
        
        try {
            PlayerService playerService = rvnkCore.getService(PlayerService.class);
            
            playerService.playerExists(playerId)
                .thenAccept(exists -> {
                    if (exists) {
                        sender.sendMessage(ChatFormat.colorize("&a✓ Player exists in database"));
                    } else {
                        sender.sendMessage(ChatFormat.colorize("&e⚠ Player does not exist in database"));
                    }
                })
                .exceptionally(throwable -> {
                    sender.sendMessage(ChatFormat.colorize("&c✖ Error checking player existence: " + throwable.getMessage()));
                    return null;
                });
                
        } catch (Exception e) {
            sender.sendMessage(ChatFormat.colorize("&c✖ Failed to get PlayerService: " + e.getMessage()));
        }
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
            return Arrays.asList("all", "recent", "online", "uuid", "name", "groups", "search", 
                               "count", "location", "updategroups", "syncgroups", "create", "exists");
        } else if (args.length == 2) {
            switch (args[0].toLowerCase()) {
                case "recent":
                    return Arrays.asList("1", "6", "12", "24", "72", "168");
                case "groups":
                    return Arrays.asList("default", "vip", "admin", "staff");
                case "uuid":
                case "exists":
                    return Arrays.asList("94c37976-5134-40b0-9e03-722ae6664fea"); // Example UUID
                case "name":
                case "search":
                    return Arrays.asList("wizard", "admin", "player");
                case "location":
                case "updategroups":
                case "syncgroups":
                case "create":
                    return null; // Return null to show online player names
            }
        } else if (args.length == 3) {
            switch (args[0].toLowerCase()) {
                case "location":
                case "create":
                    return Arrays.asList("world", "world_nether", "world_the_end");
                case "updategroups":
                    return Arrays.asList("default", "vip", "admin", "staff");
            }
        } else if (args.length >= 4 && args.length <= 6) {
            switch (args[0].toLowerCase()) {
                case "location":
                case "create":
                    return Arrays.asList("0", "64", "100", "-100");
            }
        } else if (args.length >= 4) {
            switch (args[0].toLowerCase()) {
                case "updategroups":
                    return Arrays.asList("vip", "admin", "staff", "builder");
            }
        }
        
        return Collections.emptyList();
    }
}
