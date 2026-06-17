package org.fourz.rvnktools.logfilter;

import org.bukkit.command.CommandSender;
import org.fourz.rvnkcore.RVNKCore;
import org.fourz.rvnktools.command.manager.BaseCommand;
import org.fourz.rvnkcore.util.ChatFormat;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Command for managing the Log Filter feature.
 *
 * Provides subcommands for:
 * - Status checking
 * - Configuration management
 * - Runtime filter adjustments
 * - Keyword filter management
 *
 * Usage:
 * - /logfilter status - Show filter status and configuration
 * - /logfilter reload - Reload filter configuration
 * - /logfilter enable/disable - Enable or disable the filter
 * - /logfilter add <keyword> - Add keyword filter
 * - /logfilter remove <keyword> - Remove keyword filter
 * - /logfilter list - List all keyword filters
 *
 * @since 1.1-alpha
 */
public class LogFilterCommand extends BaseCommand {

    private final LogFilter logFilter;

    public LogFilterCommand(RVNKCore plugin) {
        super(plugin, "logfilter",
              "Manage log filtering for server plugins",
              "/logfilter <status|reload|enable|disable|add|remove|list>",
              "rvnktools.command.logfilter");

        this.logFilter = plugin.getLogFilter();
    }

    @Override
    protected boolean executeCommand(CommandSender sender, String[] args) {
        if (args.length == 0) {
            return handleStatus(sender);
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "status":
                return handleStatus(sender);
            case "reload":
                return handleReload(sender);
            case "enable":
                return handleEnable(sender, true);
            case "disable":
                return handleEnable(sender, false);
            case "add":
                return handleAddKeyword(sender, args);
            case "remove":
                return handleRemoveKeyword(sender, args);
            case "list":
                return handleListKeywords(sender);
            default:
                sender.sendMessage(ChatFormat.colorize("&c▶ Unknown subcommand: " + subCommand));
                sender.sendMessage(ChatFormat.colorize("&c▶ " + getUsage()));
                return false;
        }
    }

    /**
     * Handles the status subcommand - shows current filter configuration.
     */
    private boolean handleStatus(CommandSender sender) {
        sender.sendMessage(ChatFormat.colorize("&6⚙ Log Filter Status"));
        sender.sendMessage("");

        if (logFilter == null) {
            sender.sendMessage(ChatFormat.colorize("&c✖ Log Filter is not initialized"));
            return true;
        }

        LogFilterConfig config = logFilter.getConfig();

        // Basic status
        String statusColor = logFilter.isFilterEnabled() ? "&a" : "&c";
        String statusText = logFilter.isFilterEnabled() ? "Active" : "Inactive";
        sender.sendMessage(ChatFormat.colorize("&eFilter Status: " + statusColor + statusText));

        // Configuration details
        sender.sendMessage(ChatFormat.colorize("&eConfiguration: &f" + config.getConfigSummary()));
        sender.sendMessage(ChatFormat.colorize("&eEnabled: &f" + config.isEnabled()));
        sender.sendMessage(ChatFormat.colorize("&eDebug: &f" + config.isDebugEnabled()));

        // Keyword filters
        List<String> keywords = config.getFilterKeywords();
        sender.sendMessage(ChatFormat.colorize("&eKeyword Filters: &f" + keywords.size()));

        if (!keywords.isEmpty()) {
            sender.sendMessage(ChatFormat.colorize("&7␣␣␣ Use '/logfilter list' to see all keywords"));
        }

        return true;
    }

    /**
     * Handles the reload subcommand - reloads filter configuration.
     */
    private boolean handleReload(CommandSender sender) {
        if (logFilter == null) {
            sender.sendMessage(ChatFormat.colorize("&c✖ Log Filter is not initialized"));
            return true;
        }

        try {
            logFilter.reloadConfig();
            sender.sendMessage(ChatFormat.colorize("&a✓ Log Filter configuration reloaded"));

            // Show updated status
            LogFilterConfig config = logFilter.getConfig();
            sender.sendMessage(ChatFormat.colorize("&7␣␣␣ " + config.getConfigSummary()));

        } catch (Exception e) {
            sender.sendMessage(ChatFormat.colorize("&c✖ Failed to reload configuration: " + e.getMessage()));
            logger.error("Failed to reload log filter config via command", e);
        }

        return true;
    }

    /**
     * Handles enable/disable subcommands.
     */
    private boolean handleEnable(CommandSender sender, boolean enable) {
        if (logFilter == null) {
            sender.sendMessage(ChatFormat.colorize("&c✖ Log Filter is not initialized"));
            return true;
        }

        LogFilterConfig config = logFilter.getConfig();
        config.setEnabled(enable);

        String action = enable ? "enabled" : "disabled";
        sender.sendMessage(ChatFormat.colorize("&a✓ Log Filter " + action));

        return true;
    }

    /**
     * Handles the add subcommand - adds a keyword filter.
     */
    private boolean handleAddKeyword(CommandSender sender, String[] args) {
        if (logFilter == null) {
            sender.sendMessage(ChatFormat.colorize("&c✖ Log Filter is not initialized"));
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(ChatFormat.colorize("&c▶ Usage: /logfilter add <keyword>"));
            sender.sendMessage(ChatFormat.colorize("&7␣␣␣ Example: /logfilter add \"[DHS] Received\""));
            return false;
        }

        // Join all args from index 1 onwards to support multi-word keywords
        String keyword = String.join(" ", Arrays.copyOfRange(args, 1, args.length));

        LogFilterConfig config = logFilter.getConfig();
        config.addKeywordFilter(keyword);

        sender.sendMessage(ChatFormat.colorize("&a✓ Added keyword filter: &f" + keyword));

        return true;
    }

    /**
     * Handles the remove subcommand - removes a keyword filter.
     */
    private boolean handleRemoveKeyword(CommandSender sender, String[] args) {
        if (logFilter == null) {
            sender.sendMessage(ChatFormat.colorize("&c✖ Log Filter is not initialized"));
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(ChatFormat.colorize("&c▶ Usage: /logfilter remove <keyword>"));
            return false;
        }

        // Join all args from index 1 onwards to support multi-word keywords
        String keyword = String.join(" ", Arrays.copyOfRange(args, 1, args.length));

        LogFilterConfig config = logFilter.getConfig();
        boolean removed = config.removeKeywordFilter(keyword);

        if (removed) {
            sender.sendMessage(ChatFormat.colorize("&a✓ Removed keyword filter: &f" + keyword));
        } else {
            sender.sendMessage(ChatFormat.colorize("&c✖ Keyword filter not found: &f" + keyword));
        }

        return true;
    }

    /**
     * Handles the list subcommand - lists all keyword filters.
     */
    private boolean handleListKeywords(CommandSender sender) {
        if (logFilter == null) {
            sender.sendMessage(ChatFormat.colorize("&c✖ Log Filter is not initialized"));
            return true;
        }

        LogFilterConfig config = logFilter.getConfig();
        List<String> keywords = config.getFilterKeywords();

        sender.sendMessage(ChatFormat.colorize("&6⚙ Log Filter Keywords"));
        sender.sendMessage("");

        if (keywords.isEmpty()) {
            sender.sendMessage(ChatFormat.colorize("&7No keyword filters configured"));
        } else {
            sender.sendMessage(ChatFormat.colorize("&eConfigured keyword filters:"));

            for (int i = 0; i < keywords.size(); i++) {
                sender.sendMessage(ChatFormat.colorize("&f[" + (i + 1) + "] &7" + keywords.get(i)));
            }
        }

        sender.sendMessage("");
        sender.sendMessage(ChatFormat.colorize("&7␣␣␣ Use '/logfilter add <keyword>' to add filters"));
        sender.sendMessage(ChatFormat.colorize("&7␣␣␣ Use '/logfilter remove <keyword>' to remove filters"));

        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("status", "reload", "enable", "disable", "add", "remove", "list");
        }

        return Collections.emptyList();
    }
}
