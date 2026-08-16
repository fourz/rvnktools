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
import org.bukkit.event.server.TabCompleteEvent;
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
            scoreboard.loadAndApply(player);
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
            String arg = sidebarArg(event.getMessage());
            if (arg != null) {
                Player player = event.getPlayer();
                if (arg.equals("toggle")) {
                    boolean shown = scoreboard.toggle(player);
                    player.sendMessage(shown
                            ? ChatColor.GREEN + "Network roster: sidebar."
                            : ChatColor.GRAY + "Network roster hidden. Use /list sidebar or /list tab.");
                } else {
                    // sidebar | tab | off | on (on is an alias for sidebar) — #1793
                    PresenceScoreboard.Surface target =
                            PresenceScoreboard.Surface.parse(arg, PresenceScoreboard.Surface.SIDEBAR);
                    scoreboard.setSurface(player, target);
                    switch (target) {
                        case SIDEBAR -> player.sendMessage(ChatColor.GREEN + "Network roster: sidebar.");
                        case TAB -> player.sendMessage(ChatColor.GREEN
                                + "Network roster moved to the tab list - hold Tab to see it.");
                        case OFF -> player.sendMessage(ChatColor.GRAY
                                + "Network roster hidden. Use /list sidebar or /list tab.");
                    }
                }
            } else {
                sendRoster(event.getPlayer());
            }
        }
    }

    /** Sidebar sub-arguments accepted by {@code /list} and {@code /glist} (#1783). */
    private static final List<String> SIDEBAR_ARGS = List.of("sidebar", "tab", "off", "toggle", "on");

    /**
     * Returns the normalised sidebar sub-argument ({@code toggle} / {@code on} / {@code off}) when the
     * command carries one, else null (meaning "print the roster"). {@code on}/{@code off} are explicit
     * and idempotent; {@code toggle} flips.
     */
    private static String sidebarArg(String raw) {
        if (raw == null) return null;
        String[] parts = raw.trim().split("\\s+");
        if (parts.length < 2) return null;
        String arg = parts[1].toLowerCase(Locale.ROOT);
        return SIDEBAR_ARGS.contains(arg) ? arg : null;
    }

    /**
     * Supplies completions for the overridden {@code /list} / {@code /glist} (#1783). The override runs
     * on {@link PlayerCommandPreprocessEvent}, which intercepts execution only — vanilla {@code /list}
     * owns its own completions, so the sidebar arguments are invisible without this handler.
     *
     * <p>Runs at {@link EventPriority#HIGHEST} deliberately: handlers fire lowest-to-highest, so the
     * <b>last</b> writer wins. At LOWEST, Paper's own {@code /list} completer (which offers
     * {@code uuids}) ran afterwards and overwrote ours — the completions showed {@code uuids} instead
     * of the sidebar arguments. We replace the list outright rather than merging, because the override
     * cancels {@code /list} execution and always prints the roster, so {@code uuids} would be a
     * suggestion that does nothing.</p>
     *
     * @param event the tab-complete event
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTabComplete(TabCompleteEvent event) {
        String buffer = event.getBuffer();
        String cmd = commandWord(buffer);
        if (cmd == null || !(cmd.equals("list") || cmd.equals("glist"))) return;

        String[] parts = buffer.split("\\s+", -1);
        // Only complete the FIRST argument: "/list <here>". parts[0] is the command itself.
        if (parts.length != 2) return;

        String prefix = parts[1].toLowerCase(Locale.ROOT);
        List<String> matches = SIDEBAR_ARGS.stream()
                .filter(a -> a.startsWith(prefix))
                .collect(Collectors.toList());
        if (!matches.isEmpty()) {
            event.setCompletions(matches);
        }
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
                + ChatColor.GRAY + " - " + ChatColor.WHITE + total + ChatColor.GRAY + " online across servers");
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
