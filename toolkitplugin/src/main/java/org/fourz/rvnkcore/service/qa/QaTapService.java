package org.fourz.rvnkcore.service.qa;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.fourz.rvnkcore.config.ConfigLoader;
import org.fourz.rvnkcore.service.registry.ServiceRegistry;
import org.fourz.rvnkcore.util.log.LogManager;

/**
 * The QA message tap (#2359): records what the server sends to a QA test player, so a QA session can
 * read it over REST ({@code GET /v1/qa/inbox/{player}}) instead of asking the operator to read it back.
 *
 * <p><b>Gate.</b> It runs only when all three hold: {@code qa.inbox.enabled: true} in config.yml (off by
 * default), the server id is a QA tier ({@link #QA_TIERS}; prod is {@code nations} and is never one),
 * and BKCommonLib is enabled (it supplies the packet hook). Even then it records only players who hold
 * {@link #PERM_QA_SUBJECT}, the node {@code /quest debug fire} and {@code /rvnk npc click} already use.</p>
 *
 * <p><b>Read-only.</b> The hook is BKCommonLib's {@code PacketMonitor}, which observes a packet after the
 * send decision and cannot change or drop it.</p>
 *
 * <p><b>Classloading guard.</b> BKCommonLib types live only in {@code service.qa.bkc}, reached only after
 * the plugin check, the same rule as the NPC bridge. {@code NpcBridgeClassLoadingTest} covers it.</p>
 */
public final class QaTapService implements Listener {

    /** Server ids where the tap may run. */
    public static final Set<String> QA_TIERS = Set.of("dev", "test", "event");
    /** The QA subject node; declared by RVNKCore's plugin.yml, default false. */
    public static final String PERM_QA_SUBJECT = "rvnkcore.qa.subject";
    static final String BKCOMMONLIB = "BKCommonLib";
    static final String CONFIG_ENABLED = "qa.inbox.enabled";
    static final String CONFIG_CAPACITY = "qa.inbox.capacity";
    /** Re-read subject permissions every 10 s, for a node granted while the player is online. */
    static final long REFRESH_TICKS = 200L;

    private final QaInbox inbox;
    private final Set<UUID> subjects = ConcurrentHashMap.newKeySet();
    /** Lower-case name to UUID for every player who was a subject this run, so a lookup works after quit. */
    private final Map<String, UUID> namesSeen = new ConcurrentHashMap<>();
    private final AtomicLong failures = new AtomicLong();
    private volatile boolean active;
    private volatile String reason = "not started";
    private LogManager logger;
    private Runnable uninstall;
    private BukkitTask refresh;

    QaTapService(QaInbox inbox) {
        this.inbox = inbox;
    }

    /**
     * Why the tap must stay off, or null when it may run. Pure, so the gate is unit-tested.
     */
    static String refusal(boolean configEnabled, String serverId, boolean bkcEnabled) {
        if (!configEnabled) return CONFIG_ENABLED + " is false";
        String tier = serverId == null ? "" : serverId.trim().toLowerCase(Locale.ROOT);
        if (!QA_TIERS.contains(tier)) return "server id '" + serverId + "' is not a QA tier " + QA_TIERS;
        if (!bkcEnabled) return BKCOMMONLIB + " is not enabled";
        return null;
    }

    /** Registers the service (always, so REST can say why it is off) and starts the tap when the gate allows. */
    public static QaTapService install(Plugin plugin, ServiceRegistry registry, LogManager logger) {
        QaTapService service = new QaTapService(new QaInbox(plugin.getConfig().getInt(CONFIG_CAPACITY, 200)));
        service.logger = logger;
        registry.registerService(QaTapService.class, service);

        String refused = refusal(plugin.getConfig().getBoolean(CONFIG_ENABLED, false),
                ConfigLoader.getInstance(plugin).getServerId(),
                Bukkit.getPluginManager().isPluginEnabled(BKCOMMONLIB));
        if (refused != null) {
            service.reason = refused;
            logger.debug("QA inbox off: " + refused);
            return service;
        }
        try {
            service.uninstall = org.fourz.rvnkcore.service.qa.bkc.BkcQaPacketTap.install(plugin, service, logger);
        } catch (RuntimeException | LinkageError e) {
            service.reason = "BKCommonLib packet hook failed: " + e;
            logger.warning("QA inbox off: " + service.reason);
            return service;
        }
        Bukkit.getPluginManager().registerEvents(service, plugin);
        service.refresh = Bukkit.getScheduler().runTaskTimer(plugin, service::refreshSubjects, 20L, REFRESH_TICKS);
        service.active = true;
        service.reason = null;
        logger.info("QA inbox on: recording messages sent to " + PERM_QA_SUBJECT + " players (capacity "
                + service.inbox.capacity() + " lines each, GET /v1/qa/inbox/{player})");
        return service;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        refresh(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        // Lines stay readable after the quit (a kick message is worth reading); only recording stops.
        subjects.remove(event.getPlayer().getUniqueId());
    }

    void refreshSubjects() {
        for (Player p : Bukkit.getOnlinePlayers()) refresh(p);
    }

    private void refresh(Player p) {
        if (p.hasPermission(PERM_QA_SUBJECT)) {
            subjects.add(p.getUniqueId());
            namesSeen.put(p.getName().toLowerCase(Locale.ROOT), p.getUniqueId());
        } else {
            subjects.remove(p.getUniqueId());
        }
    }

    /** Called by the packet hook on a network thread. Never throws. */
    public void onPacket(UUID player, Object packet) {
        if (!active || player == null || !subjects.contains(player)) return;
        try {
            String kind = QaPacketText.kindOf(packet);
            if (kind == null) return;
            String text = QaPacketText.textOf(packet);
            if (text == null) {
                if (failures.incrementAndGet() == 1 && logger != null) {
                    logger.warning("QA inbox: could not read text from " + packet.getClass().getName()
                            + " (logged once; further misses are counted)");
                }
                return;
            }
            inbox.record(player, kind, text, System.currentTimeMillis());
        } catch (RuntimeException e) {
            failures.incrementAndGet();
        }
    }

    /** The UUID for a name or UUID string: a subject seen this run, else null. */
    public UUID resolve(String nameOrUuid) {
        if (nameOrUuid == null || nameOrUuid.isBlank()) return null;
        try {
            return UUID.fromString(nameOrUuid.trim());
        } catch (IllegalArgumentException notUuid) {
            return namesSeen.get(nameOrUuid.trim().toLowerCase(Locale.ROOT));
        }
    }

    public boolean isActive() {
        return active;
    }

    /** Why the tap is off, or null when it runs. */
    public String getReason() {
        return reason;
    }

    public boolean isSubject(UUID player) {
        return subjects.contains(player);
    }

    public long getFailures() {
        return failures.get();
    }

    public QaInbox getInbox() {
        return inbox;
    }

    public void shutdown() {
        active = false;
        reason = "shut down";
        if (refresh != null) {
            refresh.cancel();
            refresh = null;
        }
        if (uninstall != null) {
            try {
                uninstall.run();
            } catch (RuntimeException | LinkageError ignored) {
                // BKCommonLib may already be disabled at shutdown.
            }
            uninstall = null;
        }
        HandlerList.unregisterAll(this);
    }
}
