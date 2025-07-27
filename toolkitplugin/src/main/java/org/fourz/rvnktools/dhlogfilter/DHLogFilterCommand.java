package org.fourz.rvnktools.dhlogfilter;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.fourz.rvnktools.RVNKTools;
import org.fourz.rvnktools.command.manager.BaseCommand;
import org.fourz.rvnktools.util.ChatFormat;

import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Command implementation for managing DH log filtering.
 * 
 * Provides subcommands for reloading configuration, checking status,
 * and temporarily adjusting log levels.
 */
public class DHLogFilterCommand extends BaseCommand {
    
    private final DHLogFilterService filterService;
    
    /**
     * Constructor for DHLogFilterCommand.
     * 
     * @param plugin The RVNKTools plugin instance
     * @param filterService The filter service instance
     */
    public DHLogFilterCommand(RVNKTools plugin, DHLogFilterService filterService) {
        super(plugin, "dhfilter", 
              "Manage DH log filtering to reduce console spam", 
              "/dhfilter <reload|status|level> [args]",
              "rvnktools.command.dhfilter");
        
        this.filterService = filterService;
        
        // Register subcommands
        registerSubCommand("reload", new ReloadSubCommand());
        registerSubCommand("status", new StatusSubCommand());
        registerSubCommand("level", new LevelSubCommand());
    }
    
    @Override
    protected boolean executeCommand(CommandSender sender, String[] args) {
        // Default behavior when no subcommand is provided
        sendHelp(sender);
        return true;
    }
    
    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return getMatchingSubCommands(sender, args[0]);
        } else if (args.length == 2 && "level".equalsIgnoreCase(args[0])) {
            // Tab completion for log levels
            return Arrays.stream(DHLogLevel.values())
                    .map(level -> level.getDisplayName().toLowerCase())
                    .filter(level -> level.startsWith(args[1].toLowerCase()))
                    .toList();
        }
        
        return Collections.emptyList();
    }
    
    /**
     * Subcommand for reloading configuration.
     */
    private class ReloadSubCommand extends org.fourz.rvnktools.command.manager.BaseSubCommand {
        
        public ReloadSubCommand() {
            super(plugin, DHLogFilterCommand.this, "reload", "Reload DH log filter configuration", "/dhfilter reload", false);
        }
        
        @Override
        protected boolean executeSubCommand(CommandSender sender, String[] args) {
            sender.sendMessage(ChatFormat.format("&6⚙ Reloading DH log filter configuration..."));
            
            filterService.reloadConfiguration()
                .thenAccept(success -> {
                    if (success) {
                        sender.sendMessage(ChatFormat.format("&a✓ Configuration reloaded successfully"));
                    } else {
                        sender.sendMessage(ChatFormat.format("&c✖ Failed to reload configuration"));
                    }
                })
                .exceptionally(ex -> {
                    sender.sendMessage(ChatFormat.format("&c✖ Error reloading configuration: " + ex.getMessage()));
                    logger.error("Failed to reload DH filter configuration", ex);
                    return null;
                });
            
            return true;
        }
    }
    
    /**
     * Subcommand for displaying filter status and statistics.
     */
    private class StatusSubCommand extends org.fourz.rvnktools.command.manager.BaseSubCommand {
        
        public StatusSubCommand() {
            super(plugin, DHLogFilterCommand.this, "status", "Show DH log filter status and statistics", "/dhfilter status", false);
        }
        
        @Override
        protected boolean executeSubCommand(CommandSender sender, String[] args) {
            sender.sendMessage(ChatFormat.format("&6⚙ Retrieving DH log filter status..."));
            
            filterService.getFilterStats()
                .thenAccept(stats -> displayStats(sender, stats))
                .exceptionally(ex -> {
                    sender.sendMessage(ChatFormat.format("&c✖ Error retrieving statistics: " + ex.getMessage()));
                    logger.error("Failed to retrieve DH filter statistics", ex);
                    return null;
                });
            
            return true;
        }
        
        private void displayStats(CommandSender sender, DHLogFilterStats stats) {
            sender.sendMessage(ChatColor.GOLD + "=== DH Log Filter Status ===");
            
            // Filter status
            String statusColor = stats.isFilterActive() ? "&a" : "&c";
            String statusText = stats.isFilterActive() ? "ACTIVE" : "INACTIVE";
            sender.sendMessage(ChatFormat.format("&eStatus: " + statusColor + statusText));
            
            if (stats.isFilterActive()) {
                sender.sendMessage(ChatFormat.format("&eLog Level: &f" + stats.getCurrentLevel().getDisplayName()));
                sender.sendMessage(ChatFormat.format("&eKeyword Rules: &f" + stats.getKeywordRulesActive()));
                
                // Statistics
                sender.sendMessage(ChatColor.YELLOW + "Statistics:");
                sender.sendMessage(ChatFormat.format("&7   Messages Filtered: &f" + stats.getMessagesFiltered()));
                sender.sendMessage(ChatFormat.format("&7   Messages Allowed: &f" + stats.getMessagesAllowed()));
                sender.sendMessage(ChatFormat.format("&7   Total Processed: &f" + stats.getTotalMessages()));
                sender.sendMessage(ChatFormat.format("&7   Filtering Efficiency: &f" + 
                    String.format("%.1f%%", stats.getFilteringEfficiency())));
                
                // Performance
                sender.sendMessage(ChatColor.YELLOW + "Performance:");
                sender.sendMessage(ChatFormat.format("&7   Cache Size: &f" + stats.getCacheSize()));
                sender.sendMessage(ChatFormat.format("&7   Avg Processing Time: &f" + 
                    String.format("%.3fms", stats.getAverageProcessingTimeMs())));
                
                // Timestamps
                if (stats.getFilterStartTime() != null) {
                    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                    sender.sendMessage(ChatFormat.format("&7   Filter Started: &f" + 
                        stats.getFilterStartTime().format(formatter)));
                }
                
                if (stats.getLastStatsReset() != null) {
                    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                    sender.sendMessage(ChatFormat.format("&7   Stats Reset: &f" + 
                        stats.getLastStatsReset().format(formatter)));
                }
            }
        }
    }
    
    /**
     * Subcommand for changing log level temporarily.
     */
    private class LevelSubCommand extends org.fourz.rvnktools.command.manager.BaseSubCommand {
        
        public LevelSubCommand() {
            super(plugin, DHLogFilterCommand.this, "level", "Change log level temporarily", "/dhfilter level <debug|info|warn|error>", false);
        }
        
        @Override
        protected boolean executeSubCommand(CommandSender sender, String[] args) {
            if (args.length < 1) {
                sender.sendMessage(ChatFormat.format("&c▶ Usage: /dhfilter level <debug|info|warn|error>"));
                return true;
            }
            
            try {
                DHLogLevel newLevel = DHLogLevel.fromString(args[0]);
                
                sender.sendMessage(ChatFormat.format("&6⚙ Setting log level to " + newLevel.getDisplayName() + "..."));
                
                filterService.setLogLevel(newLevel)
                    .thenRun(() -> {
                        sender.sendMessage(ChatFormat.format("&a✓ Log level set to " + newLevel.getDisplayName()));
                        sender.sendMessage(ChatFormat.format("&7␣␣␣ This is a temporary change. Use reload to restore configured level."));
                    })
                    .exceptionally(ex -> {
                        sender.sendMessage(ChatFormat.format("&c✖ Failed to set log level: " + ex.getMessage()));
                        logger.error("Failed to set DH filter log level", ex);
                        return null;
                    });
                
            } catch (IllegalArgumentException e) {
                sender.sendMessage(ChatFormat.format("&c✖ Invalid log level: " + args[0]));
                sender.sendMessage(ChatFormat.format("&7␣␣␣ Valid levels: debug, info, warn, error"));
            }
            
            return true;
        }
        
        @Override
        protected List<String> getTabCompletions(CommandSender sender, String[] args) {
            if (args.length == 1) {
                return Arrays.stream(DHLogLevel.values())
                        .map(level -> level.getDisplayName().toLowerCase())
                        .filter(level -> level.startsWith(args[0].toLowerCase()))
                        .toList();
            }
            return Collections.emptyList();
        }
    }
}