package org.fourz.rvnkcore.event;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.plugin.Plugin;
import org.fourz.rvnkcore.api.model.PresenceDTO;
import org.fourz.rvnkcore.service.presence.PresenceScoreboard;
import org.fourz.rvnkcore.service.presence.PresenceService;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Wires cross-server presence into gameplay (#1728):
 * <ul>
 *   <li>publishes this server's roster to peers on join/quit (deferred a tick for an accurate count),</li>
 *   <li>keeps the sidebar in sync and applies it to joiners,</li>
 *   <li>overrides {@code /list} (and adds {@code /glist}) to print the merged origin-tagged roster.</li>
 * </ul>
 *
 * @since 1.5.37
 */
public class PresenceListener implements Listener {

    private final Plugin plugin;
    private final PresenceService service;
    private final PresenceScoreboard scoreboard;

    /**
     * @param plugin     owning plugin (main-thread scheduler)
     * @param service    presence service
     * @param scoreboard shared sidebar
     */
    public PresenceListener(Plugin plugin, PresenceService service, PresenceScoreboard scoreboard) {
        this.plugin = plugin;
        this.service = service;
        this.scoreboard = scoreboard;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        final Player player = event.getPlayer();
        // Defer a tick so getOnlinePlayers() includes the joiner, then republish + refresh UI.
        Bukkit.getScheduler().runTask(plugin, () -> {
            service.broadcastLocalRoster();
            scoreboard.render(service.getMergedRoster(), service.totalCount());
            scoreboard.apply(player);
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        final java.util.UUID id = event.getPlayer().getUniqueId();
        scoreboard.forget(id);
        // Defer a tick so getOnlinePlayers() no longer includes the quitter before we republish.
        Bukkit.getScheduler().runTask(plugin, () -> {
            service.broadcastLocalRoster();
            scoreboard.render(service.getMergedRoster(), service.totalCount());
        });
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        String cmd = commandWord(event.getMessage());
        if (cmd == null) return;
        if (cmd.equals("list") || cmd.equals("glist")) {
            event.setCancelled(true);
            if (hasToggleArg(event.getMessage())) {
                boolean shown = scoreboard.toggle(event.getPlayer());
                event.getPlayer().sendMessage(shown
                        ? ChatColor.GREEN + "Network sidebar shown."
                        : ChatColor.GRAY + "Network sidebar hidden. Use /list toggle to show it again.");
            } else {
                sendRoster(event.getPlayer());
            }
        }
    }

    /** True when the command's first argument is "toggle". */
    private static boolean hasToggleArg(String raw) {
        if (raw == null) return false;
        String[] parts = raw.trim().split("\\s+");
        return parts.length >= 2 && parts[1].equalsIgnoreCase("toggle");
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onConsoleCommand(ServerCommandEvent event) {
        String cmd = commandWord("/" + event.getCommand());
        if (cmd == null) return;
        if (cmd.equals("list") || cmd.equals("glist")) {
            event.setCancelled(true);
            sendRoster(event.getSender());
        }
    }

    /** Extracts the lowercase command word from a raw "/cmd args" string, or null. */
    private static String commandWord(String raw) {
        if (raw == null || !raw.startsWith("/")) return null;
        String body = raw.substring(1).trim();
        if (body.isEmpty()) return null;
        int sp = body.indexOf(' ');
        String word = (sp >= 0) ? body.substring(0, sp) : body;
        return word.toLowerCase(Locale.ROOT);
    }

    /** Prints the merged, origin-tagged roster to a sender. */
    private void sendRoster(CommandSender sender) {
        List<PresenceService.ServerGroup> groups = service.getMergedRoster();
        int total = service.totalCount();
        sender.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "Network"
                + ChatColor.GRAY + " — " + ChatColor.WHITE + total + ChatColor.GRAY + " online across servers");
        for (PresenceService.ServerGroup g : groups) {
            ChatColor tag = g.isLocal() ? ChatColor.GREEN : ChatColor.AQUA;
            String names = g.getPlayers().stream()
                    .map(PresenceDTO.Entry::getName)
                    .filter(n -> n != null && !n.isBlank())
                    .collect(Collectors.joining(ChatColor.GRAY + ", " + ChatColor.WHITE));
            sender.sendMessage(tag + "[" + ChatColor.WHITE + g.getLabel() + tag + "] "
                    + ChatColor.GRAY + "(" + g.getPlayers().size() + ") "
                    + ChatColor.WHITE + (names.isEmpty() ? ChatColor.DARK_GRAY + "empty" : names));
        }
    }
}
