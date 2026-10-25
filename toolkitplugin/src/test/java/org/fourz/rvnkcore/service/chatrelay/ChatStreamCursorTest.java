package org.fourz.rvnkcore.service.chatrelay;

import org.fourz.rvnkcore.api.model.ChatMessageDTO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** The /v1/chat/stream cursor (#2425): the same stale/gap rules as /recent, plus a full drain. */
class ChatStreamCursorTest {

    private static ChatMessageDTO dto(String room, String message) {
        ChatMessageDTO d = new ChatMessageDTO(java.util.UUID.randomUUID().toString(), "test", "global",
                null, "tester", message, System.currentTimeMillis());
        d.setRoom(room);
        return d;
    }

    private static void fill(ChatMessageBuffer b, String room, int n) {
        for (int i = 0; i < n; i++) b.record(dto(room, "m"));
    }

    private static List<Long> seqs(ChatStreamCursor.Batch batch) {
        return batch.messages().stream().map(ChatMessageBuffer.Entry::getSeq).toList();
    }

    @Test
    void noBootIsStaleAndResyncsToTheNewestLines() {
        ChatMessageBuffer b = new ChatMessageBuffer(500);
        fill(b, "GLOBAL", 10);
        ChatStreamCursor.Batch open = new ChatStreamCursor(b, null, 0, 3, null, null).read();
        assertTrue(open.stale(), "same as /recent: a cursorless read is a resync");
        assertEquals(List.of(8L, 9L, 10L), seqs(open));
        assertEquals(b.getBootId(), open.bootId());
        assertEquals(10L, open.seq());
    }

    @Test
    void oldBootAfterARestartIsStale() {
        ChatMessageBuffer b = new ChatMessageBuffer(500);
        fill(b, "GLOBAL", 4);
        ChatStreamCursor.Batch open = new ChatStreamCursor(b, "deadbeef", 99, 200, null, null).read();
        assertTrue(open.stale());
        assertEquals(List.of(1L, 2L, 3L, 4L), seqs(open));
        assertEquals(4L, open.seq());
    }

    @Test
    void goodCursorResumesWithoutStaleAndThenReadsOnlyNewLines() {
        ChatMessageBuffer b = new ChatMessageBuffer(500);
        fill(b, "GLOBAL", 5);
        ChatStreamCursor c = new ChatStreamCursor(b, b.getBootId(), 3, 200, null, null);
        ChatStreamCursor.Batch open = c.read();
        assertFalse(open.stale());
        assertEquals(List.of(4L, 5L), seqs(open));
        assertTrue(c.read().messages().isEmpty(), "nothing new");
        fill(b, "GLOBAL", 2);
        ChatStreamCursor.Batch next = c.read();
        assertFalse(next.stale());
        assertEquals(List.of(6L, 7L), seqs(next));
    }

    @Test
    void backlogLongerThanOnePageIsDrainedInFull() {
        ChatMessageBuffer b = new ChatMessageBuffer(1000);
        fill(b, "GLOBAL", 450);
        ChatStreamCursor c = new ChatStreamCursor(b, b.getBootId(), 10, 200, null, null);
        ChatStreamCursor.Batch open = c.read();
        assertFalse(open.stale());
        assertEquals(440, open.messages().size(), "every line after the cursor, not one page");
        assertEquals(11L, seqs(open).get(0));
        assertEquals(450L, open.seq());
    }

    @Test
    void evictedCursorIsAGap() {
        ChatMessageBuffer b = new ChatMessageBuffer(5);
        fill(b, "GLOBAL", 20);
        ChatStreamCursor.Batch open = new ChatStreamCursor(b, b.getBootId(), 2, 200, null, null).read();
        assertTrue(open.stale(), "lines 3..15 were evicted unread");
        assertEquals(List.of(16L, 17L, 18L, 19L, 20L), seqs(open));
    }

    @Test
    void filteredReaderFollowsTheHeadSoOtherRoomsDoNotFakeAGap() {
        // A SERVER-room reader while only GLOBAL talks. Without moving the cursor to the head, the
        // reader keeps seq 1 and the eviction of GLOBAL lines later reports a false gap.
        ChatMessageBuffer b = new ChatMessageBuffer(5);
        b.record(dto("SERVER", "hello"));
        ChatStreamCursor c = new ChatStreamCursor(b, b.getBootId(), 0, 200, "SERVER", null);
        c.read();
        for (int i = 0; i < 20; i++) {
            b.record(dto("GLOBAL", "noise"));
            ChatStreamCursor.Batch quiet = c.read();
            assertFalse(quiet.stale(), "read " + i);
            assertTrue(quiet.messages().isEmpty());
        }
        assertEquals(21L, c.seq());
        b.record(dto("SERVER", "again"));
        ChatStreamCursor.Batch next = c.read();
        assertFalse(next.stale());
        assertEquals(List.of(22L), seqs(next));
    }

    @Test
    void pageReportsTheHead() {
        ChatMessageBuffer b = new ChatMessageBuffer(500);
        assertEquals(0L, b.since(null, 0, 10, null, null).getHead());
        fill(b, "GLOBAL", 3);
        assertEquals(3L, b.since(b.getBootId(), 0, 1, "NONE", null).getHead());
    }

    @Test
    void listenerRunsAfterEachNewLineOnly() {
        ChatMessageBuffer b = new ChatMessageBuffer(500);
        int[] calls = {0};
        b.addListener(() -> calls[0]++);
        b.addListener(() -> { throw new IllegalStateException("a bad listener"); });
        ChatMessageDTO d = dto("GLOBAL", "x");
        assertTrue(b.record(d), "a throwing listener must not cost the line");
        assertFalse(b.record(d), "duplicate msgId");
        assertEquals(1, calls[0]);
    }
}
