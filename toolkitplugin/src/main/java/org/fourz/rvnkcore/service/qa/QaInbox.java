package org.fourz.rvnkcore.service.qa;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * What a QA test player was sent (#2359): a bounded, in-memory list of plain-text lines per player.
 *
 * <p>One sequence counter is shared by every player, so a cursor is a plain number: a reader asks for
 * the lines after the last {@code seq} it saw. Gaps between one player's numbers are normal (another
 * player took them). Each player keeps the newest {@code capacity} lines; older ones are dropped.
 * Nothing is written to disk, and a restart empties it.</p>
 *
 * <p>Thread-safe: lines arrive on network threads and are read on Jetty threads.</p>
 */
public final class QaInbox {

    /** Hard bounds on the per-player capacity, whatever config.yml says. */
    static final int MIN_CAPACITY = 10;
    static final int MAX_CAPACITY = 2000;
    /** Hard bound on one page. */
    public static final int MAX_PAGE = 500;

    /** One line the player was sent. {@code kind} is chat, actionbar, title or subtitle. */
    public record Line(long seq, long at, String kind, String text) {
    }

    /** Lines after a cursor, oldest first; {@code cursor} is what to send as {@code since} next time. */
    public record Page(long cursor, List<Line> lines, boolean more) {
    }

    private final int capacity;
    private final AtomicLong seq = new AtomicLong();
    private final Map<UUID, ArrayDeque<Line>> byPlayer = new ConcurrentHashMap<>();

    public QaInbox(int capacity) {
        this.capacity = Math.max(MIN_CAPACITY, Math.min(MAX_CAPACITY, capacity));
    }

    public int capacity() {
        return capacity;
    }

    /** Records one line; blank text is ignored. */
    public void record(UUID player, String kind, String text, long now) {
        if (player == null || text == null || text.isBlank()) return;
        ArrayDeque<Line> lines = byPlayer.computeIfAbsent(player, k -> new ArrayDeque<>());
        synchronized (lines) {
            // Take the number inside the lock, so one player's lines stay in seq order.
            lines.addLast(new Line(seq.incrementAndGet(), now, kind, text));
            while (lines.size() > capacity) lines.removeFirst();
        }
    }

    /**
     * The player's lines with {@code seq > since}, oldest first, at most {@code limit}. When the page is
     * full, {@code cursor} is the last line returned and {@code more} is true; otherwise {@code cursor}
     * is the newest number handed out to anyone, so the next call skips everything already seen.
     */
    public Page since(UUID player, long since, int limit) {
        int max = Math.max(1, Math.min(MAX_PAGE, limit));
        long newest = seq.get();
        ArrayDeque<Line> lines = byPlayer.get(player);
        if (lines == null) return new Page(Math.max(since, newest), List.of(), false);
        List<Line> out = new ArrayList<>();
        boolean more = false;
        synchronized (lines) {
            for (Line l : lines) {
                if (l.seq() <= since) continue;
                if (out.size() == max) {
                    more = true;
                    break;
                }
                out.add(l);
            }
        }
        long cursor = more ? out.get(out.size() - 1).seq() : Math.max(since, newest);
        return new Page(cursor, List.copyOf(out), more);
    }

    /** Drops a player's lines. */
    public void clear(UUID player) {
        byPlayer.remove(player);
    }
}
