package org.fourz.rvnkcore.api.chat;

import com.google.gson.Gson;
import org.fourz.rvnkcore.api.model.ChatMessageDTO;
import org.fourz.rvnkcore.service.chatrelay.ChatMessageBuffer;
import org.fourz.rvnkcore.service.chatrelay.ChatStreamCursor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/** The SSE push (#2425): wire frames, push latency, heartbeat, and closing dead or stale streams. */
class ChatStreamHubTest {

    private final Gson gson = new Gson();
    private final AtomicReference<ChatMessageBuffer> live = new AtomicReference<>(new ChatMessageBuffer(500));
    private ChatStreamHub hub;

    private ChatStreamHub hub(long heartbeatMs, int max) {
        hub = new ChatStreamHub(live::get, e -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("seq", e.getSeq());
            m.put("message", e.getDto().getMessage());
            return m;
        }, gson::toJson, heartbeatMs, max);
        return hub;
    }

    @AfterEach
    void tearDown() {
        if (hub != null) hub.shutdown();
    }

    /** Collects frames; can be told to fail. */
    static final class FakeSink implements ChatStreamHub.Sink {
        final StringBuffer text = new StringBuffer();
        volatile boolean fail;
        volatile boolean closed;

        @Override public void send(String frame) throws IOException {
            if (fail) throw new IOException("broken pipe");
            text.append(frame);
        }
        @Override public void close() { closed = true; }

        boolean await(String needle, long ms) throws InterruptedException {
            long end = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(ms);
            while (System.nanoTime() < end) {
                if (text.indexOf(needle) >= 0) return true;
                Thread.sleep(5);
            }
            return text.indexOf(needle) >= 0;
        }
    }

    private static ChatMessageDTO dto(String message) {
        ChatMessageDTO d = new ChatMessageDTO(java.util.UUID.randomUUID().toString(), "test", "global",
                null, "tester", message, System.currentTimeMillis());
        d.setRoom("SERVER");
        return d;
    }

    private ChatStreamCursor cursor(String boot, long since) {
        return new ChatStreamCursor(live.get(), boot, since, 200, null, null);
    }

    @Test
    void openWritesHelloThenTheBacklog() throws Exception {
        ChatMessageBuffer b = live.get();
        b.record(dto("one"));
        b.record(dto("two"));
        FakeSink sink = new FakeSink();
        hub(60_000, 4).open(cursor(null, 0), sink);
        String t = sink.text.toString();
        assertTrue(t.startsWith("retry: 3000\nevent: hello\ndata: {\"bootId\":\"" + b.getBootId()
                + "\",\"seq\":2,\"stale\":true,\"heartbeatMs\":60000}\n\n"), t);
        assertTrue(t.contains("id: " + b.getBootId() + ":1\nevent: message\ndata: {\"seq\":1,\"message\":\"one\"}\n\n"), t);
        assertTrue(t.indexOf("\"one\"") < t.indexOf("\"two\""));
    }

    @Test
    void aNewLineReachesTheStreamWellUnderOneSecond() throws Exception {
        FakeSink sink = new FakeSink();
        hub(60_000, 4).open(cursor(live.get().getBootId(), 0), sink);
        long t0 = System.nanoTime();
        live.get().record(dto("pushed"));
        assertTrue(sink.await("\"pushed\"", 1000), "pushed line did not arrive");
        long ms = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t0);
        assertTrue(ms < 1000, "took " + ms + " ms");
        assertFalse(sink.text.toString().contains("event: stale"));
    }

    @Test
    void resumeWithAGoodCursorIsNotStale() throws Exception {
        ChatMessageBuffer b = live.get();
        b.record(dto("old"));
        b.record(dto("new"));
        FakeSink sink = new FakeSink();
        hub(60_000, 4).open(cursor(b.getBootId(), 1), sink);
        String t = sink.text.toString();
        assertTrue(t.contains("\"stale\":false"), t);
        assertFalse(t.contains("\"old\""));
        assertTrue(t.contains("\"new\""));
    }

    @Test
    void heartbeatIsAnSseComment() throws Exception {
        FakeSink sink = new FakeSink();
        hub(30, 4).open(cursor(null, 0), sink);
        assertTrue(sink.await(": heartbeat 0\n\n", 2000), sink.text.toString());
    }

    @Test
    void aFailedWriteClosesTheStream() throws Exception {
        FakeSink sink = new FakeSink();
        ChatStreamHub h = hub(30, 4);
        ChatStreamHub.Stream s = h.open(cursor(null, 0), sink);
        sink.fail = true;
        long end = System.currentTimeMillis() + 2000;
        while (!s.isClosed() && System.currentTimeMillis() < end) Thread.sleep(5);
        assertTrue(s.isClosed());
        assertTrue(sink.closed);
        assertEquals(0, h.openCount());
    }

    @Test
    void aReplacedBufferClosesTheStreamSoTheClientResyncs() throws Exception {
        FakeSink sink = new FakeSink();
        ChatStreamHub h = hub(30, 4);
        ChatStreamHub.Stream s = h.open(cursor(null, 0), sink);
        live.set(new ChatMessageBuffer(500)); // the relay service restarted
        long end = System.currentTimeMillis() + 2000;
        while (!s.isClosed() && System.currentTimeMillis() < end) Thread.sleep(5);
        assertTrue(s.isClosed());
    }

    @Test
    void theCapRefusesExtraStreams() throws Exception {
        ChatStreamHub h = hub(60_000, 1);
        ChatStreamHub.Stream first = h.open(cursor(null, 0), new FakeSink());
        assertThrows(ChatStreamHub.FullException.class, () -> h.open(cursor(null, 0), new FakeSink()));
        first.close();
        assertDoesNotThrow(() -> h.open(cursor(null, 0), new FakeSink()));
    }

    @Test
    void prettyJsonIsSplitIntoDataLines() {
        ChatStreamHub h = new ChatStreamHub(live::get, e -> Map.of(), o -> "{\n  \"a\": 1\n}", 60_000, 1);
        try {
            assertEquals("event: x\ndata: {\ndata:   \"a\": 1\ndata: }\n\n", h.event("x", Map.of()));
        } finally {
            h.shutdown();
        }
    }

    @Test
    void lastEventId() {
        assertArrayEquals(new String[] {"ab12cd34", "17"}, ChatStreamHub.parseLastEventId("ab12cd34:17"));
        assertNull(ChatStreamHub.parseLastEventId(null));
        assertNull(ChatStreamHub.parseLastEventId("ab12cd34"));
        assertNull(ChatStreamHub.parseLastEventId("ab12cd34:x"));
        assertNull(ChatStreamHub.parseLastEventId(":5"));
    }
}
