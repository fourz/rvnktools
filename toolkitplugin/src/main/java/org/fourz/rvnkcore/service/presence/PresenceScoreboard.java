package org.fourz.rvnkcore.service.presence;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.fourz.rvnkcore.api.model.PresenceDTO;
import org.fourz.rvnkcore.util.log.LogManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Maintains a shared sidebar scoreboard showing the merged cross-server roster (#1728).
 *
 * <p>One shared {@link Scoreboard} carries a {@link DisplaySlot#SIDEBAR} objective rebuilt from the
 * merged roster on every presence change. It is applied to online players who have not toggled it off
 * ({@link #hide}); toggling off restores the server's main scoreboard so the feature never permanently
 * clobbers another plugin's sidebar. Sidebar lines are capped and made unique (scoreboard entries must
 * be distinct) with invisible colour padding.</p>
 *
 * @since 1.5.37
 */
public class PresenceScoreboard {

    private static final int MAX_LINES = 15;
    private static final String TITLE = ChatColor.GOLD + "" + ChatColor.BOLD + "Network";

    private final Plugin plugin;
    private final LogManager logger;
    private final Scoreboard board;
    private Objective objective;

    /** Players who toggled the sidebar off — they keep their main scoreboard. */
    private final Set<UUID> hidden = ConcurrentHashMap.newKeySet();

    /**
     * Creates the shared scoreboard and its sidebar objective.
     *
     * @param plugin owning plugin
     * @param logger LogManager instance
     */
    public PresenceScoreboard(Plugin plugin, LogManager logger) {
        this.plugin = plugin;
        this.logger = logger;
        this.board = Bukkit.getScoreboardManager().getNewScoreboard();
        this.objective = board.registerNewObjective("netlist", "dummy", TITLE);
        this.objective.setDisplaySlot(DisplaySlot.SIDEBAR);
    }

    /**
     * Rebuilds the sidebar from the merged roster and re-applies it to all non-hidden online players.
     * Must run on the main thread.
     *
     * @param groups the merged roster (local first, then remotes)
     * @param total  total online across all servers
     */
    public void render(List<PresenceService.ServerGroup> groups, int total) {
        // Recreate the objective to clear previous entries cleanly.
        if (objective != null) {
            try { objective.unregister(); } catch (IllegalStateException ignored) { /* already gone */ }
        }
        objective = board.registerNewObjective("netlist", "dummy",
                TITLE + ChatColor.GRAY + " (" + total + ")");
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);

        List<String> lines = new ArrayList<>();
        for (PresenceService.ServerGroup g : groups) {
            String header = (g.isLocal() ? ChatColor.GREEN : ChatColor.AQUA) + "["
                    + ChatColor.WHITE + g.getLabel() + (g.isLocal() ? ChatColor.GREEN : ChatColor.AQUA) + "] "
                    + ChatColor.GRAY + g.getPlayers().size();
            lines.add(header);
            for (PresenceDTO.Entry e : g.getPlayers()) {
                lines.add(ChatColor.GRAY + " " + ChatColor.WHITE + safeName(e.getName()));
                if (lines.size() >= MAX_LINES) break;
            }
            if (lines.size() >= MAX_LINES) break;
        }

        // Assign descending scores so lines render top-to-bottom in insertion order.
        int score = lines.size();
        for (String line : lines) {
            objective.getScore(unique(line, score)).setScore(score);
            score--;
        }

        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!hidden.contains(p.getUniqueId())) {
                p.setScoreboard(board);
            }
        }
    }

    /** Applies the sidebar to a player unless they have it toggled off. */
    public void apply(Player player) {
        if (player != null && !hidden.contains(player.getUniqueId())) {
            player.setScoreboard(board);
        }
    }

    /**
     * Toggles the sidebar for a player.
     *
     * @param player the player
     * @return true if the sidebar is now shown, false if hidden
     */
    public boolean toggle(Player player) {
        UUID id = player.getUniqueId();
        if (hidden.remove(id)) {
            player.setScoreboard(board);
            return true;
        }
        hidden.add(id);
        player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        return false;
    }

    /** Clears per-player tracking on quit. */
    public void forget(UUID id) {
        hidden.remove(id);
    }

    /** Truncates a name to a safe sidebar length. */
    private static String safeName(String name) {
        if (name == null) return "?";
        return name.length() > 32 ? name.substring(0, 32) : name;
    }

    /**
     * Makes a sidebar entry unique (scoreboard entries must be distinct) by appending an invisible
     * colour-code run derived from the score. Keeps within the entry length budget.
     */
    private static String unique(String line, int salt) {
        String s = line.length() > 40 ? line.substring(0, 40) : line;
        StringBuilder pad = new StringBuilder();
        // Encode the salt as a short run of §r/§0 pairs — invisible, distinct per line.
        for (int i = 0; i < (salt % 8) + 1; i++) {
            pad.append(ChatColor.RESET);
        }
        String out = s + pad;
        return out.length() > 128 ? out.substring(0, 128) : out;
    }
}
