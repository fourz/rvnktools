package org.fourz.rvnkcore.service.chatrelay;

import org.fourz.rvnkcore.api.model.ChatMessageDTO;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Bounded in-memory tail of recent chat, so a client can poll for what was said instead of scraping
 * the console log (#1869).
 *
 * <h3>Cursor</h3>
 * Every recorded message is stamped with a monotonic {@code seq} starting at 1. A client polls with
 * the last {@code seq} it saw and receives everything after it. Because the buffer is memory-only,
 * the sequence restarts on every plugin enable — so the buffer also carries a {@code bootId} minted
 * at construction. A client that sends a {@code bootId} which no longer matches is told
 * {@link Page#stale}, and resyncs from the buffer head.
 *
 * <p>Without that marker a post-restart client sits waiting on a {@code seq} the server has already
 * reset past and <em>silently stops receiving chat</em> — no error, no way to tell "nothing was said"
 * from "I am broken". {@code stale} is also raised when the client has fallen so far behind that
 * messages were evicted before it read them, so a gap is reported rather than hidden.</p>
 *
 * <h3>Thread safety</h3>
 * Writes arrive from the main thread and from async chat threads; reads arrive on Jetty request
 * threads. Every public method is {@code synchronized} on this instance. Reads copy the matching
 * slice out under the lock and return an immutable list, so a caller never iterates live state.
 * (The {@code AtomicReference}-snapshot approach used by {@code LiveDataCache} does not fit here —
 * that cache is refreshed on a timer, whereas this one is written per chat event.)
 *
 * <h3>Not persisted</h3>
 * No table, no migration. Losing the buffer on restart is expected and is exactly what
 * {@code bootId} exists to communicate.
 *
 * @since 1.5.68
 */
public final class ChatMessageBuffer {

    /** Default retained-message count when {@code chat-relay.buffer.size} is unset. */
    public static final int DEFAULT_SIZE = 500;

    private static final int MIN_SIZE = 1;
    private static final int MAX_SIZE = 10_000;

    /** Hard ceiling on messages returned by one poll, whatever the caller asks for. */
    public static final int MAX_LIMIT = 200;

    private final String bootId;
    private final ArrayDeque<Entry> entries = new ArrayDeque<>();
    private final Set<String> recordedIds = new HashSet<>();

    private int capacity;
    private long nextSeq = 1L;

    /**
     * Creates a buffer with a fresh {@code bootId}.
     *
     * @param capacity Requested retained-message count; clamped to [{@value MIN_SIZE}, {@value MAX_SIZE}]
     */
    public ChatMessageBuffer(int capacity) {
        this.bootId = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        this.capacity = clamp(capacity);
    }

    /** One retained message and the cursor position assigned to it. */
    public static final class Entry {
        private final long seq;
        private final ChatMessageDTO dto;

        Entry(long seq, ChatMessageDTO dto) {
            this.seq = seq;
            this.dto = dto;
        }

        public long getSeq() { return seq; }

        public ChatMessageDTO getDto() { return dto; }
    }

    /** Result of a cursor read: the messages, where the cursor now sits, and whether it was stale. */
    public static final class Page {
        private final String bootId;
        private final long seq;
        private final boolean stale;
        private final List<Entry> messages;

        Page(String bootId, long seq, boolean stale, List<Entry> messages) {
            this.bootId = bootId;
            this.seq = seq;
            this.stale = stale;
            this.messages = messages;
        }

        /** Buffer identity for this plugin lifetime; send it back on the next poll. */
        public String getBootId() { return bootId; }

        /** Cursor to poll from next — the highest seq in {@link #getMessages()}, else the buffer head. */
        public long getSeq() { return seq; }

        /** True when the supplied cursor could not be honoured and the client must resync. */
        public boolean isStale() { return stale; }

        public List<Entry> getMessages() { return messages; }
    }

    /**
     * Records a message, evicting the oldest entry when full.
     *
     * <p>Ignores a {@code msgId} already present. Both RVNKEvents' unconditional record and
     * RVNKCore's relay path can see the same GLOBAL line, and it must appear once (#1871).</p>
     *
     * @param dto The message; ignored when null or missing a {@code msgId}
     * @return true when a new entry was added
     */
    public synchronized boolean record(ChatMessageDTO dto) {
        if (dto == null || dto.getMsgId() == null || dto.getMsgId().isEmpty()) return false;
        if (!recordedIds.add(dto.getMsgId())) return false;

        entries.addLast(new Entry(nextSeq++, dto));
        evictToCapacity();
        return true;
    }

    /**
     * Reads everything after {@code sinceSeq}, oldest first.
     *
     * @param clientBootId Buffer identity the client last saw; null or mismatched forces a resync
     * @param sinceSeq     Highest seq the client has already handled; {@code <= 0} means "from the head"
     * @param limit        Maximum messages to return, clamped to [1, {@value MAX_LIMIT}]
     * @param room         Room filter (case-insensitive), or null for every room
     * @param world        World filter (case-insensitive — world names vary in case, #1627), or null
     * @return A page; never null, possibly empty
     */
    public synchronized Page since(String clientBootId, long sinceSeq, int limit,
                                   String room, String world) {
        int cap = limit <= 0 ? MAX_LIMIT : Math.min(limit, MAX_LIMIT);

        boolean bootMismatch = clientBootId == null
                || clientBootId.isEmpty()
                || !bootId.equals(clientBootId);

        long oldest = entries.isEmpty() ? nextSeq : entries.peekFirst().getSeq();
        // The client's next expected message is sinceSeq + 1. If the buffer has already evicted past
        // that point, messages were lost between polls — report the gap instead of hiding it.
        boolean gap = !bootMismatch && sinceSeq > 0 && (sinceSeq + 1) < oldest;

        boolean stale = bootMismatch || gap;
        long effectiveSince = stale ? 0L : sinceSeq;

        List<Entry> out = new ArrayList<>();
        for (Entry e : entries) {
            if (e.getSeq() <= effectiveSince) continue;
            if (!matches(e.getDto(), room, world)) continue;
            out.add(e);
            if (out.size() >= cap) break;
        }

        // Cursor advances to the last message actually returned. When a filter matched nothing, hold
        // the caller's position rather than skipping past unread messages they filtered out.
        long cursor;
        if (!out.isEmpty()) {
            cursor = out.get(out.size() - 1).getSeq();
        } else if (stale) {
            cursor = entries.isEmpty() ? nextSeq - 1 : entries.peekLast().getSeq();
        } else {
            cursor = sinceSeq;
        }

        return new Page(bootId, cursor, stale, Collections.unmodifiableList(out));
    }

    private static boolean matches(ChatMessageDTO dto, String room, String world) {
        if (room != null && !room.isEmpty()) {
            if (dto.getRoom() == null || !dto.getRoom().equalsIgnoreCase(room)) return false;
        }
        if (world != null && !world.isEmpty()) {
            if (dto.getWorld() == null || !dto.getWorld().equalsIgnoreCase(world)) return false;
        }
        return true;
    }

    /**
     * Applies a new capacity, trimming immediately when it shrank.
     *
     * <p>Called from {@code refreshConfig} so {@code chat-relay.buffer.size} takes effect on reload
     * rather than only at boot.</p>
     *
     * @param newCapacity Requested size; clamped to [{@value MIN_SIZE}, {@value MAX_SIZE}]
     */
    public synchronized void resize(int newCapacity) {
        this.capacity = clamp(newCapacity);
        evictToCapacity();
    }

    /** @return Buffer identity for this plugin lifetime. */
    public String getBootId() { return bootId; }

    /** @return Seq of the newest retained message, or 0 when empty. */
    public synchronized long currentSeq() {
        return entries.isEmpty() ? 0L : entries.peekLast().getSeq();
    }

    /** @return Retained message count. */
    public synchronized int size() { return entries.size(); }

    /** @return Effective capacity after clamping. */
    public synchronized int capacity() { return capacity; }

    private void evictToCapacity() {
        while (entries.size() > capacity) {
            Entry dropped = entries.removeFirst();
            recordedIds.remove(dropped.getDto().getMsgId());
        }
        // Defensive: recordedIds tracks exactly what the deque holds, but a msgId collision on an
        // evicted entry would otherwise leak. Rebuild if the two ever diverge.
        if (recordedIds.size() > entries.size()) {
            recordedIds.clear();
            for (Iterator<Entry> it = entries.iterator(); it.hasNext(); ) {
                recordedIds.add(it.next().getDto().getMsgId());
            }
        }
    }

    private static int clamp(int value) {
        if (value < MIN_SIZE) return DEFAULT_SIZE;
        return Math.min(value, MAX_SIZE);
    }
}
