package org.fourz.rvnkcore.command;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.fourz.rvnkcore.RVNKCore;
import org.fourz.rvnkcore.api.model.NotificationTypeDefinition;
import org.fourz.rvnkcore.api.model.PlayerPreferencesDTO;
import org.fourz.rvnkcore.api.model.QuietHoursConfig;
import org.fourz.rvnkcore.api.service.PlayerPreferencesService;
import org.fourz.rvnktools.command.manager.BaseCommand;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Unified command for managing player notification preferences across all RVNK plugins.
 *
 * <p>Provides a single command interface for players to configure their notification settings:</p>
 * <ul>
 *   <li>/pref - Show summary of all plugin preferences</li>
 *   <li>/pref &lt;plugin&gt; - Show detailed preferences for a plugin</li>
 *   <li>/pref &lt;plugin&gt; toggle - Toggle master on/off</li>
 *   <li>/pref &lt;plugin&gt; enable/disable &lt;type&gt; - Manage notification types</li>
 *   <li>/pref &lt;plugin&gt; quiet &lt;hour1&gt; &lt;hour2&gt; - Set quiet hours</li>
 *   <li>/pref &lt;plugin&gt; channel &lt;type&gt; &lt;channel&gt; &lt;on|off&gt; - Toggle channels</li>
 *   <li>/pref &lt;plugin&gt; reset - Reset to defaults</li>
 *   <li>/pref admin types [plugin] - View registered notification types (admin)</li>
 *   <li>/pref admin defaults &lt;plugin&gt; &lt;type&gt; &lt;on|off&gt; - Set default (admin)</li>
 * </ul>
 *
 * @since 1.5.0
 */
public class PlayerPreferencesCommand extends BaseCommand {

    /** Fallback plugin list used when no types have been registered via the registry yet. */
    private static final List<String> SUPPORTED_PLUGINS_FALLBACK = Arrays.asList(
        "rvnkquests", "rvnklore", "rvnktools"
    );

    /**
     * Plugins that own their preference UX via a dedicated command.
     * Excluded from /pref — players are redirected to the plugin's own command.
     */
    private static final Map<String, String> EXTERNALLY_MANAGED_PLUGINS = Map.of(
        "bartershops", "/shop notifications"
    );

    private static final List<String> STANDARD_CHANNELS = Arrays.asList(
        "TITLE", "ACTION_BAR", "CHAT", "SOUND", "BOSS_BAR", "DISCORD"
    );

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
        // Admin subcommand — console and players allowed, checked before player-only guard
        if (args.length >= 1 && args[0].equalsIgnoreCase("admin")) {
            if (!sender.hasPermission("rvnkcore.prefs.admin")) {
                sender.sendMessage(ChatColor.RED + "✖ You don't have permission for admin preference commands");
                return true;
            }
            return handleAdmin(sender, args);
        }

        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "✖ This command is player-only");
            return true;
        }

        if (prefsService == null) {
            sender.sendMessage(ChatColor.RED + "✖ PlayerPreferencesService is not available");
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

        // Redirect plugins that own their preference UX
        if (EXTERNALLY_MANAGED_PLUGINS.containsKey(pluginId)) {
            player.sendMessage(ChatColor.YELLOW + "[" + pluginId + "] preferences are managed by: "
                    + ChatColor.WHITE + EXTERNALLY_MANAGED_PLUGINS.get(pluginId));
            return true;
        }

        // Validate plugin against registry (fallback to static list)
        List<String> availablePlugins = getAvailablePlugins();
        if (!availablePlugins.contains(pluginId)) {
            player.sendMessage(ChatColor.RED + "✖ Unknown plugin: " + args[0]);
            player.sendMessage(ChatColor.GRAY + "Available: " + String.join(", ", availablePlugins));
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

    // ========== Admin Subcommand ==========

    private boolean handleAdmin(CommandSender sender, String[] args) {
        // args[0] = "admin"
        if (args.length < 2) {
            sender.sendMessage(ChatColor.GOLD + "=== Admin Preferences Commands ===");
            sender.sendMessage(ChatColor.GRAY + "/pref admin types - List all registered notification types");
            sender.sendMessage(ChatColor.GRAY + "/pref admin types <plugin> - List types for a specific plugin");
            sender.sendMessage(ChatColor.GRAY + "/pref admin defaults <plugin> <type> <on|off> - Set default value");
            return true;
        }

        String subcommand = args[1].toLowerCase();
        switch (subcommand) {
            case "types":
                return handleAdminTypes(sender, args);
            case "defaults":
                return handleAdminDefaults(sender, args);
            default:
                sender.sendMessage(ChatColor.RED + "✖ Unknown admin subcommand: " + args[1]);
                sender.sendMessage(ChatColor.GRAY + "Available: types, defaults");
                return true;
        }
    }

    private boolean handleAdminTypes(CommandSender sender, String[] args) {
        if (prefsService == null) {
            sender.sendMessage(ChatColor.RED + "✖ PlayerPreferencesService is not available");
            return true;
        }

        Map<String, List<NotificationTypeDefinition>> allTypes = prefsService.getAllRegisteredTypes();

        if (allTypes.isEmpty()) {
            sender.sendMessage(ChatColor.YELLOW + "No notification types registered yet (plugins may not have loaded).");
            return true;
        }

        // Filter by plugin if specified
        if (args.length >= 3) {
            String pluginFilter = args[2].toLowerCase();
            List<NotificationTypeDefinition> types = allTypes.get(pluginFilter);
            if (types == null || types.isEmpty()) {
                sender.sendMessage(ChatColor.RED + "✖ No types registered for plugin: " + pluginFilter);
                sender.sendMessage(ChatColor.GRAY + "Registered plugins: " + String.join(", ", allTypes.keySet()));
                return true;
            }
            sender.sendMessage(ChatColor.GOLD + "=== Registered Types: " + pluginFilter + " ===");
            for (NotificationTypeDefinition def : types) {
                String status = def.defaultEnabled() ? ChatColor.GREEN + "ON" : ChatColor.RED + "OFF";
                sender.sendMessage("  " + ChatColor.GRAY + def.typeId() + ChatColor.RESET
                        + " (" + status + ChatColor.RESET + ")"
                        + ChatColor.DARK_GRAY + " - " + def.description());
            }
            return true;
        }

        // Show all plugins and their types
        sender.sendMessage(ChatColor.GOLD + "=== Registered Notification Types ===");
        for (Map.Entry<String, List<NotificationTypeDefinition>> entry : allTypes.entrySet()) {
            String typeList = entry.getValue().stream()
                    .map(NotificationTypeDefinition::typeId)
                    .collect(Collectors.joining(", "));
            sender.sendMessage(ChatColor.YELLOW + "[" + entry.getKey() + "] " + ChatColor.GRAY + typeList);
        }
        return true;
    }

    private boolean handleAdminDefaults(CommandSender sender, String[] args) {
        if (prefsService == null) {
            sender.sendMessage(ChatColor.RED + "✖ PlayerPreferencesService is not available");
            return true;
        }

        // /pref admin defaults <plugin> <type> <on|off>
        if (args.length < 4) {
            sender.sendMessage(ChatColor.RED + "✖ Usage: /pref admin defaults <plugin> <type> <on|off>");
            return true;
        }

        String pluginId = args[2].toLowerCase();
        String type = args[3].toLowerCase();

        // If only 4 args (no on|off), show current defaults for the plugin
        if (args.length < 5) {
            sender.sendMessage(ChatColor.GOLD + "=== Defaults: " + pluginId + " ===");
            prefsService.getDefaultPreferences(pluginId)
                    .thenAccept(defaults -> {
                        List<NotificationTypeDefinition> registered = prefsService.getRegisteredTypes(pluginId);
                        if (registered.isEmpty()) {
                            // Show just the requested type
                            Boolean enabled = defaults.getNotificationTypes().getOrDefault(type, true);
                            String status = enabled ? ChatColor.GREEN + "ON (default)" : ChatColor.RED + "OFF (admin override)";
                            sender.sendMessage("  " + ChatColor.GRAY + type + ": " + status);
                        } else {
                            // Show all registered types with their defaults
                            for (NotificationTypeDefinition def : registered) {
                                Boolean enabled = defaults.getNotificationTypes().getOrDefault(def.typeId(), def.defaultEnabled());
                                boolean isOverride = defaults.getNotificationTypes().containsKey(def.typeId());
                                String label = isOverride ? " (admin override)" : " (default)";
                                String status = enabled ? ChatColor.GREEN + "ON" + label : ChatColor.RED + "OFF" + label;
                                sender.sendMessage("  " + ChatColor.GRAY + def.typeId() + ": " + status);
                            }
                        }
                    })
                    .exceptionally(ex -> {
                        sender.sendMessage(ChatColor.RED + "✖ Error loading defaults: " + ex.getMessage());
                        return null;
                    });
            return true;
        }

        String state = args[4].toLowerCase();
        if (!state.equals("on") && !state.equals("off")) {
            sender.sendMessage(ChatColor.RED + "✖ State must be 'on' or 'off'");
            return true;
        }

        // Validate type exists in registry
        List<NotificationTypeDefinition> types = prefsService.getRegisteredTypes(pluginId);
        if (!types.isEmpty() && types.stream().noneMatch(t -> t.typeId().equals(type))) {
            sender.sendMessage(ChatColor.RED + "✖ Unknown type '" + type + "' for plugin " + pluginId);
            String available = types.stream().map(NotificationTypeDefinition::typeId).collect(Collectors.joining(", "));
            sender.sendMessage(ChatColor.GRAY + "Available: " + available);
            return true;
        }

        String value = state.equals("on") ? "true" : "false";
        prefsService.setDefaultPreference(pluginId, type, value)
                .thenRun(() -> {
                    String status = state.equals("on") ? ChatColor.GREEN + "ON" : ChatColor.RED + "OFF";
                    sender.sendMessage(ChatColor.AQUA + "✓ Default for " + pluginId + "/" + type + " set to " + status);
                })
                .exceptionally(ex -> {
                    sender.sendMessage(ChatColor.RED + "✖ Error setting default: " + ex.getMessage());
                    logger.warning("Error setting admin default preference", ex);
                    return null;
                });
        return true;
    }

    // ========== Player Preference Display ==========

    /**
     * Display summary of all plugin preferences.
     * Shows registered plugins from the type registry, falling back to static list.
     */
    private void showAllPreferences(Player player, UUID playerUuid) {
        player.sendMessage(ChatColor.GOLD + "═══ Your Notification Preferences ═══");

        List<String> plugins = getAvailablePlugins();
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (String plugin : plugins) {
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

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .thenRun(() -> player.sendMessage(ChatColor.GRAY + "Tip: /pref <plugin> for details | /pref admin types to see all types"))
            .exceptionally(ex -> {
                logger.warning("Error in showAllPreferences", ex);
                return null;
            });
    }

    /**
     * Display detailed plugin preferences, reading types from the registry.
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

                List<String> typeIds = getTypeIds(pluginId);
                if (typeIds.isEmpty()) {
                    player.sendMessage(ChatColor.GRAY + "  (no types registered for this plugin)");
                } else {
                    for (String type : typeIds) {
                        Boolean enabled = prefs.getNotificationTypes().getOrDefault(type, true);
                        String status = enabled ? ChatColor.GREEN + "✓" : ChatColor.RED + "✗";
                        player.sendMessage("  " + status + ChatColor.RESET + " " + ChatColor.GRAY + type);
                    }
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

    // ========== Preference Action Handlers ==========

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

    private void handleEnable(Player player, UUID playerUuid, String pluginId, String type) {
        List<String> validTypes = getTypeIds(pluginId);
        if (!validTypes.isEmpty() && !validTypes.contains(type.toLowerCase())) {
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

    private void handleDisable(Player player, UUID playerUuid, String pluginId, String type) {
        List<String> validTypes = getTypeIds(pluginId);
        if (!validTypes.isEmpty() && !validTypes.contains(type.toLowerCase())) {
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

    private void handleChannel(Player player, UUID playerUuid, String pluginId,
                              String type, String channel, String state) {
        List<String> validTypes = getTypeIds(pluginId);
        if (!validTypes.isEmpty() && !validTypes.contains(type.toLowerCase())) {
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

        if (!state.equalsIgnoreCase("on") && !state.equalsIgnoreCase("off")) {
            player.sendMessage(ChatColor.RED + "✖ State must be 'on' or 'off'");
            return;
        }

        boolean enabled = state.equalsIgnoreCase("on");
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

    private void handleReset(Player player, UUID playerUuid, String pluginId) {
        prefsService.resetPreferences(playerUuid, pluginId)
            .thenRun(() -> player.sendMessage(ChatColor.AQUA + "✓ Preferences reset to defaults"))
            .exceptionally(ex -> {
                player.sendMessage(ChatColor.RED + "✖ Error resetting preferences: " + ex.getMessage());
                logger.warning("Error resetting preferences", ex);
                return null;
            });
    }

    // ========== Tab Completion ==========

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 0) {
            return Collections.emptyList();
        }

        // Admin completions
        if (args[0].equalsIgnoreCase("admin") && args.length > 1) {
            return tabCompleteAdmin(sender, args);
        }

        // First arg: plugin IDs + "admin" (for admins)
        if (args.length == 1) {
            List<String> options = new ArrayList<>(getAvailablePlugins());
            if (sender.hasPermission("rvnkcore.prefs.admin")) {
                options.add("admin");
            }
            return options.stream()
                .filter(p -> p.startsWith(args[0].toLowerCase()))
                .collect(Collectors.toList());
        }

        String pluginId = args[0].toLowerCase();
        List<String> availablePlugins = getAvailablePlugins();
        if (!availablePlugins.contains(pluginId)) {
            return Collections.emptyList();
        }

        // Second arg: actions
        if (args.length == 2) {
            List<String> actions = Arrays.asList("toggle", "enable", "disable", "quiet", "channel", "reset");
            return actions.stream()
                .filter(a -> a.startsWith(args[1].toLowerCase()))
                .collect(Collectors.toList());
        }

        String action = args[1].toLowerCase();

        // Notification type completion
        if ((action.equals("enable") || action.equals("disable") || action.equals("channel")) && args.length == 3) {
            return getTypeIds(pluginId).stream()
                .filter(t -> t.startsWith(args[2].toLowerCase()))
                .collect(Collectors.toList());
        }

        // Channel name completion
        if (action.equals("channel") && args.length == 4) {
            return STANDARD_CHANNELS.stream()
                .filter(c -> c.startsWith(args[3].toUpperCase()))
                .collect(Collectors.toList());
        }

        // Channel on/off completion
        if (action.equals("channel") && args.length == 5) {
            return Arrays.asList("on", "off").stream()
                .filter(s -> s.startsWith(args[4].toLowerCase()))
                .collect(Collectors.toList());
        }

        // Quiet hours disable option
        if (action.equals("quiet") && args.length == 3) {
            return Collections.singletonList("disable").stream()
                .filter(s -> s.startsWith(args[2].toLowerCase()))
                .collect(Collectors.toList());
        }

        return Collections.emptyList();
    }

    private List<String> tabCompleteAdmin(CommandSender sender, String[] args) {
        // args[0] = "admin", args[1+] = subcommand and its args
        if (args.length == 2) {
            return Arrays.asList("types", "defaults").stream()
                .filter(s -> s.startsWith(args[1].toLowerCase()))
                .collect(Collectors.toList());
        }

        if (args.length == 3) {
            // Plugin IDs from registry
            if (prefsService != null) {
                return new ArrayList<>(prefsService.getAllRegisteredTypes().keySet()).stream()
                    .filter(p -> p.startsWith(args[2].toLowerCase()))
                    .collect(Collectors.toList());
            }
        }

        if (args[1].equalsIgnoreCase("defaults") && args.length == 4 && prefsService != null) {
            String pluginId = args[2].toLowerCase();
            return prefsService.getRegisteredTypes(pluginId).stream()
                .map(NotificationTypeDefinition::typeId)
                .filter(t -> t.startsWith(args[3].toLowerCase()))
                .collect(Collectors.toList());
        }

        if (args[1].equalsIgnoreCase("defaults") && args.length == 5) {
            return Arrays.asList("on", "off").stream()
                .filter(s -> s.startsWith(args[4].toLowerCase()))
                .collect(Collectors.toList());
        }

        return Collections.emptyList();
    }

    // ========== Registry Helpers ==========

    /**
     * Returns the list of plugins to show in preference commands.
     * Uses the type registry if populated; falls back to the static list.
     */
    private List<String> getAvailablePlugins() {
        List<String> base;
        if (prefsService == null) {
            base = SUPPORTED_PLUGINS_FALLBACK;
        } else {
            Map<String, List<NotificationTypeDefinition>> registered = prefsService.getAllRegisteredTypes();
            base = registered.isEmpty() ? SUPPORTED_PLUGINS_FALLBACK : new ArrayList<>(registered.keySet());
        }
        return base.stream()
                .filter(p -> !EXTERNALLY_MANAGED_PLUGINS.containsKey(p))
                .collect(Collectors.toList());
    }

    /**
     * Returns the list of type IDs for a plugin.
     * Uses the type registry if populated; returns empty list otherwise (no validation).
     */
    private List<String> getTypeIds(String pluginId) {
        if (prefsService == null) {
            return Collections.emptyList();
        }
        return prefsService.getRegisteredTypes(pluginId).stream()
            .map(NotificationTypeDefinition::typeId)
            .collect(Collectors.toList());
    }
}
