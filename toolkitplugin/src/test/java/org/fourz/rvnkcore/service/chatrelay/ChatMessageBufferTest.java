package org.fourz.rvnkcore.service.chatrelay;

import org.fourz.rvnkcore.api.model.ChatMessageDTO;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The tail buffer's read semantics (#2048).
 *
 * <p>The defect: a cursorless {@code since()} scanned head-first and returned the OLDEST
 * {@code limit} entries, so a caller asking for "recent" chat read a frozen window at the
 * start of the buffer forever, with every response well-formed. A cursorless read must
 * serve the NEWEST slice; a cursored read must keep forward pagination after {@code since}.</p>
 */
class ChatMessageBufferTest {

    private static ChatMessageDTO dto(String message) {
        ChatMessageDTO d = new ChatMessageDTO(
                java.util.UUID.randomUUID().toString(), "test", "global",
                null, "tester", message, System.currentTimeMillis());
        d.setRoom("GLOBAL");
        return d;
    }

    private static ChatMessageBuffer filled(int n) {
        ChatMessageBuffer buffer = new ChatMessageBuffer(500);
        for (int i = 1; i <= n; i++) {
            buffer.record(dto("m" + i));
        }
        return buffer;
    }

    private static List<Long> seqs(ChatMessageBuffer.Page page) {
        return page.getMessages().stream().map(ChatMessageBuffer.Entry::getSeq)
                .collect(Collectors.toList());
    }

    @Test
    void cursorlessSmallLimitReturnsTheNewestSlice() {
        // The reproduced defect: 44 in the buffer, limit=5 returned seqs 1..5.
        ChatMessageBuffer buffer = filled(44);
        ChatMessageBuffer.Page page = buffer.since(null, 0, 5, null, null);
        assertEquals(List.of(40L, 41L, 42L, 43L, 44L), seqs(page), "must be the newest 5, oldest-first");
        assertEquals(44L, page.getSeq(), "cursor lands on the head so the next poll continues");
    }

    @Test
    void cursorlessFullLimitStillReturnsEverything() {
        ChatMessageBuffer buffer = filled(44);
        ChatMessageBuffer.Page page = buffer.since(null, 0, 200, null, null);
        assertEquals(44, page.getMessages().size());
        assertEquals(1L, seqs(page).get(0));
        assertEquals(44L, page.getSeq());
    }

    @Test
    void cursoredReadKeepsForwardPagination() {
        // Control: a cursored reader pages FORWARD - newest-first here would create gaps.
        ChatMessageBuffer buffer = filled(44);
        String boot = buffer.getBootId();
        ChatMessageBuffer.Page page = buffer.since(boot, 10, 5, null, null);
        assertEquals(List.of(11L, 12L, 13L, 14L, 15L), seqs(page), "oldest 5 AFTER the cursor");
        assertFalse(page.isStale());
        assertEquals(15L, page.getSeq());
    }

    @Test
    void cursoredTailReturnsOnlyWhatIsNew() {
        ChatMessageBuffer buffer = filled(44);
        String boot = buffer.getBootId();
        ChatMessageBuffer.Page page = buffer.since(boot, 40, 200, null, null);
        assertEquals(List.of(41L, 42L, 43L, 44L), seqs(page));
    }

    @Test
    void staleResyncServesTheNewestSlice() {
        // A boot mismatch resyncs from the head, not from the fossil end of the buffer.
        ChatMessageBuffer buffer = filled(44);
        ChatMessageBuffer.Page page = buffer.since("not-this-boot", 30, 5, null, null);
        assertTrue(page.isStale());
        assertEquals(List.of(40L, 41L, 42L, 43L, 44L), seqs(page));
        assertEquals(44L, page.getSeq());
    }

    @Test
    void roomFilterAppliesBeforeTheNewestCut() {
        ChatMessageBuffer buffer = new ChatMessageBuffer(500);
        for (int i = 1; i <= 10; i++) {
            ChatMessageDTO d = dto("m" + i);
            d.setRoom(i % 2 == 0 ? "SERVER" : "GLOBAL");
            buffer.record(d);
        }
        ChatMessageBuffer.Page page = buffer.since(null, 0, 3, "SERVER", null);
        assertEquals(List.of(6L, 8L, 10L), seqs(page), "newest 3 MATCHING entries, not newest 3 overall");
    }
}
