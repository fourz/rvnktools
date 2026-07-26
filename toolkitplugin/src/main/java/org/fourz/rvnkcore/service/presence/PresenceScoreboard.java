package org.fourz.rvnkcore.service.presence;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.fourz.rvnkcore.api.model.PresenceDTO;
import org.fourz.rvnkcore.api.service.PlayerPreferencesService;
import org.fourz.rvnkcore.util.log.LogManager;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Maintains a shared sidebar scoreboard showing the merged cross-server roster (#1728).
 *
 * <p>One shared {@link Scoreboard} carries a {@link DisplaySlot#SIDEBAR} objective rebuilt from the
 * merged roster on every presence change. Visibility is a <b>persistent per-player preference</b>
 * ({@code rvnkcore:presence_sidebar}, default on) via {@link PlayerPreferencesService}: it survives
 * relogs and is toggled with {@code /list toggle}. Preference values are cached in memory (loaded on
 * join) so the render loop never blocks on the async store; when preferences are unavailable the
 * sidebar defaults to shown and the toggle is in-memory only.</p>
 *
 * @since 1.5.37
 */
public class PresenceScoreboard {

    /** Plugin id + notification type used for the persistent visibility preference. */
    public static final String PREF_PLUGIN_ID = "rvnkcore";
    public static final String PREF_TYPE = "presence_sidebar";

    private static final int MAX_LINES = 15;
    private static final String TITLE = ChatColor.GOLD + "" + ChatColor.BOLD + "Network";

    private final Plugin plugin;
    private final LogManager logger;
    private final PlayerPreferencesService prefs; // may be null
    private final Scoreboard board;
    private Objective objective;

    /** Cached per-player visibility (true = show). Absent → default shown. */
    private final Map<UUID, Boolean> showCache = new ConcurrentHashMap<>();

    /**
     * @param plugin owning plugin
     * @param logger LogManager instance
     * @param prefs  preferences service for persistent visibility (nullable → in-memory default-on)
     */
    public PresenceScoreboard(Plugin plugin, LogManager logger, PlayerPreferencesService prefs) {
        this.plugin = plugin;
        this.logger = logger;
        this.prefs = prefs;
        this.board = Bukkit.getScoreboardManager().getNewScoreboard();
        this.objective = board.registerNewObjective("netlist", "dummy", TITLE);
        this.objective.setDisplaySlot(DisplaySlot.SIDEBAR);
    }

    /** True when the player's cached preference says show (default on). */
    private boolean shown(UUID id) {
        return showCache.getOrDefault(id, Boolean.TRUE);
    }

    /**
     * Loads the player's persisted visibility preference and applies the sidebar accordingly. Called on
     * join; async, resolves on the main thread.
     *
     * @param player the joining player
     */
    public void loadAndApply(Player player) {
        UUID id = player.getUniqueId();
        if (prefs == null) {
            showCache.put(id, Boolean.TRUE);
            applyNow(player, true);
            return;
        }
        prefs.isNotificationEnabled(id, PREF_PLUGIN_ID, PREF_TYPE).thenAccept(enabled -> {
            boolean show = enabled == null || enabled;
            showCache.put(id, show);
            Bukkit.getScheduler().runTask(plugin, () -> {
                Player p = Bukkit.getPlayer(id);
                if (p != null) applyNow(p, show);
            });
        }).exceptionally(e -> {
            showCache.put(id, Boolean.TRUE);
            return null;
        });
    }

    /**
     * Rebuilds the sidebar from the merged roster and re-applies it to every player whose preference
     * shows it. Must run on the main thread.
     *
     * @param groups the merged roster (local first, then remotes)
     * @param total  total online across all servers
     */
    public void render(List<PresenceService.ServerGroup> groups, int total) {
        if (objective != null) {
            try { objective.unregister(); } catch (IllegalStateException ignored) { /* already gone */ }
        }
        objective = board.registerNewObjective("netlist", "dummy",
                TITLE + ChatColor.GRAY + " (" + total + ")");
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        applyNumberStyle(objective);

        // Flat, column-aligned roster (#1786): "<name padded> <server letter>", one row per player,
        // instead of a per-server header block. The server is a single letter (T/E/N) so the name
        // column stays wide and the eye can scan one list rather than nested groups.
        List<String> lines = new java.util.ArrayList<>();
        int nameWidth = 0;
        for (PresenceService.ServerGroup g : groups) {
            for (PresenceDTO.Entry e : g.getPlayers()) {
                nameWidth = Math.max(nameWidth, safeName(e.getName()).length());
            }
        }
        nameWidth = Math.min(nameWidth, 16);   // MC name cap; keeps padding sane on odd data

        for (PresenceService.ServerGroup g : groups) {
            String letter = serverLetter(g.getLabel());
            ChatColor letterColor = g.isLocal() ? ChatColor.GREEN : ChatColor.AQUA;
            for (PresenceDTO.Entry e : g.getPlayers()) {
                String name = safeName(e.getName());
                String pad = " ".repeat(Math.max(1, nameWidth - name.length() + 2));
                lines.add(ChatColor.WHITE + name + pad + letterColor + letter);
                if (lines.size() >= MAX_LINES) break;
            }
            if (lines.size() >= MAX_LINES) break;
        }

        int score = lines.size();
        for (String line : lines) {
            objective.getScore(unique(line, score)).setScore(score);
            score--;
        }

        for (Player p : Bukkit.getOnlinePlayers()) {
            if (shown(p.getUniqueId())) {
                p.setScoreboard(board);
            }
        }
    }

    /** Applies (or removes) the shared sidebar for a player per {@code show}. */
    private void applyNow(Player player, boolean show) {
        if (show) {
            player.setScoreboard(board);
        } else {
            player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        }
    }

    /**
     * Toggles the sidebar for a player and persists the choice as a preference.
     *
     * @param player the player
     * @return true if the sidebar is now shown, false if hidden
     */
    public boolean toggle(Player player) {
        return setShown(player, !shown(player.getUniqueId()));
    }

    /**
     * Sets the sidebar visibility explicitly and persists the choice (#1783). Idempotent — unlike
     * {@link #toggle}, calling it twice with the same value leaves the state unchanged, which is what
     * {@code /list on} and {@code /list off} need for macros and scripted use.
     *
     * @param player the player
     * @param show   true to show the sidebar, false to hide it
     * @return the resulting visibility (equal to {@code show})
     */
    public boolean setShown(Player player, boolean show) {
        UUID id = player.getUniqueId();
        showCache.put(id, show);
        applyNow(player, show);
        if (prefs != null) {
            prefs.setNotificationEnabled(id, PREF_PLUGIN_ID, PREF_TYPE, show).exceptionally(e -> {
                logger.warning("Failed to persist presence sidebar preference for " + player.getName()
                        + ": " + e.getMessage());
                return null;
            });
        }
        return show;
    }

    /**
     * Single-letter server code for the roster's right-hand column (#1786) — {@code test→T},
     * {@code event→E}, {@code nations→N}. Uses the first character of the configured label so a new
     * server needs no code change.
     */
    private static String serverLetter(String label) {
        if (label == null || label.isEmpty()) return "?";
        return label.substring(0, 1).toUpperCase(java.util.Locale.ROOT);
    }

    /**
     * Styles the sidebar's score numbers black so they read as background rather than data (#1786).
     *
     * <p>Minecraft always renders sidebar scores, in red, and there is no way to suppress them through
     * spigot-api — which is what RVNKCore compiles against. Paper does expose
     * {@code Objective#numberFormat}, and every RVNK tier runs Paper, so this reaches it reflectively:
     * compile against spigot, use the richer API when the runtime provides it. If anything is missing
     * the numbers simply stay red, which is the current behaviour — never a startup failure.</p>
     *
     * <p>Swap {@code BLACK} for {@code RED} (or drop the call) to make the numbers prominent again.</p>
     */
    private void applyNumberStyle(Objective obj) {
        try {
            Class<?> numberFormat = Class.forName("io.papermc.paper.scoreboard.numbers.NumberFormat");
            Class<?> styleApplicable = Class.forName("net.kyori.adventure.text.format.StyleBuilderApplicable");
            Class<?> namedColor = Class.forName("net.kyori.adventure.text.format.NamedTextColor");

            Object black = namedColor.getField("BLACK").get(null);
            Object applicables = java.lang.reflect.Array.newInstance(styleApplicable, 1);
            java.lang.reflect.Array.set(applicables, 0, black);

            Object format = numberFormat
                    .getMethod("styled", applicables.getClass())
                    .invoke(null, applicables);

            obj.getClass().getMethod("numberFormat", numberFormat).invoke(obj, format);
        } catch (Throwable t) {
            // Non-Paper runtime or an API change — leave the default red numbers visible.
            logger.debug("Sidebar number styling unavailable (" + t.getClass().getSimpleName()
                    + ") — scores stay visible");
        }
    }

    /** Clears cached visibility on quit. */
    public void forget(UUID id) {
        showCache.remove(id);
    }

    private static String safeName(String name) {
        if (name == null) return "?";
        return name.length() > 32 ? name.substring(0, 32) : name;
    }

    private static String unique(String line, int salt) {
        String s = line.length() > 40 ? line.substring(0, 40) : line;
        StringBuilder pad = new StringBuilder();
        for (int i = 0; i < (salt % 8) + 1; i++) {
            pad.append(ChatColor.RESET);
        }
        String out = s + pad;
        return out.length() > 128 ? out.substring(0, 128) : out;
    }
}
