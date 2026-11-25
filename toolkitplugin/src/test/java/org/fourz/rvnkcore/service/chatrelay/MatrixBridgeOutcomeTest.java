package org.fourz.rvnkcore.service.chatrelay;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** #2443: a bot post answers what the Matrix bridge did, not "accepted" before it answers. */
@DisplayName("Matrix bridge outcome (#2443)")
class MatrixBridgeOutcomeTest {

    @Test
    @DisplayName("Each bridge status maps to one outcome and HTTP status")
    void mapping() {
        assertEquals(MatrixBridgeOutcome.DELIVERED, MatrixBridgeOutcome.of(200));
        assertEquals(MatrixBridgeOutcome.DELIVERED, MatrixBridgeOutcome.of(204));
        assertEquals(MatrixBridgeOutcome.RATE_LIMITED, MatrixBridgeOutcome.of(429));
        assertEquals(MatrixBridgeOutcome.REFUSED, MatrixBridgeOutcome.of(403));
        assertEquals(MatrixBridgeOutcome.REFUSED, MatrixBridgeOutcome.of(500));
        assertEquals(MatrixBridgeOutcome.UNREACHABLE, MatrixBridgeOutcome.of(MatrixBridgeOutcome.FAILED));
        assertEquals(MatrixBridgeOutcome.NO_BRIDGE, MatrixBridgeOutcome.of(MatrixBridgeOutcome.NOT_SENT));
        assertEquals(MatrixBridgeOutcome.UNCONFIRMED, MatrixBridgeOutcome.of(null));

        assertEquals(429, MatrixBridgeOutcome.RATE_LIMITED.httpStatus());
        assertEquals("RATE_LIMITED", MatrixBridgeOutcome.RATE_LIMITED.errorCode());
        assertEquals(202, MatrixBridgeOutcome.UNCONFIRMED.httpStatus());
        assertTrue(MatrixBridgeOutcome.UNCONFIRMED.success(), "not confirmed is still a success envelope");
        assertFalse(MatrixBridgeOutcome.REFUSED.success());
        assertEquals("BAD_GATEWAY", MatrixBridgeOutcome.UNREACHABLE.errorCode());
    }

    @Test
    @DisplayName("Messages say what Discord/Matrix got, and that game players saw the line")
    void messages() {
        assertTrue(MatrixBridgeOutcome.RATE_LIMITED.message(429, 3500).contains("rate cap"));
        assertTrue(MatrixBridgeOutcome.REFUSED.message(403, 3500).contains("403"));
        assertTrue(MatrixBridgeOutcome.UNCONFIRMED.message(null, 3500).contains("3500 ms"));
        assertNotEquals("Matrix post accepted", MatrixBridgeOutcome.UNCONFIRMED.message(null, 3500));
    }

    @Test
    @DisplayName("The wait is the relay timeout plus 500 ms, capped at 5 s")
    void waitMs() {
        assertEquals(3_500L, MatrixBridgeOutcome.waitMs(3_000));
        assertEquals(5_000L, MatrixBridgeOutcome.waitMs(30_000));
        assertEquals(500L, MatrixBridgeOutcome.waitMs(-1));
    }
}
