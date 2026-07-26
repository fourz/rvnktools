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

    /** Cached per-player surface. Absent → default SIDEBAR. */
    private final Map<UUID, Surface> surfaceCache = new ConcurrentHashMap<>();

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

    /** Where a player wants the network roster drawn (#1793). */
    public enum Surface {
        /** Right-of-screen scoreboard sidebar (default; always visible). */
        SIDEBAR,
        /** Tab-list footer — zero screen cost, visible while holding Tab. */
        TAB,
        /** Not shown at all. */
        OFF;

        /** Parses a token, falling back to {@code def} for anything unrecognised. */
        public static Surface parse(String token, Surface def) {
            if (token == null) return def;
            switch (token.trim().toLowerCase(java.util.Locale.ROOT)) {
                case "sidebar": case "side": case "on":  return SIDEBAR;
                case "tab": case "list":                 return TAB;
                case "off": case "none": case "hide":    return OFF;
                default:                                 return def;
            }
        }

        public String token() { return name().toLowerCase(java.util.Locale.ROOT); }
    }

    /** Metadata key holding the chosen surface. Stored beside the legacy boolean, not replacing it. */
    public static final String PREF_SURFACE_KEY = "presence-surface";

    /** The player's cached surface (default SIDEBAR). */
    private Surface surface(UUID id) {
        return surfaceCache.getOrDefault(id, Surface.SIDEBAR);
    }

    /** True when the player's cached preference draws the sidebar. */
    private boolean shown(UUID id) {
        return surface(id) == Surface.SIDEBAR;
    }

    /**
     * Loads the player's persisted surface preference and applies it. Called on join; async, resolves on
     * the main thread.
     *
     * <p><b>Migration (#1793):</b> the surface is stored in preference metadata, but earlier builds only
     * had the boolean {@code presence_sidebar} notification type. When the metadata key is absent we
     * derive the surface from that boolean — enabled becomes {@code SIDEBAR}, disabled becomes
     * {@code OFF} — so nobody's existing on/off choice is silently reset to the default.</p>
     *
     * @param player the joining player
     */
    public void loadAndApply(Player player) {
        UUID id = player.getUniqueId();
        if (prefs == null) {
            surfaceCache.put(id, Surface.SIDEBAR);
            applyNow(player, Surface.SIDEBAR);
            return;
        }
        prefs.getPreferences(id, PREF_PLUGIN_ID).thenAccept(dto -> {
            String stored = (dto != null && dto.getMetadata() != null)
                    ? dto.getMetadata().get(PREF_SURFACE_KEY) : null;

            if (stored != null && !stored.isBlank()) {
                applyResolved(id, Surface.parse(stored, Surface.SIDEBAR));
                return;
            }
            // No surface recorded yet — inherit the legacy boolean so an existing "off" is respected.
            prefs.isNotificationEnabled(id, PREF_PLUGIN_ID, PREF_TYPE).thenAccept(enabled -> {
                boolean on = enabled == null || enabled;
                applyResolved(id, on ? Surface.SIDEBAR : Surface.OFF);
            }).exceptionally(e -> {
                applyResolved(id, Surface.SIDEBAR);
                return null;
            });
        }).exceptionally(e -> {
            applyResolved(id, Surface.SIDEBAR);
            return null;
        });
    }

    /** Caches the resolved surface and applies it on the main thread. */
    private void applyResolved(UUID id, Surface resolved) {
        surfaceCache.put(id, resolved);
        Bukkit.getScheduler().runTask(plugin, () -> {
            Player p = Bukkit.getPlayer(id);
            if (p != null) applyNow(p, resolved);
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

        // Tab footer carries the same roster for players on the TAB surface (#1793). The tab LIST itself
        // only enumerates local players — remote ones cannot be real rows without packet work — so the
        // merged roster lives in the footer text instead.
        lastTabFooter = ChatColor.GOLD + "" + ChatColor.BOLD + "Network"
                + ChatColor.GRAY + " (" + total + ")\n"
                + String.join("\n", lines);

        for (Player p : Bukkit.getOnlinePlayers()) {
            switch (surface(p.getUniqueId())) {
                case SIDEBAR -> p.setScoreboard(board);
                case TAB -> p.setPlayerListHeaderFooter("", lastTabFooter);
                case OFF -> { /* nothing to refresh */ }
            }
        }
    }

    /** Most recent tab-footer text, reused when a player switches onto the TAB surface between renders. */
    private volatile String lastTabFooter = "";

    /**
     * Applies a surface to one player, clearing whichever surface they are leaving (#1793) — without
     * this a switch would leave a stale sidebar or a stale tab footer behind.
     */
    private void applyNow(Player player, Surface target) {
        // Always clear the tab footer first; it is re-set below only when TAB is the target.
        if (target != Surface.TAB) {
            player.setPlayerListHeaderFooter("", "");
        }
        applyNow(player, target == Surface.SIDEBAR);
        if (target == Surface.TAB) {
            player.setPlayerListHeaderFooter("", lastTabFooter);
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
     * Sets the player's roster surface and persists it (#1793).
     *
     * @param player  the player
     * @param target  the surface to switch to
     * @return the surface now in effect
     */
    public Surface setSurface(Player player, Surface target) {
        UUID id = player.getUniqueId();
        surfaceCache.put(id, target);
        applyNow(player, target);

        if (prefs != null) {
            // Persist the surface in metadata, and keep the legacy boolean in step so anything still
            // reading presence_sidebar (or an older build during a rollback) sees a consistent state.
            prefs.getPreferences(id, PREF_PLUGIN_ID).thenAccept(dto -> {
                if (dto == null) return;
                dto.getMetadata().put(PREF_SURFACE_KEY, target.token());
                prefs.savePreferences(dto);
            }).exceptionally(e -> {
                logger.warning("Failed to persist presence surface for " + player.getName()
                        + ": " + e.getMessage());
                return null;
            });
            prefs.setNotificationEnabled(id, PREF_PLUGIN_ID, PREF_TYPE, target != Surface.OFF)
                 .exceptionally(e -> null);
        }
        return target;
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
        Surface target = show ? Surface.SIDEBAR : Surface.OFF;
        surfaceCache.put(id, target);
        applyNow(player, target);
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

            // blank() hides the score outright — simpler and more reliable than styling it black, and
            // visually the same result (the number stops competing with the name column).
            Object format = numberFormat.getMethod("blank").invoke(null);

            // Resolve the setter on the PUBLIC Objective interface, not obj.getClass(): the runtime
            // class is CraftObjective, which is not an exported/accessible type, so a Method resolved
            // from it fails with IllegalAccessException on invoke. Paper adds numberFormat to the
            // Objective interface itself, so this both compiles (spigot-api) and works (Paper).
            Class<?> objectiveApi = Class.forName("org.bukkit.scoreboard.Objective");
            objectiveApi.getMethod("numberFormat", numberFormat).invoke(obj, format);

            if (!numberStyleLogged) {
                numberStyleLogged = true;
                logger.info("Presence sidebar: score numbers hidden via Paper NumberFormat");
            }
        } catch (Throwable t) {
            // Non-Paper runtime or an API change — leave the default red numbers visible. Logged once
            // at WARNING (not debug) so a silent regression here is actually noticeable.
            if (!numberStyleLogged) {
                numberStyleLogged = true;
                logger.warning("Presence sidebar: could not hide score numbers ("
                        + t.getClass().getSimpleName() + ": " + t.getMessage()
                        + ") — they will render in red");
            }
        }
    }

    /** Guards the one-shot log in {@link #applyNumberStyle} — render() runs on every presence change. */
    private boolean numberStyleLogged = false;

    /** Clears cached visibility on quit. */
    public void forget(UUID id) {
        surfaceCache.remove(id);
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
