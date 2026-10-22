package org.fourz.rvnkcore.api.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The refused-Matrix-line log never prints raw client bytes (#2384). */
@DisplayName("Chat inbound refusal log")
class ChatRelayControllerLogTest {

    @Test
    @DisplayName("logSafe: null as '-', controls and non-ASCII replaced, long values cut")
    void logSafe() {
        assertEquals("-", ChatRelayController.logSafe(null));
        assertEquals("ravengrub", ChatRelayController.logSafe("ravengrub"));
        assertEquals("a?b?c", ChatRelayController.logSafe("a\nb‮c"));
        String cut = ChatRelayController.logSafe("x".repeat(60));
        assertEquals(43, cut.length());
        assertTrue(cut.endsWith("..."));
    }
}
