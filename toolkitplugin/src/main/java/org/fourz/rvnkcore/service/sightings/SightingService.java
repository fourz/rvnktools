package org.fourz.rvnkcore.service.sightings;

import java.net.InetAddress;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import java.util.regex.Pattern;

/**
 * Player sightings and entitled lists, pulled by z-office (#2351).
 *
 * <p>Never blocks gameplay: a join only queues work. One writer thread stores sightings and
 * entitlement changes; its queue is bounded and drops a sighting when full. Reads for the API run on
 * their own small pool. Only a public IPv4 address is recorded; anything else records nothing.</p>
 */
public class SightingService {

    /** Entitled list names: lower case, digits and dashes. */
    public static final Pattern LIST = Pattern.compile("[a-z0-9-]{1,32}");
    public static final int MAX_PAGE = 500;
    static final int WRITE_QUEUE = 256;

    /** Where outcomes go. */
    public interface Log {
        void info(String message);
        void warning(String message);
    }

    private volatile SightingsConfig config;
    private final SightingStore sightings;
    private final EntitlementStore entitlements;
    private final Log log;
    private final LongSupplier clock;
    private final ThreadPoolExecutor writer;
    private final ExecutorService reader;

    public SightingService(SightingsConfig config, SightingStore sightings, EntitlementStore entitlements,
                           Log log, LongSupplier clock) {
        this.config = config;
        this.sightings = sightings;
        this.entitlements = entitlements;
        this.log = log;
        this.clock = clock;
        this.writer = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(WRITE_QUEUE),
                daemon("rvnkcore-sightings-write"),
                (r, ex) -> log.warning("net-sightings: write queue full, dropped a sighting"));
        this.reader = new ThreadPoolExecutor(2, 2, 0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(32),
                daemon("rvnkcore-sightings-read"), new ThreadPoolExecutor.AbortPolicy());
        SightingsReadToken.configure(config.tokenHashes());
    }

    public SightingsConfig config() {
        return config;
    }

    /** Applies a reloaded config: the enabled flag, server name, retention and read tokens. */
    public void refreshConfig(SightingsConfig fresh) {
        this.config = fresh;
        int tokens = SightingsReadToken.configure(fresh.tokenHashes());
        log.info("net-sightings: config refreshed - enabled=" + fresh.enabled() + ", server=" + fresh.server()
                + ", retention=" + fresh.retentionDays() + "d, read tokens=" + tokens);
    }

    /**
     * Queues a join sighting. Returns at once.
     *
     * @return false when nothing was queued (off, or the address is not a public IPv4)
     */
    public boolean recordJoin(UUID uuid, String name, InetAddress address) {
        return record(uuid, name, address, Sighting.JOIN);
    }

    private boolean record(UUID uuid, String name, InetAddress address, String reason) {
        SightingsConfig c = config;
        if (!c.enabled() || uuid == null) return false;
        Optional<String> ip = address == null ? Optional.empty() : PublicIpv4.of(address);
        if (ip.isEmpty()) return false;
        long at = clock.getAsLong();
        writer.execute(() -> {
            List<String> lists;
            try {
                lists = entitlements.lists(uuid);
            } catch (SQLException e) {
                // A sighting with no lists only means "no renew" to z-office, never "revoke".
                log.warning("net-sightings: entitlement read failed for " + name + ": " + e.getMessage());
                lists = List.of();
            }
            try {
                sightings.append(new Sighting(0L, uuid, name == null ? "" : name, ip.get(), c.server(), at, reason, lists));
            } catch (SQLException e) {
                log.warning("net-sightings: could not store a sighting for " + name + ": " + e.getMessage());
            }
        });
        return true;
    }

    /** Result of a grant or a revoke. */
    public record Change(boolean changed, boolean sighted, List<String> lists) { }

    /**
     * Grants a list. When {@code address} is a public IPv4 (the player is online), an
     * {@code entitlement} sighting follows at once, so z-office sees the grant without a new login.
     */
    public CompletableFuture<Change> grant(UUID uuid, String name, String list, String by, InetAddress address) {
        String l = normaliseList(list);
        return change(() -> entitlements.grant(uuid, l, by), uuid, name, address);
    }

    /** Revokes a list. z-office never revokes from a sighting; access then ends by its own expiry. */
    public CompletableFuture<Change> revoke(UUID uuid, String name, String list, InetAddress address) {
        String l = normaliseList(list);
        return change(() -> entitlements.revoke(uuid, l), uuid, name, address);
    }

    public CompletableFuture<List<String>> lists(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return entitlements.lists(uuid);
            } catch (SQLException e) {
                throw new CompletionException(e);
            }
        }, reader);
    }

    private interface Sql<T> {
        T run() throws SQLException;
    }

    private CompletableFuture<Change> change(Sql<Boolean> op, UUID uuid, String name, InetAddress address) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                boolean changed = op.run();
                List<String> lists = entitlements.lists(uuid);
                boolean sighted = changed && record(uuid, name, address, Sighting.ENTITLEMENT);
                return new Change(changed, sighted, lists);
            } catch (SQLException e) {
                throw new CompletionException(e);
            }
        }, reader);
    }

    /** One API page. {@code limit} is clamped to 1..{@value #MAX_PAGE}. */
    public CompletableFuture<SightingStore.Page> page(long after, int limit) {
        int n = Math.max(1, Math.min(limit, MAX_PAGE));
        return CompletableFuture.supplyAsync(() -> {
            try {
                return sightings.after(Math.max(0L, after), n);
            } catch (SQLException e) {
                throw new CompletionException(e);
            }
        }, reader);
    }

    /** Deletes rows past retention. Blocks; call from an async task. */
    public int pruneNow() {
        long cutoff = clock.getAsLong() - TimeUnit.DAYS.toMillis(config.retentionDays());
        try {
            int n = sightings.prune(cutoff);
            if (n > 0) log.info("net-sightings: pruned " + n + " sighting(s) older than " + config.retentionDays() + "d");
            return n;
        } catch (SQLException e) {
            log.warning("net-sightings: prune failed: " + e.getMessage());
            return 0;
        }
    }

    public void shutdown() {
        writer.shutdown();
        reader.shutdownNow();
        try {
            writer.awaitTermination(3, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        writer.shutdownNow();
    }

    /** Lower-cases and checks a list name. */
    public static String normaliseList(String list) {
        String l = list == null ? "" : list.trim().toLowerCase(Locale.ROOT);
        if (!LIST.matcher(l).matches()) throw new IllegalArgumentException("Invalid list name: " + list);
        return l;
    }

    /** True when the page cursor is older than the oldest kept row, so z-office missed pruned rows. */
    public static boolean gap(long after, long oldestId) {
        return after > 0 && oldestId > 0 && after < oldestId - 1;
    }

    private static java.util.concurrent.ThreadFactory daemon(String name) {
        return r -> {
            Thread t = new Thread(r, name);
            t.setDaemon(true);
            return t;
        };
    }
}
