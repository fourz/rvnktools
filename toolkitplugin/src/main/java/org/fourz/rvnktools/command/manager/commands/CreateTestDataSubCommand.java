package org.fourz.rvnktools.command.manager.commands;

import org.bukkit.command.CommandSender;
import org.fourz.rvnkcore.RVNKCore;
import org.fourz.rvnktools.command.manager.BaseCommand;
import org.fourz.rvnktools.command.manager.BaseSubCommand;
import org.fourz.rvnktools.util.AnnouncementTestService;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Subcommand for creating test data using the modern RVNKCore announcement API.
 * This is used for API testing and validation purposes.
 *
 * Usage: /rvnktools createtestdata [types|announcements|all]
 */
public class CreateTestDataSubCommand extends BaseSubCommand {

    private AnnouncementTestService testService;
    
    public CreateTestDataSubCommand(RVNKCore plugin, BaseCommand parent) {
        super(plugin, parent, "createtestdata", 
              "Create test data for API validation", 
              "/rvnktools createtestdata <types|announcements|all|single|status>",
              "rvnktools.command.createtestdata", false);
    }
    
    @Override
    protected boolean executeSubCommand(CommandSender sender, String[] args) {
        // Initialize test service if not already done
        if (testService == null) {
            RVNKCore rvnkCore = RVNKCore.getInstance();
            if (rvnkCore == null) {
                sender.sendMessage("§c✖ RVNKCore is not available. Cannot create test data.");
                return true;
            }
            testService = new AnnouncementTestService(plugin, rvnkCore);
        }
        
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }
        
        String operation = args[0].toLowerCase();
        
        switch (operation) {
            case "types":
                createTestTypes(sender);
                break;
            case "announcements":
                createTestAnnouncements(sender);
                break;
            case "all":
                createAllTestData(sender);
                break;
            case "single":
                if (args.length < 4) {
                    sender.sendMessage("§c▶ Usage: /rvnktools createtestdata single <id> <type> <message>");
                    return true;
                }
                createSingleAnnouncement(sender, args[1], args[2], String.join(" ", Arrays.copyOfRange(args, 3, args.length)));
                break;
            case "status":
                showStatus(sender);
                break;
            default:
                sendHelp(sender);
                break;
        }
        return true;
    }
    
    private void createTestTypes(CommandSender sender) {
        sender.sendMessage("§6⚙ Creating default announcement types...");
        
        testService.createDefaultAnnouncementTypes().whenComplete((count, throwable) -> {
            if (throwable != null) {
                sender.sendMessage("§c✖ Failed to create announcement types: " + throwable.getMessage());
                logger.warning("Failed to create announcement types: " + throwable.getMessage());
            } else {
                sender.sendMessage("§a✓ Created " + count + " announcement types");
                logger.info("Created " + count + " announcement types for testing");
            }
        });
    }
    
    private void createTestAnnouncements(CommandSender sender) {
        sender.sendMessage("§6⚙ Creating test announcements...");
        
        testService.createTestAnnouncements().whenComplete((count, throwable) -> {
            if (throwable != null) {
                sender.sendMessage("§c✖ Failed to create test announcements: " + throwable.getMessage());
                logger.warning("Failed to create test announcements: " + throwable.getMessage());
            } else {
                sender.sendMessage("§a✓ Created " + count + " test announcements");
                logger.info("Created " + count + " test announcements for API testing");
            }
        });
    }
    
    private void createAllTestData(CommandSender sender) {
        sender.sendMessage("§6⚙ Creating all test data...");
        
        CompletableFuture<Integer> typeFuture = testService.createDefaultAnnouncementTypes();
        CompletableFuture<Integer> announcementFuture = testService.createTestAnnouncements();
        
        CompletableFuture.allOf(typeFuture, announcementFuture).whenComplete((unused, throwable) -> {
            if (throwable != null) {
                sender.sendMessage("§c✖ Failed to create test data: " + throwable.getMessage());
                logger.warning("Failed to create test data: " + throwable.getMessage());
            } else {
                try {
                    int typeCount = typeFuture.get();
                    int announcementCount = announcementFuture.get();
                    sender.sendMessage("§a✓ Created " + typeCount + " types and " + announcementCount + " announcements");
                    logger.info("Created test data: " + typeCount + " types and " + announcementCount + " announcements");
                } catch (Exception e) {
                    sender.sendMessage("§c✖ Error getting results: " + e.getMessage());
                    logger.warning("Error getting test data results: " + e.getMessage());
                }
            }
        });
    }
    
    private void createSingleAnnouncement(CommandSender sender, String id, String type, String message) {
        sender.sendMessage("§6⚙ Creating announcement: " + id);
        
        testService.createAnnouncement(id, message, type, true).whenComplete((success, throwable) -> {
            if (throwable != null) {
                sender.sendMessage("§c✖ Failed to create announcement: " + throwable.getMessage());
                logger.warning("Failed to create announcement '" + id + "': " + throwable.getMessage());
            } else if (success) {
                sender.sendMessage("§a✓ Created announcement: " + id);
                logger.info("Created test announcement: " + id);
            } else {
                sender.sendMessage("§c✖ Failed to create announcement: " + id);
                logger.warning("Failed to create announcement: " + id + " (unknown reason)");
            }
        });
    }
    
    private void showStatus(CommandSender sender) {
        sender.sendMessage("§6⚙ Current announcement status:");
        
        List<String> types = testService.getAvailableTypes();
        List<String> announcements = testService.getCurrentAnnouncements();
        
        sender.sendMessage("§7   Available types: " + types.size() + " (" + String.join(", ", types) + ")");
        sender.sendMessage("§7   Current announcements: " + announcements.size() + " (" + String.join(", ", announcements) + ")");
    }
    
    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§c▶ Usage: /rvnktools createtestdata <types|announcements|all|single|status>");
        sender.sendMessage("§7   types - Create default announcement types");
        sender.sendMessage("§7   announcements - Create test announcements");
        sender.sendMessage("§7   all - Create both types and announcements");
        sender.sendMessage("§7   single <id> <type> <message> - Create a single announcement");
        sender.sendMessage("§7   status - Show current announcement status");
    }
    
    @Override
    protected List<String> getTabCompletions(CommandSender sender, String[] args) {
        if (args.length == 1) {
            List<String> completions = Arrays.asList("types", "announcements", "all", "single", "status");
            return completions.stream()
                .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                .collect(java.util.stream.Collectors.toList());
        }
        
        if (args.length == 3 && args[0].equalsIgnoreCase("single")) {
            // Initialize test service if not already done
            if (testService == null) {
                RVNKCore rvnkCore = RVNKCore.getInstance();
                if (rvnkCore == null) {
                    return Collections.emptyList();
                }
                testService = new AnnouncementTestService(plugin, rvnkCore);
            }
            List<String> types = testService.getAvailableTypes();
            return types.stream()
                .filter(s -> s.toLowerCase().startsWith(args[2].toLowerCase()))
                .collect(java.util.stream.Collectors.toList());
        }
        
        return Collections.emptyList();
    }
}
