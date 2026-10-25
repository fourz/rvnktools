package org.fourz.rvnkcore.service.chatrelay;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One {@code /v1/chat/stream} reader's position in a {@link ChatMessageBuffer} (#2425).
 *
 * <p>It reads through {@link ChatMessageBuffer#since}, so it has the same cursor semantics as
 * {@code /v1/chat/recent}: a missing or old {@code boot} is {@code stale} and resyncs to the newest
 * lines, and a cursor the buffer has evicted past is a {@code stale} gap. The first read is the open:
 * the client's cursor, then everything after it. Each later read returns only new lines.</p>
 *
 * <p>Two differences from a poller. A read drains every page, not one. And after a read that was not
 * cut short, the cursor moves to the buffer head even when the room/world filter matched nothing.
 * Without that, a filtered reader holds an old cursor while other rooms talk, and eviction later
 * reports a false gap. Not thread-safe: the stream serialises reads per reader.</p>
 */
public final class ChatStreamCursor {

    /** One read: where the cursor now is, whether it was stale, and the lines, oldest first. */
    public record Batch(String bootId, long seq, boolean stale, List<ChatMessageBuffer.Entry> messages) { }

    private final ChatMessageBuffer buffer;
    private final String room;
    private final String world;
    private final int firstLimit;
    private String boot;
    private long seq;
    private boolean opened;

    /**
     * @param boot       the {@code bootId} the client last saw, or null
     * @param since      the last {@code seq} the client handled, or 0
     * @param firstLimit newest-lines cap on a resync, as {@code limit} on {@code /recent}
     */
    public ChatStreamCursor(ChatMessageBuffer buffer, String boot, long since, int firstLimit,
                            String room, String world) {
        this.buffer = buffer;
        this.boot = boot;
        this.seq = Math.max(0L, since);
        this.firstLimit = firstLimit <= 0 ? ChatMessageBuffer.MAX_LIMIT : Math.min(firstLimit, ChatMessageBuffer.MAX_LIMIT);
        this.room = room;
        this.world = world;
    }

    public ChatMessageBuffer buffer() { return buffer; }

    /** The cursor: send it back as {@code since} with {@link #bootId()} to resume. */
    public long seq() { return seq; }

    /** The buffer's boot id once opened, else what the client sent. */
    public String bootId() { return opened ? buffer.getBootId() : boot; }

    /** Reads everything after the cursor. The first call is the open. */
    public Batch read() {
        int limit = opened ? ChatMessageBuffer.MAX_LIMIT : firstLimit;
        String readBoot = opened ? buffer.getBootId() : boot;
        opened = true;

        ChatMessageBuffer.Page page = buffer.since(readBoot, seq, limit, room, world);
        boolean stale = page.isStale();
        List<ChatMessageBuffer.Entry> out = new ArrayList<>(page.getMessages());
        // Drain: a full page may have more behind it. Later pages use the fresh boot id and cursor.
        while (page.getMessages().size() >= limit) {
            limit = ChatMessageBuffer.MAX_LIMIT;
            page = buffer.since(page.getBootId(), page.getSeq(), limit, room, world);
            if (page.isStale()) {
                // Evicted while draining: the lines in between are gone. Report it, keep the newest.
                stale = true;
                out.clear();
            }
            out.addAll(page.getMessages());
        }
        // The last page was not full, so every entry up to its head was scanned.
        seq = Math.max(page.getSeq(), page.getHead());
        boot = page.getBootId();
        return new Batch(boot, seq, stale, Collections.unmodifiableList(out));
    }
}
