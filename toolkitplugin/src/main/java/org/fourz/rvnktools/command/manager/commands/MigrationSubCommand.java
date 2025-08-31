package org.fourz.rvnktools.command.manager.commands;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.fourz.rvnkcore.api.service.AnnouncementService;
import org.fourz.rvnktools.command.manager.BaseSubCommand;
import org.fourz.rvnktools.command.manager.RVNKCommand;
import org.fourz.rvnktools.migration.MigrationOrchestrator;
import org.fourz.rvnktools.util.log.LogManager;
import org.fourz.rvnktools.RVNKTools;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Migration SubCommand for Phase 1 Migration Framework
 * 
 * Provides command-line interface for YAML-to-Database migration operations:
 * - migrate dry-run: Validate migration without database changes
 * - migrate execute: Perform full migration to RVNKCore database
 * - migrate status: Show current migration status and progress
 * - migrate backup: Manage migration backups
 * 
 * @since Phase 1 Migration Framework
 */
public class MigrationSubCommand extends BaseSubCommand {
    
    private final LogManager logger;
    private MigrationOrchestrator migrationOrchestrator;
    
    public MigrationSubCommand(RVNKTools plugin, RVNKCommand parent) {
        super(plugin, parent, "migration",
              "Manage YAML-to-Database migration operations",
              "/rvnktools migration <dry-run|execute|status|backup> [options]",
              "rvnktools.admin.migration", false);
        this.logger = LogManager.getInstance(plugin);
    }
    
    @Override
    protected boolean executeSubCommand(CommandSender sender, String[] args) {
        if (args.length < 1) {
            sender.sendMessage("§cUsage: " + getUsage());
            showHelp(sender);
            return true;
        }
        
        String subAction = args[0].toLowerCase();
        
        switch (subAction) {
            case "dry-run":
            case "dryrun":
                return executeDryRun(sender, args);
                
            case "execute":
            case "run":
                return executeMigration(sender, args);
                
            case "status":
                return showStatus(sender, args);
                
            case "backup":
                return manageBackup(sender, args);
                
            case "help":
            case "?":
                showHelp(sender);
                return true;
                
            default:
                sender.sendMessage("§cUnknown migration action: " + subAction);
                showHelp(sender);
                return true;
        }
    }
    
    /**
     * Execute dry-run migration (validation only)
     */
    private boolean executeDryRun(CommandSender sender, String[] args) {
        sender.sendMessage("§e[Migration] Starting dry-run validation...");
        sender.sendMessage("§7This will validate the migration without making any database changes.");
        
        try {
            MigrationOrchestrator orchestrator = getOrchestrator();
            
            CompletableFuture<MigrationOrchestrator.MigrationResult> future = 
                orchestrator.executeMigration(true); // dry-run = true
            
            // Run asynchronously to prevent blocking
            future.whenComplete((result, throwable) -> {
                if (throwable != null) {
                    sender.sendMessage("§c[Migration] Dry-run failed: " + throwable.getMessage());
                    logger.error("Migration dry-run failed", throwable);
                    return;
                }
                
                if (result.isSuccess()) {
                    sender.sendMessage("§a[Migration] ✓ Dry-run completed successfully!");
                    
                    // Show summary
                    if (result.getTransformResult() != null) {
                        var summary = result.getTransformResult().getSummary();
                        sender.sendMessage("§7- Types: " + summary.getTypesTransformed());
                        sender.sendMessage("§7- Announcements: " + summary.getAnnouncementsTransformed());
                        
                        if (summary.hasIssues()) {
                            sender.sendMessage("§e- Warnings: " + summary.getValidationIssues().size());
                            sender.sendMessage("§7Use '/rvnktools migration status' for detailed issues");
                        }
                    }
                    
                    sender.sendMessage("§a[Migration] Ready for actual migration! Use '/rvnktools migration execute'");
                    
                } else {
                    sender.sendMessage("§c[Migration] ✗ Dry-run failed: " + result.getErrorMessage());
                    sender.sendMessage("§7Check console for detailed error information.");
                }
            });
            
            sender.sendMessage("§e[Migration] Dry-run started in background. You will be notified when complete.");
            
        } catch (Exception e) {
            sender.sendMessage("§c[Migration] Failed to start dry-run: " + e.getMessage());
            logger.error("Failed to start migration dry-run", e);
        }
        
        return true;
    }
    
    /**
     * Execute full migration with database changes
     */
    private boolean executeMigration(CommandSender sender, String[] args) {
        // Safety check - require confirmation for players
        if (sender instanceof Player && !hasConfirmation(args)) {
            sender.sendMessage("§c[Migration] ⚠ WARNING: This will migrate YAML data to RVNKCore database!");
            sender.sendMessage("§c[Migration] This operation will modify your announcement system.");
            sender.sendMessage("§e[Migration] Add '--confirm' to proceed: /rvnktools migration execute --confirm");
            return true;
        }
        
        sender.sendMessage("§e[Migration] Starting full migration from YAML to RVNKCore database...");
        sender.sendMessage("§7Creating backup and migrating data. This may take a moment.");
        
        try {
            MigrationOrchestrator orchestrator = getOrchestrator();
            
            CompletableFuture<MigrationOrchestrator.MigrationResult> future = 
                orchestrator.executeMigration(false); // dry-run = false
            
            future.whenComplete((result, throwable) -> {
                if (throwable != null) {
                    sender.sendMessage("§c[Migration] ✗ Migration failed: " + throwable.getMessage());
                    logger.error("Migration execution failed", throwable);
                    return;
                }
                
                if (result.isSuccess()) {
                    sender.sendMessage("§a[Migration] ✓ Migration completed successfully!");
                    
                    if (result.getDatabaseResult() != null) {
                        var dbResult = result.getDatabaseResult();
                        sender.sendMessage("§7- Announcements migrated: " + dbResult.getAnnouncementsCreated());
                        
                        if (!dbResult.getErrors().isEmpty()) {
                            sender.sendMessage("§e- Warnings: " + dbResult.getErrors().size());
                        }
                    }
                    
                    sender.sendMessage("§a[Migration] Your announcement system has been successfully migrated to RVNKCore!");
                    
                } else {
                    sender.sendMessage("§c[Migration] ✗ Migration failed: " + result.getErrorMessage());
                    
                    if (result.getStatus() == MigrationOrchestrator.MigrationStatus.ROLLED_BACK) {
                        sender.sendMessage("§e[Migration] System has been rolled back to original state.");
                    } else if (result.getStatus() == MigrationOrchestrator.MigrationStatus.FAILED_WITH_ROLLBACK_FAILURE) {
                        sender.sendMessage("§c[Migration] ⚠ CRITICAL: Rollback failed! Check backups manually.");
                    }
                    
                    sender.sendMessage("§7Check console for detailed error information.");
                }
            });
            
            sender.sendMessage("§e[Migration] Migration started in background. You will be notified when complete.");
            
        } catch (Exception e) {
            sender.sendMessage("§c[Migration] Failed to start migration: " + e.getMessage());
            logger.error("Failed to start migration execution", e);
        }
        
        return true;
    }
    
    /**
     * Show current migration status
     */
    private boolean showStatus(CommandSender sender, String[] args) {
        sender.sendMessage("§e[Migration] Current Status:");
        
        try {
            MigrationOrchestrator orchestrator = getOrchestrator();
            
            sender.sendMessage("§7- Status: " + orchestrator.getCurrentStatus());
            sender.sendMessage("§7- Progress: " + orchestrator.getCurrentStep() + "/" + orchestrator.getTotalSteps());
            
            MigrationOrchestrator.MigrationResult lastResult = orchestrator.getLastMigrationResult();
            if (lastResult != null) {
                sender.sendMessage("§7- Last Operation: " + (lastResult.isSuccess() ? "§aSuccess" : "§cFailed"));
                
                if (!lastResult.isSuccess() && lastResult.getErrorMessage() != null) {
                    sender.sendMessage("§7- Last Error: §c" + lastResult.getErrorMessage());
                }
            }
            
            // Show recent progress entries
            List<String> recentProgress = orchestrator.getProgressLog();
            if (!recentProgress.isEmpty()) {
                sender.sendMessage("§7- Recent Activity:");
                int showCount = Math.min(3, recentProgress.size());
                for (int i = recentProgress.size() - showCount; i < recentProgress.size(); i++) {
                    sender.sendMessage("§8  " + recentProgress.get(i));
                }
            }
            
            // Show errors if any
            List<String> errors = orchestrator.getErrorLog();
            if (!errors.isEmpty()) {
                sender.sendMessage("§7- Recent Errors: §c" + errors.size());
                if (args.length > 1 && args[1].equalsIgnoreCase("--errors")) {
                    for (String error : errors) {
                        sender.sendMessage("§c  " + error);
                    }
                } else {
                    sender.sendMessage("§7  Use '--errors' to show detailed error list");
                }
            }
            
        } catch (Exception e) {
            sender.sendMessage("§c[Migration] Failed to get status: " + e.getMessage());
        }
        
        return true;
    }
    
    /**
     * Manage migration backups
     */
    private boolean manageBackup(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§e[Migration] Backup Management:");
            sender.sendMessage("§7/rvnktools migration backup list - List all backups");
            sender.sendMessage("§7/rvnktools migration backup cleanup <count> - Keep only N newest backups");
            return true;
        }
        
        String backupAction = args[1].toLowerCase();
        
        switch (backupAction) {
            case "list":
                return listBackups(sender);
                
            case "cleanup":
                if (args.length < 3) {
                    sender.sendMessage("§cUsage: /rvnktools migration backup cleanup <max_backups>");
                    return true;
                }
                try {
                    int maxBackups = Integer.parseInt(args[2]);
                    return cleanupBackups(sender, maxBackups);
                } catch (NumberFormatException e) {
                    sender.sendMessage("§cInvalid number: " + args[2]);
                    return true;
                }
                
            default:
                sender.sendMessage("§cUnknown backup action: " + backupAction);
                return true;
        }
    }
    
    /**
     * List available backups
     */
    private boolean listBackups(CommandSender sender) {
        sender.sendMessage("§e[Migration] Listing migration backups...");
        
        try {
            // Note: BackupRollbackManager is part of the orchestrator
            // In a full implementation, we'd expose backup listing through the orchestrator
            sender.sendMessage("§7Backup listing functionality will be available in the orchestrator interface.");
            
        } catch (Exception e) {
            sender.sendMessage("§c[Migration] Failed to list backups: " + e.getMessage());
        }
        
        return true;
    }
    
    /**
     * Cleanup old backups
     */
    private boolean cleanupBackups(CommandSender sender, int maxBackups) {
        sender.sendMessage("§e[Migration] Cleaning up backups (keeping " + maxBackups + " newest)...");
        
        try {
            // Backup cleanup would be implemented through the orchestrator
            sender.sendMessage("§7Backup cleanup functionality will be available in the orchestrator interface.");
            
        } catch (Exception e) {
            sender.sendMessage("§c[Migration] Failed to cleanup backups: " + e.getMessage());
        }
        
        return true;
    }
    
    /**
     * Show help information
     */
    private void showHelp(CommandSender sender) {
        sender.sendMessage("§e[Migration] YAML-to-Database Migration Commands:");
        sender.sendMessage("§7/rvnktools migration dry-run - Validate migration (no changes)");
        sender.sendMessage("§7/rvnktools migration execute [--confirm] - Perform full migration");
        sender.sendMessage("§7/rvnktools migration status [--errors] - Show current status");
        sender.sendMessage("§7/rvnktools migration backup <list|cleanup> - Manage backups");
        sender.sendMessage("§7/rvnktools migration help - Show this help");
        sender.sendMessage("");
        sender.sendMessage("§e[Migration] Process Overview:");
        sender.sendMessage("§71. Run 'dry-run' to validate your YAML data");
        sender.sendMessage("§72. Run 'execute --confirm' to migrate to database");
        sender.sendMessage("§73. Use 'status' to monitor progress");
        sender.sendMessage("");
        sender.sendMessage("§c[Migration] Important: Always run dry-run first!");
    }
    
    /**
     * Get or create migration orchestrator
     */
    private MigrationOrchestrator getOrchestrator() throws Exception {
        if (migrationOrchestrator == null) {
            // Get AnnouncementService from RVNKCore
            org.fourz.rvnkcore.RVNKCore rvnkCore = org.fourz.rvnkcore.RVNKCore.getInstance();
            if (rvnkCore == null) {
                throw new IllegalStateException("RVNKCore not available - is RVNKCore initialized?");
            }
            
            AnnouncementService announcementService = rvnkCore.getAnnouncementService();
            if (announcementService == null) {
                throw new IllegalStateException("AnnouncementService not available - is RVNKCore properly configured?");
            }
            
            migrationOrchestrator = new MigrationOrchestrator(plugin, announcementService);
        }
        
        return migrationOrchestrator;
    }
    
    /**
     * Check if confirmation flag is present
     */
    private boolean hasConfirmation(String[] args) {
        for (String arg : args) {
            if (arg.equalsIgnoreCase("--confirm") || arg.equalsIgnoreCase("-c")) {
                return true;
            }
        }
        return false;
    }
    
    @Override
    public List<String> getTabCompletions(CommandSender sender, String[] args) {
        List<String> completions = new ArrayList<>();
        
        if (args.length == 1) {
            completions.addAll(Arrays.asList("dry-run", "execute", "status", "backup", "help"));
        } else if (args.length == 2) {
            String subAction = args[0].toLowerCase();
            if ("backup".equals(subAction)) {
                completions.addAll(Arrays.asList("list", "cleanup"));
            } else if ("execute".equals(subAction)) {
                completions.add("--confirm");
            } else if ("status".equals(subAction)) {
                completions.add("--errors");
            }
        } else if (args.length == 3 && "cleanup".equals(args[1].toLowerCase())) {
            completions.addAll(Arrays.asList("3", "5", "10"));
        }
        
        return completions;
    }
    
    @Override
    public boolean hasPermission(CommandSender sender) {
        return sender.hasPermission("rvnktools.admin.migration") || sender.isOp();
    }
}
