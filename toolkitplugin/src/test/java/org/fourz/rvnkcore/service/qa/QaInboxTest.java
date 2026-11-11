package org.fourz.rvnkcore.service.qa;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("QA inbox: per-player lines with a shared cursor (#2359)")
class QaInboxTest {

    private final UUID alice = UUID.randomUUID();
    private final UUID bob = UUID.randomUUID();

    private static List<String> texts(QaInbox.Page page) {
        return page.lines().stream().map(QaInbox.Line::text).toList();
    }

    @Test
    @DisplayName("a reader gets only its player's lines, oldest first, and the cursor skips what it saw")
    void cursorAndIsolation() {
        QaInbox inbox = new QaInbox(50);
        inbox.record(alice, "chat", "a1", 1L);
        inbox.record(bob, "chat", "b1", 2L);
        inbox.record(alice, "title", "a2", 3L);

        QaInbox.Page first = inbox.since(alice, 0, 100);
        assertEquals(List.of("a1", "a2"), texts(first));
        assertEquals("title", first.lines().get(1).kind());
        assertFalse(first.more());
        assertEquals(3, first.cursor(), "cursor is the newest number handed out");

        assertTrue(inbox.since(alice, first.cursor(), 100).lines().isEmpty(), "nothing new after the cursor");
        inbox.record(alice, "chat", "a3", 4L);
        assertEquals(List.of("a3"), texts(inbox.since(alice, first.cursor(), 100)));
    }

    @Test
    @DisplayName("a full page sets more and stops the cursor at its last line")
    void paging() {
        QaInbox inbox = new QaInbox(50);
        for (int i = 1; i <= 5; i++) inbox.record(alice, "chat", "m" + i, i);
        QaInbox.Page p1 = inbox.since(alice, 0, 2);
        assertEquals(List.of("m1", "m2"), texts(p1));
        assertTrue(p1.more());
        QaInbox.Page p2 = inbox.since(alice, p1.cursor(), 2);
        assertEquals(List.of("m3", "m4"), texts(p2));
        QaInbox.Page p3 = inbox.since(alice, p2.cursor(), 2);
        assertEquals(List.of("m5"), texts(p3));
        assertFalse(p3.more());
    }

    @Test
    @DisplayName("each player keeps only the newest capacity lines; blank text is ignored")
    void capacityAndBlank() {
        QaInbox inbox = new QaInbox(QaInbox.MIN_CAPACITY);
        for (int i = 1; i <= QaInbox.MIN_CAPACITY + 3; i++) inbox.record(alice, "chat", "m" + i, i);
        inbox.record(alice, "chat", "   ", 99L);
        List<String> kept = texts(inbox.since(alice, 0, 100));
        assertEquals(QaInbox.MIN_CAPACITY, kept.size());
        assertEquals("m4", kept.get(0));
        assertEquals(new QaInbox(1).capacity(), QaInbox.MIN_CAPACITY, "capacity is clamped");
    }

    @Test
    @DisplayName("an unknown player reads an empty page; clear drops a player's lines")
    void unknownAndClear() {
        QaInbox inbox = new QaInbox(50);
        assertTrue(inbox.since(alice, 0, 10).lines().isEmpty());
        inbox.record(alice, "chat", "x", 1L);
        inbox.clear(alice);
        assertTrue(inbox.since(alice, 0, 10).lines().isEmpty());
    }
}
