package org.fourz.rvnkcore.api.chat;

import org.fourz.rvnkcore.service.chatrelay.ChatMessageBuffer;
import org.fourz.rvnkcore.service.chatrelay.ChatStreamCursor;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Pushes chat to {@code GET /v1/chat/stream} readers as Server-Sent Events (#2425).
 *
 * <h3>No parked threads</h3>
 * The servlet starts async and returns its Jetty thread at once (#1552). A new chat line only
 * schedules a drain on this hub's two daemon threads; the drain reads the reader's
 * {@link ChatStreamCursor} and writes the frames. Writes for one reader are serialised; a reader that
 * stops reading blocks a hub thread at most until the connector idle timeout fails the write, and is
 * then closed. Readers are capped ({@code maxStreams}): this is for a few tools, not for browsers.
 *
 * <h3>Wire</h3>
 * <pre>
 * retry: 3000
 * event: hello      data: {"bootId","seq","stale","heartbeatMs"}   - once, before the backlog
 * id: boot:seq
 * event: message    data: { same shape as one /v1/chat/recent message }
 * event: stale      data: {"bootId","seq","stale":true}           - lines were evicted unread
 * : heartbeat seq   - every heartbeatMs, so a client can tell a quiet stream from a dead one
 * </pre>
 * After a plugin restart the connection drops; a client that reconnects with its old {@code boot}
 * gets {@code hello} with {@code stale:true} and the newest lines, the same as {@code /recent}.
 */
public final class ChatStreamHub {

    /** Where frames go. Implementations write and flush; {@link #close} ends the response. */
    public interface Sink {
        void send(String frame) throws IOException;
        void close();
    }

    /** Thrown when {@code maxStreams} readers are already open. */
    public static final class FullException extends Exception {
        FullException(int max) { super("At most " + max + " chat streams may be open"); }
    }

    private final Supplier<ChatMessageBuffer> currentBuffer;
    private final Function<ChatMessageBuffer.Entry, Map<String, Object>> wire;
    private final Function<Object, String> json;
    private final long heartbeatMs;
    private final int maxStreams;
    private final ScheduledThreadPoolExecutor pool;
    private final Set<Stream> streams = ConcurrentHashMap.newKeySet();
    private final Set<ChatMessageBuffer> hooked = ConcurrentHashMap.newKeySet();
    private final ScheduledFuture<?> heartbeat;

    /**
     * @param currentBuffer the live buffer, or null when the chat relay is gone; a stream on any other
     *                      buffer is closed at the next heartbeat so its client reconnects
     * @param wire          one entry in the {@code /v1/chat/recent} message shape
     * @param json          serialiser for event data
     */
    public ChatStreamHub(Supplier<ChatMessageBuffer> currentBuffer,
                         Function<ChatMessageBuffer.Entry, Map<String, Object>> wire,
                         Function<Object, String> json, long heartbeatMs, int maxStreams) {
        this.currentBuffer = currentBuffer;
        this.wire = wire;
        this.json = json;
        this.heartbeatMs = heartbeatMs;
        this.maxStreams = maxStreams;
        this.pool = new ScheduledThreadPoolExecutor(2, r -> {
            Thread t = new Thread(r, "rvnkcore-chat-stream");
            t.setDaemon(true);
            return t;
        });
        this.pool.setRemoveOnCancelPolicy(true);
        this.heartbeat = pool.scheduleWithFixedDelay(this::beat, heartbeatMs, heartbeatMs, TimeUnit.MILLISECONDS);
    }

    public int openCount() {
        return streams.size();
    }

    /**
     * Opens a reader and writes {@code hello} plus the backlog before returning.
     *
     * @return the stream; close it when the client goes away
     * @throws FullException when the cap is reached; nothing was written
     */
    public Stream open(ChatStreamCursor cursor, Sink sink) throws FullException {
        Stream s = new Stream(cursor, sink);
        synchronized (streams) {
            if (streams.size() >= maxStreams) throw new FullException(maxStreams);
            streams.add(s);
        }
        ChatMessageBuffer buffer = cursor.buffer();
        if (hooked.add(buffer)) buffer.addListener(() -> wake(buffer));
        s.open();
        return s;
    }

    /** Closes every stream and stops the threads. */
    public void shutdown() {
        heartbeat.cancel(false);
        for (Stream s : streams) s.close();
        pool.shutdownNow();
    }

    private void wake(ChatMessageBuffer buffer) {
        for (Stream s : streams) {
            if (s.cursor.buffer() == buffer) s.schedule();
        }
    }

    private void beat() {
        ChatMessageBuffer live = currentBuffer.get();
        for (Stream s : streams) {
            if (s.cursor.buffer() != live) {
                s.close(); // the relay restarted under us: make the client reconnect and resync
            } else {
                s.heartbeat();
            }
        }
    }

    /** One open reader. */
    public final class Stream {
        private final ChatStreamCursor cursor;
        private final Sink sink;
        private final AtomicBoolean pending = new AtomicBoolean();
        private final AtomicBoolean closed = new AtomicBoolean();
        /** Guarded by this. A drain that wins the race to the lock before open() must not read. */
        private boolean opened;

        private Stream(ChatStreamCursor cursor, Sink sink) {
            this.cursor = cursor;
            this.sink = sink;
        }

        public boolean isClosed() {
            return closed.get();
        }

        /** Idempotent. Ends the response and stops pushes. */
        public void close() {
            if (!closed.compareAndSet(false, true)) return;
            streams.remove(this);
            try {
                sink.close();
            } catch (RuntimeException ignored) {
                // the connection is already gone
            }
        }

        private synchronized void open() {
            opened = true;
            ChatStreamCursor.Batch b = cursor.read();
            StringBuilder sb = new StringBuilder("retry: 3000\n");
            sb.append(event("hello", status(b, true)));
            appendMessages(sb, b);
            write(sb.toString());
        }

        /** Coalesces: one drain is queued however many lines arrive before it runs. */
        private void schedule() {
            if (closed.get() || !pending.compareAndSet(false, true)) return;
            try {
                pool.execute(this::drain);
            } catch (RuntimeException e) {
                pending.set(false); // pool shut down
            }
        }

        private synchronized void drain() {
            pending.set(false);
            if (closed.get() || !opened) return; // open() reads everything up to now
            ChatStreamCursor.Batch b = cursor.read();
            if (b.messages().isEmpty() && !b.stale()) return;
            StringBuilder sb = new StringBuilder();
            if (b.stale()) sb.append(event("stale", status(b, false)));
            appendMessages(sb, b);
            write(sb.toString());
        }

        private synchronized void heartbeat() {
            if (!closed.get() && opened) write(": heartbeat " + cursor.seq() + "\n\n");
        }

        private void appendMessages(StringBuilder sb, ChatStreamCursor.Batch b) {
            for (ChatMessageBuffer.Entry e : b.messages()) {
                sb.append("id: ").append(b.bootId()).append(':').append(e.getSeq()).append('\n');
                sb.append(event("message", wire.apply(e)));
            }
        }

        private void write(String frame) {
            if (frame.isEmpty() || closed.get()) return;
            try {
                sink.send(frame);
            } catch (IOException | RuntimeException e) {
                close(); // client went away, or the write timed out
            }
        }
    }

    private Map<String, Object> status(ChatStreamCursor.Batch b, boolean hello) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("bootId", b.bootId());
        m.put("seq", b.seq());
        m.put("stale", b.stale());
        if (hello) m.put("heartbeatMs", heartbeatMs);
        return m;
    }

    /** One SSE event. A serialiser that pretty-prints is split over several {@code data:} lines. */
    String event(String name, Object data) {
        StringBuilder sb = new StringBuilder("event: ").append(name).append('\n');
        for (String line : json.apply(data).split("\r?\n", -1)) sb.append("data: ").append(line).append('\n');
        return sb.append('\n').toString();
    }

    /**
     * Parses an EventSource {@code Last-Event-ID} ({@code boot:seq}) into {@code [boot, seq]}, or
     * null when it is absent or malformed. Lets a browser-style client resume without query params.
     */
    public static String[] parseLastEventId(String header) {
        if (header == null) return null;
        int i = header.lastIndexOf(':');
        if (i <= 0 || i == header.length() - 1) return null;
        String seq = header.substring(i + 1).trim();
        try {
            Long.parseLong(seq);
        } catch (NumberFormatException e) {
            return null;
        }
        return new String[] {header.substring(0, i).trim(), seq};
    }
}
