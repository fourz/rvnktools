package org.fourz.rvnktools.dhlogfilter.command;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.fourz.rvnktools.RVNKTools;
import org.fourz.rvnktools.command.manager.BaseCommand;
import org.fourz.rvnktools.command.manager.BaseSubCommand;
import org.fourz.rvnktools.dhlogfilter.manager.DHLogFilterManager;
import org.fourz.rvnktools.dhlogfilter.model.FilterStats;
import org.fourz.rvnktools.dhlogfilter.service.DHLogFilterService;
import org.fourz.rvnktools.util.ChatFormat;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Command implementation for DH log filter management.
 * Provides administrative commands for controlling the log filtering system
 * using the RVNK CommandManager framework.
 * 
 * @since 1.1-alpha
 */
public class DHLogFilterCommand extends BaseCommand {
    
    private final DHLogFilterManager filterManager;
    
    /**
     * Constructor for DHLogFilterCommand.
     * 
     * @param plugin The RVNKTools plugin instance
     * @param filterManager The DH log filter manager
     */
    public DHLogFilterCommand(RVNKTools plugin, DHLogFilterManager filterManager) {
        super(plugin, "dhfilter", 
              "Manage DH log filtering to reduce console spam", 
              "/dhfilter <reload|status|level|cache> [args]",
              "rvnktools.command.dhfilter");
        
        this.filterManager = filterManager;
        
        // Register subcommands
        registerSubCommands();
    }
    
    /**
     * Register all subcommands for the DH filter command.
     */
    private void registerSubCommands() {
        registerSubCommand("reload", new ReloadSubCommand());
        registerSubCommand("status", new StatusSubCommand());
        registerSubCommand("level", new LevelSubCommand());
        registerSubCommand("cache", new CacheSubCommand());
        registerSubCommand("toggle", new ToggleSubCommand());
        registerSubCommand("stats", new StatsSubCommand());
    }
    
    @Override
    protected boolean executeCommand(CommandSender sender, String[] args) {
        // Default behavior shows help
        sendHelp(sender);
        return true;
    }
    
    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (!hasPermission(sender)) {
            return Collections.emptyList();
        }
        
        if (args.length == 1) {
            return getMatchingSubCommands(sender, args[0]);
        } else if (args.length == 2 && "level".equalsIgnoreCase(args[0])) {
            // Tab completion for log levels
            return Arrays.asList("DEBUG", "INFO", "WARN", "ERROR")
                .stream()
                .filter(level -> level.toLowerCase().startsWith(args[1].toLowerCase()))
                .toList();
        }
        
        return super.tabComplete(sender, args);
    }
    
    /**
     * Subcommand to reload the DH log filter configuration.
     */
    private class ReloadSubCommand extends BaseSubCommand {
        
        public ReloadSubCommand() {
            super(plugin, DHLogFilterCommand.this, "reload", 
                  "Reload the DH log filter configuration from disk", 
                  "/dhfilter reload", "rvnktools.command.dhfilter.reload", false);
        }
        
        @Override
        protected boolean executeSubCommand(CommandSender sender, String[] args) {
            sender.sendMessage(ChatFormat.format("&6⚙ Reloading DH log filter configuration..."));
            
            try {
                boolean success = filterManager.reloadConfiguration();
                
                if (success) {
                    sender.sendMessage(ChatFormat.format("&a✓ DH log filter configuration reloaded successfully"));
                } else {
                    sender.sendMessage(ChatFormat.format("&c✖ Failed to reload DH log filter configuration"));
                }
                
            } catch (Exception e) {
                sender.sendMessage(ChatFormat.format("&c✖ Error reloading configuration: " + e.getMessage()));
                logger.error("Error in dhfilter reload command", e);
            }
            
            return true;
        }
    }
    
    /**
     * Subcommand to show the current status of the DH log filter.
     */
    private class StatusSubCommand extends BaseSubCommand {
        
        public StatusSubCommand() {
            super(plugin, DHLogFilterCommand.this, "status", 
                  "Show the current status of the DH log filter", 
                  "/dhfilter status", "rvnktools.command.dhfilter.status", false);
        }
        
        @Override
        protected boolean executeSubCommand(CommandSender sender, String[] args) {
            if (!hasPermission(sender)) {
                sendNoPermissionMessage(sender);
                return true;
            }
            
            try {
                DHLogFilterService service = filterManager.getFilterService();
                
                boolean isActive = service.isFilterActive().join();
                String currentLevel = service.getCurrentLogLevel().join();
                
                sender.sendMessage(ChatFormat.format("&e=== DH Log Filter Status ==="));
                sender.sendMessage(ChatFormat.format("&7Status: " + (isActive ? "&aActive" : "&cInactive")));
                sender.sendMessage(ChatFormat.format("&7Current Level: &f" + currentLevel));
                
                // Show basic statistics
                FilterStats stats = service.getFilterStats().join();
                sender.sendMessage(ChatFormat.format("&7Messages Processed: &f" + stats.getMessagesProcessed()));
                sender.sendMessage(ChatFormat.format("&7Messages Filtered: &f" + stats.getMessagesFiltered()));
                sender.sendMessage(ChatFormat.format("&7Filtering Efficiency: &f" + String.format("%.1f%%", stats.getFilteringEfficiency())));
                
                sender.sendMessage(ChatFormat.format("&7␣␣␣ Use '/dhfilter stats' for detailed statistics"));
                
            } catch (Exception e) {
                sender.sendMessage(ChatFormat.format("&c✖ Error retrieving filter status: " + e.getMessage()));
                logger.error("Error in dhfilter status command", e);
            }
            
            return true;
        }
    }
    
    /**
     * Subcommand to change the log level temporarily.
     */
    private class LevelSubCommand extends BaseSubCommand {
        
        public LevelSubCommand() {
            super(plugin, DHLogFilterCommand.this, "level", 
                  "Change the log level temporarily", 
                  "/dhfilter level <DEBUG|INFO|WARN|ERROR>", "rvnktools.command.dhfilter.level", false);
        }
        
        @Override
        protected boolean executeSubCommand(CommandSender sender, String[] args) {
            if (!hasPermission(sender)) {
                sendNoPermissionMessage(sender);
                return true;
            }
            
            if (args.length < 1) {
                sender.sendMessage(ChatFormat.format("&c▶ Usage: /dhfilter level <DEBUG|INFO|WARN|ERROR>"));
                return true;
            }
            
            String level = args[0].toUpperCase();
            
            try {
                DHLogFilterService service = filterManager.getFilterService();
                service.setLogLevel(level).join();
                
                sender.sendMessage(ChatFormat.format("&a✓ DH log filter level changed to: " + level));
                sender.sendMessage(ChatFormat.format("&7␣␣␣ This change is temporary and will reset on server restart"));
                
            } catch (IllegalArgumentException e) {
                sender.sendMessage(ChatFormat.format("&c✖ Invalid log level: " + level));
                sender.sendMessage(ChatFormat.format("&7␣␣␣ Valid levels are: DEBUG, INFO, WARN, ERROR"));
            } catch (Exception e) {
                sender.sendMessage(ChatFormat.format("&c✖ Error changing log level: " + e.getMessage()));
                logger.error("Error in dhfilter level command", e);
            }
            
            return true;
        }
    }
    
    /**
     * Subcommand to manage the message cache.
     */
    private class CacheSubCommand extends BaseSubCommand {
        
        public CacheSubCommand() {
            super(plugin, DHLogFilterCommand.this, "cache", 
                  "Manage the message cache for rate limiting", 
                  "/dhfilter cache <clear>", "rvnktools.command.dhfilter.cache", false);
        }
        
        @Override
        protected boolean executeSubCommand(CommandSender sender, String[] args) {
            if (!hasPermission(sender)) {
                sendNoPermissionMessage(sender);
                return true;
            }
            
            if (args.length < 1) {
                sender.sendMessage(ChatFormat.format("&c▶ Usage: /dhfilter cache <clear>"));
                return true;
            }
            
            String action = args[0].toLowerCase();
            
            if ("clear".equals(action)) {
                try {
                    DHLogFilterService service = filterManager.getFilterService();
                    service.clearMessageCache().join();
                    
                    sender.sendMessage(ChatFormat.format("&a✓ DH log filter message cache cleared"));
                    
                } catch (Exception e) {
                    sender.sendMessage(ChatFormat.format("&c✖ Error clearing cache: " + e.getMessage()));
                    logger.error("Error in dhfilter cache clear command", e);
                }
            } else {
                sender.sendMessage(ChatFormat.format("&c✖ Unknown cache action: " + action));
                sender.sendMessage(ChatFormat.format("&7␣␣␣ Available actions: clear"));
            }
            
            return true;
        }
        
        @Override
        protected List<String> getTabCompletions(CommandSender sender, String[] args) {
            if (args.length == 1) {
                return List.of("clear");
            }
            return Collections.emptyList();
        }
    }
    
    /**
     * Subcommand to toggle the filter on/off.
     */
    private class ToggleSubCommand extends BaseSubCommand {
        
        public ToggleSubCommand() {
            super(plugin, DHLogFilterCommand.this, "toggle", 
                  "Toggle the DH log filter on or off", 
                  "/dhfilter toggle", "rvnktools.command.dhfilter.toggle", false);
        }
        
        @Override
        protected boolean executeSubCommand(CommandSender sender, String[] args) {
            if (!hasPermission(sender)) {
                sendNoPermissionMessage(sender);
                return true;
            }
            
            try {
                DHLogFilterService service = filterManager.getFilterService();
                boolean currentlyActive = service.isFilterActive().join();
                
                if (currentlyActive) {
                    filterManager.removeFilter();
                    sender.sendMessage(ChatFormat.format("&a✓ DH log filter disabled"));
                } else {
                    filterManager.applyFilter();
                    sender.sendMessage(ChatFormat.format("&a✓ DH log filter enabled"));
                }
                
            } catch (Exception e) {
                sender.sendMessage(ChatFormat.format("&c✖ Error toggling filter: " + e.getMessage()));
                logger.error("Error in dhfilter toggle command", e);
            }
            
            return true;
        }
    }
    
    /**
     * Subcommand to show detailed statistics.
     */
    private class StatsSubCommand extends BaseSubCommand {
        
        public StatsSubCommand() {
            super(plugin, DHLogFilterCommand.this, "stats", 
                  "Show detailed statistics about the DH log filter", 
                  "/dhfilter stats", "rvnktools.command.dhfilter.stats", false);
        }
        
        @Override
        protected boolean executeSubCommand(CommandSender sender, String[] args) {
            if (!hasPermission(sender)) {
                sendNoPermissionMessage(sender);
                return true;
            }
            
            try {
                DHLogFilterService service = filterManager.getFilterService();
                FilterStats stats = service.getFilterStats().join();
                
                sender.sendMessage(ChatFormat.format("&e=== DH Log Filter Statistics ==="));
                sender.sendMessage(ChatFormat.format("&7Start Time: &f" + stats.getStartTime()));
                sender.sendMessage(ChatFormat.format("&7Filter Active: " + (stats.isFilterActive() ? "&aYes" : "&cNo")));
                sender.sendMessage(ChatFormat.format("&7Current Level: &f" + stats.getCurrentLogLevel()));
                sender.sendMessage("");
                sender.sendMessage(ChatFormat.format("&7Messages Processed: &f" + stats.getMessagesProcessed()));
                sender.sendMessage(ChatFormat.format("&7Messages Filtered: &f" + stats.getMessagesFiltered()));
                sender.sendMessage(ChatFormat.format("&7Messages Allowed: &f" + stats.getMessagesAllowed()));
                sender.sendMessage(ChatFormat.format("&7Rate Limited: &f" + stats.getRateLimitedMessages()));
                sender.sendMessage("");
                sender.sendMessage(ChatFormat.format("&7Filtering Efficiency: &f" + String.format("%.1f%%", stats.getFilteringEfficiency())));
                sender.sendMessage(ChatFormat.format("&7Average Processing Time: &f" + String.format("%.3fms", stats.getAverageProcessingTimeMs())));
                sender.sendMessage(ChatFormat.format("&7Cache Size: &f" + stats.getCacheSize()));
                
            } catch (Exception e) {
                sender.sendMessage(ChatFormat.format("&c✖ Error retrieving statistics: " + e.getMessage()));
                logger.error("Error in dhfilter stats command", e);
            }
            
            return true;
        }
    }
}