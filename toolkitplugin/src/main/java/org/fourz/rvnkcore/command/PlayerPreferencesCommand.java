package org.fourz.rvnkcore.command;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.fourz.rvnkcore.RVNKCore;
import org.fourz.rvnkcore.api.model.PlayerPreferencesDTO;
import org.fourz.rvnkcore.api.model.QuietHoursConfig;
import org.fourz.rvnkcore.api.service.PlayerPreferencesService;
import org.fourz.rvnktools.command.manager.BaseCommand;
import org.fourz.rvnktools.command.manager.SubCommand;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Unified command for managing player notification preferences across all RVNK plugins.
 *
 * <p>Provides a single command interface for players to configure their notification settings:</p>
 * <ul>
 *   <li>/pref - Show summary of all plugin preferences</li>
 *   <li>/pref <plugin> - Show detailed preferences for a plugin</li>
 *   <li>/pref <plugin> toggle - Toggle master on/off</li>
 *   <li>/pref <plugin> enable/disable <type> - Manage notification types</li>
 *   <li>/pref <plugin> quiet <hour1> <hour2> - Set quiet hours</li>
 *   <li>/pref <plugin> channel <type> <channel> <on|off> - Toggle channels</li>
 *   <li>/pref <plugin> reset - Reset to defaults</li>
 * </ul>
 *
 * @since 1.5.0
 */
public class PlayerPreferencesCommand extends BaseCommand {

    private static final List<String> SUPPORTED_PLUGINS = Arrays.asList(
        "rvnkquests", "rvnklore", "bartershops", "rvnktools"
    );

    private static final List<String> STANDARD_CHANNELS = Arrays.asList(
        "TITLE", "ACTION_BAR", "CHAT", "SOUND", "BOSS_BAR", "DISCORD"
    );

    private static final Map<String, List<String>> PLUGIN_NOTIFICATION_TYPES = new HashMap<>();
    static {
        PLUGIN_NOTIFICATION_TYPES.put("rvnkquests", Arrays.asList(
            "quest_start", "quest_complete", "quest_failed", "objective_progress",
            "objective_complete", "quest_available", "milestone", "chain_progress"
        ));
        PLUGIN_NOTIFICATION_TYPES.put("rvnklore", Arrays.asList(
            "discovery", "achievement", "collection_completion"
        ));
        PLUGIN_NOTIFICATION_TYPES.put("bartershops", Arrays.asList(
            "trade_complete", "trade_failed", "shop_expired"
        ));
        PLUGIN_NOTIFICATION_TYPES.put("rvnktools", Arrays.asList(
            "announcement", "broadcast"
        ));
    }

    private final PlayerPreferencesService prefsService;

    public PlayerPreferencesCommand(RVNKCore plugin) {
        super(plugin, "pref",
              "Manage your notification preferences",
              "/pref [plugin] [action] [args]",
              "rvnkcore.prefs");

        this.prefsService = plugin.getService(PlayerPreferencesService.class);
    }

    @Override
    protected boolean executeCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "✖ This command is player-only");
            return true;
        }

        Player player = (Player) sender;
        UUID playerUuid = player.getUniqueId();

        // Route based on args
        if (args.length == 0) {
            showAllPreferences(player, playerUuid);
            return true;
        }

        String pluginId = args[0].toLowerCase();
        if (!SUPPORTED_PLUGINS.contains(pluginId)) {
            player.sendMessage(ChatColor.RED + "✖ Unknown plugin: " + args[0]);
            player.sendMessage(ChatColor.GRAY + "Available: " + String.join(", ", SUPPORTED_PLUGINS));
            return true;
        }

        if (args.length == 1) {
            showPluginPreferences(player, playerUuid, pluginId);
            return true;
        }

        String action = args[1].toLowerCase();
        try {
            switch (action) {
                case "toggle":
                    handleToggle(player, playerUuid, pluginId);
                    break;
                case "enable":
                    if (args.length < 3) {
                        player.sendMessage(ChatColor.RED + "✖ Usage: /pref " + pluginId + " enable <type>");
                        return true;
                    }
                    handleEnable(player, playerUuid, pluginId, args[2]);
                    break;
                case "disable":
                    if (args.length < 3) {
                        player.sendMessage(ChatColor.RED + "✖ Usage: /pref " + pluginId + " disable <type>");
                        return true;
                    }
                    handleDisable(player, playerUuid, pluginId, args[2]);
                    break;
                case "quiet":
                    handleQuietHours(player, playerUuid, pluginId, args);
                    break;
                case "channel":
                    if (args.length < 5) {
                        player.sendMessage(ChatColor.RED + "✖ Usage: /pref " + pluginId + " channel <type> <channel> <on|off>");
                        return true;
                    }
                    handleChannel(player, playerUuid, pluginId, args[2], args[3], args[4]);
                    break;
                case "reset":
                    handleReset(player, playerUuid, pluginId);
                    break;
                default:
                    player.sendMessage(ChatColor.RED + "✖ Unknown action: " + action);
                    player.sendMessage(ChatColor.GRAY + "Available actions: toggle, enable, disable, quiet, channel, reset");
                    return true;
            }
        } catch (Exception e) {
            player.sendMessage(ChatColor.RED + "✖ Error processing command: " + e.getMessage());
            logger.warning("Error in PlayerPreferencesCommand", e);
        }

        return true;
    }

    /**
     * Display summary of all plugin preferences
     */
    private void showAllPreferences(Player player, UUID playerUuid) {
        player.sendMessage(ChatColor.GOLD + "═══ Your Notification Preferences ═══");

        // Fetch all plugin preferences asynchronously
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (String plugin : SUPPORTED_PLUGINS) {
            CompletableFuture<Void> future = prefsService.getPreferences(playerUuid, plugin)
                .thenAccept(prefs -> {
                    String master = prefs.isMasterEnabled() ? ChatColor.GREEN + "ON" : ChatColor.RED + "OFF";
                    String quietStatus = prefs.getQuietHours().isEnabled()
                        ? prefs.getQuietHours().getStartHour() + ":00-" + prefs.getQuietHours().getEndHour() + ":00"
                        : ChatColor.GRAY + "none";

                    int enabledCount = (int) prefs.getNotificationTypes().values().stream()
                        .filter(v -> v != null && v).count();
                    int disabledCount = (int) prefs.getNotificationTypes().values().stream()
                        .filter(v -> v != null && !v).count();

                    player.sendMessage(ChatColor.YELLOW + "[" + plugin.toUpperCase() + "]");
                    player.sendMessage("  Master: " + master + ChatColor.RESET +
                                     " | Quiet: " + quietStatus + ChatColor.RESET);
                    player.sendMessage("  Types: " + ChatColor.GREEN + enabledCount + " enabled" +
                                     ChatColor.RESET + " | " + ChatColor.RED + disabledCount + " disabled");
                    player.sendMessage("");
                })
                .exceptionally(ex -> {
                    player.sendMessage(ChatColor.RED + "✖ Error loading " + plugin + " preferences");
                    logger.warning("Error loading preferences for " + plugin, ex);
                    return null;
                });
            futures.add(future);
        }

        // Wait for all futures to complete
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .thenRun(() -> player.sendMessage(ChatColor.GRAY + "Tip: /pref <plugin> for details"))
            .exceptionally(ex -> {
                logger.warning("Error in showAllPreferences", ex);
                return null;
            });
    }

    /**
     * Display detailed plugin preferences
     */
    private void showPluginPreferences(Player player, UUID playerUuid, String pluginId) {
        prefsService.getPreferences(playerUuid, pluginId)
            .thenAccept(prefs -> {
                player.sendMessage(ChatColor.GOLD + "═══ " + pluginId.toUpperCase() + " Preferences ═══");

                String master = prefs.isMasterEnabled() ? ChatColor.GREEN + "ON" : ChatColor.RED + "OFF";
                player.sendMessage(ChatColor.YELLOW + "Master Toggle: " + master);

                if (prefs.getQuietHours().isEnabled()) {
                    player.sendMessage(ChatColor.YELLOW + "Quiet Hours: " +
                        prefs.getQuietHours().getStartHour() + ":00 - " +
                        prefs.getQuietHours().getEndHour() + ":00");
                } else {
                    player.sendMessage(ChatColor.YELLOW + "Quiet Hours: " + ChatColor.GRAY + "disabled");
                }

                player.sendMessage("");
                player.sendMessage(ChatColor.GOLD + "Notification Types:");

                List<String> types = PLUGIN_NOTIFICATION_TYPES.getOrDefault(pluginId, Collections.emptyList());
                for (String type : types) {
                    Boolean enabled = prefs.getNotificationTypes().getOrDefault(type, true);
                    String status = enabled ? ChatColor.GREEN + "✓" : ChatColor.RED + "✗";
                    String channels = String.join(", ", STANDARD_CHANNELS);
                    player.sendMessage("  " + status + ChatColor.RESET + " " + ChatColor.GRAY + type +
                                     ChatColor.RESET + " (" + channels + ")");
                }

                player.sendMessage("");
                player.sendMessage(ChatColor.GRAY + "Tip: /pref " + pluginId + " toggle to disable all");
            })
            .exceptionally(ex -> {
                player.sendMessage(ChatColor.RED + "✖ Error loading preferences: " + ex.getMessage());
                logger.warning("Error loading preferences", ex);
                return null;
            });
    }

    /**
     * Toggle master on/off for a plugin
     */
    private void handleToggle(Player player, UUID playerUuid, String pluginId) {
        prefsService.isMasterEnabled(playerUuid, pluginId)
            .thenCompose(currentEnabled -> {
                boolean newEnabled = !currentEnabled;
                return prefsService.setMasterEnabled(playerUuid, pluginId, newEnabled)
                    .thenApply(v -> newEnabled);
            })
            .thenAccept(newEnabled -> {
                String status = newEnabled ? ChatColor.GREEN + "enabled" : ChatColor.RED + "disabled";
                player.sendMessage(ChatColor.AQUA + "✓ " + pluginId + " notifications " + status);
            })
            .exceptionally(ex -> {
                player.sendMessage(ChatColor.RED + "✖ Error toggling master: " + ex.getMessage());
                logger.warning("Error toggling master", ex);
                return null;
            });
    }

    /**
     * Enable a notification type
     */
    private void handleEnable(Player player, UUID playerUuid, String pluginId, String type) {
        List<String> validTypes = PLUGIN_NOTIFICATION_TYPES.getOrDefault(pluginId, Collections.emptyList());
        if (!validTypes.contains(type.toLowerCase())) {
            player.sendMessage(ChatColor.RED + "✖ Unknown notification type: " + type);
            player.sendMessage(ChatColor.GRAY + "Available: " + String.join(", ", validTypes));
            return;
        }

        prefsService.setNotificationEnabled(playerUuid, pluginId, type, true)
            .thenRun(() -> player.sendMessage(ChatColor.AQUA + "✓ Enabled " + type + " notifications"))
            .exceptionally(ex -> {
                player.sendMessage(ChatColor.RED + "✖ Error enabling notifications: " + ex.getMessage());
                logger.warning("Error enabling notifications", ex);
                return null;
            });
    }

    /**
     * Disable a notification type
     */
    private void handleDisable(Player player, UUID playerUuid, String pluginId, String type) {
        List<String> validTypes = PLUGIN_NOTIFICATION_TYPES.getOrDefault(pluginId, Collections.emptyList());
        if (!validTypes.contains(type.toLowerCase())) {
            player.sendMessage(ChatColor.RED + "✖ Unknown notification type: " + type);
            player.sendMessage(ChatColor.GRAY + "Available: " + String.join(", ", validTypes));
            return;
        }

        prefsService.setNotificationEnabled(playerUuid, pluginId, type, false)
            .thenRun(() -> player.sendMessage(ChatColor.AQUA + "✓ Disabled " + type + " notifications"))
            .exceptionally(ex -> {
                player.sendMessage(ChatColor.RED + "✖ Error disabling notifications: " + ex.getMessage());
                logger.warning("Error disabling notifications", ex);
                return null;
            });
    }

    /**
     * Set or disable quiet hours
     */
    private void handleQuietHours(Player player, UUID playerUuid, String pluginId, String[] args) {
        if (args.length < 3) {
            player.sendMessage(ChatColor.RED + "✖ Usage: /pref " + pluginId + " quiet <hour1> <hour2> or /pref " + pluginId + " quiet disable");
            return;
        }

        if (args[2].equalsIgnoreCase("disable")) {
            prefsService.setQuietHours(playerUuid, pluginId, -1, -1)
                .thenRun(() -> player.sendMessage(ChatColor.AQUA + "✓ Quiet hours disabled"))
                .exceptionally(ex -> {
                    player.sendMessage(ChatColor.RED + "✖ Error disabling quiet hours: " + ex.getMessage());
                    logger.warning("Error disabling quiet hours", ex);
                    return null;
                });
            return;
        }

        if (args.length < 4) {
            player.sendMessage(ChatColor.RED + "✖ Usage: /pref " + pluginId + " quiet <hour1> <hour2> or /pref " + pluginId + " quiet disable");
            return;
        }

        try {
            int hour1 = Integer.parseInt(args[2]);
            int hour2 = Integer.parseInt(args[3]);

            if (hour1 < 0 || hour1 > 23 || hour2 < 0 || hour2 > 23) {
                player.sendMessage(ChatColor.RED + "✖ Hours must be between 0 and 23");
                return;
            }

            prefsService.setQuietHours(playerUuid, pluginId, hour1, hour2)
                .thenRun(() -> player.sendMessage(ChatColor.AQUA + "✓ Quiet hours set to " + hour1 + ":00 - " + hour2 + ":00"))
                .exceptionally(ex -> {
                    player.sendMessage(ChatColor.RED + "✖ Error setting quiet hours: " + ex.getMessage());
                    logger.warning("Error setting quiet hours", ex);
                    return null;
                });
        } catch (NumberFormatException e) {
            player.sendMessage(ChatColor.RED + "✖ Hours must be numbers");
        }
    }

    /**
     * Toggle a specific channel for a notification type
     */
    private void handleChannel(Player player, UUID playerUuid, String pluginId,
                              String type, String channel, String state) {
        List<String> validTypes = PLUGIN_NOTIFICATION_TYPES.getOrDefault(pluginId, Collections.emptyList());
        if (!validTypes.contains(type.toLowerCase())) {
            player.sendMessage(ChatColor.RED + "✖ Unknown notification type: " + type);
            player.sendMessage(ChatColor.GRAY + "Available: " + String.join(", ", validTypes));
            return;
        }

        String channelUpper = channel.toUpperCase();
        if (!STANDARD_CHANNELS.contains(channelUpper)) {
            player.sendMessage(ChatColor.RED + "✖ Unknown channel: " + channel);
            player.sendMessage(ChatColor.GRAY + "Available: " + String.join(", ", STANDARD_CHANNELS));
            return;
        }

        boolean enabled = state.equalsIgnoreCase("on");
        if (!state.equalsIgnoreCase("on") && !state.equalsIgnoreCase("off")) {
            player.sendMessage(ChatColor.RED + "✖ State must be 'on' or 'off'");
            return;
        }

        prefsService.setChannelEnabled(playerUuid, pluginId, type, channelUpper, enabled)
            .thenRun(() -> {
                String status = enabled ? ChatColor.GREEN + "enabled" : ChatColor.RED + "disabled";
                player.sendMessage(ChatColor.AQUA + "✓ " + channelUpper + " " + status + " for " + type);
            })
            .exceptionally(ex -> {
                player.sendMessage(ChatColor.RED + "✖ Error updating channel: " + ex.getMessage());
                logger.warning("Error updating channel", ex);
                return null;
            });
    }

    /**
     * Reset preferences to defaults
     */
    private void handleReset(Player player, UUID playerUuid, String pluginId) {
        prefsService.resetPreferences(playerUuid, pluginId)
            .thenRun(() -> player.sendMessage(ChatColor.AQUA + "✓ Preferences reset to defaults"))
            .exceptionally(ex -> {
                player.sendMessage(ChatColor.RED + "✖ Error resetting preferences: " + ex.getMessage());
                logger.warning("Error resetting preferences", ex);
                return null;
            });
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            return Collections.emptyList();
        }

        if (args.length == 0) {
            return Collections.emptyList();
        }

        // Tab complete plugin names
        if (args.length == 1) {
            return SUPPORTED_PLUGINS.stream()
                .filter(p -> p.startsWith(args[0].toLowerCase()))
                .collect(Collectors.toList());
        }

        String pluginId = args[0].toLowerCase();
        if (!SUPPORTED_PLUGINS.contains(pluginId)) {
            return Collections.emptyList();
        }

        // Tab complete actions
        if (args.length == 2) {
            List<String> actions = Arrays.asList("toggle", "enable", "disable", "quiet", "channel", "reset");
            return actions.stream()
                .filter(a -> a.startsWith(args[1].toLowerCase()))
                .collect(Collectors.toList());
        }

        String action = args[1].toLowerCase();

        // Tab complete notification types
        if ((action.equals("enable") || action.equals("disable") || action.equals("channel")) && args.length == 3) {
            List<String> types = PLUGIN_NOTIFICATION_TYPES.getOrDefault(pluginId, Collections.emptyList());
            return types.stream()
                .filter(t -> t.startsWith(args[2].toLowerCase()))
                .collect(Collectors.toList());
        }

        // Tab complete channels
        if (action.equals("channel") && args.length == 4) {
            return STANDARD_CHANNELS.stream()
                .filter(c -> c.startsWith(args[3].toUpperCase()))
                .collect(Collectors.toList());
        }

        // Tab complete on/off
        if (action.equals("channel") && args.length == 5) {
            return Arrays.asList("on", "off").stream()
                .filter(s -> s.startsWith(args[4].toLowerCase()))
                .collect(Collectors.toList());
        }

        // Tab complete quiet hours disable option
        if (action.equals("quiet") && args.length == 3) {
            return Arrays.asList("disable").stream()
                .filter(s -> s.startsWith(args[2].toLowerCase()))
                .collect(Collectors.toList());
        }

        return Collections.emptyList();
    }
}
