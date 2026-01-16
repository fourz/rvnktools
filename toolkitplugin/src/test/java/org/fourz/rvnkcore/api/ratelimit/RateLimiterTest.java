package org.fourz.rvnkcore.api.ratelimit;

import org.fourz.rvnkcore.api.exception.RateLimitException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the RateLimiter class.
 */
class RateLimiterTest {

    private RateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        // Create a rate limiter with low limits for testing
        rateLimiter = new RateLimiter(5, 1); // 5 capacity, 1 token/sec
    }

    @AfterEach
    void tearDown() {
        rateLimiter.shutdown();
    }

    @Test
    @DisplayName("Initial requests are allowed")
    void initialRequestsAllowed() {
        assertTrue(rateLimiter.isAllowed("client1"));
        assertTrue(rateLimiter.isAllowed("client1"));
        assertTrue(rateLimiter.isAllowed("client1"));
    }

    @Test
    @DisplayName("Rate limit is enforced after capacity exhausted")
    void rateLimitEnforcedAfterCapacity() {
        String clientId = "client2";

        // Exhaust the capacity
        for (int i = 0; i < 5; i++) {
            assertTrue(rateLimiter.isAllowed(clientId), "Request " + i + " should be allowed");
        }

        // Next request should be denied
        assertFalse(rateLimiter.isAllowed(clientId), "Request after capacity should be denied");
    }

    @Test
    @DisplayName("checkLimit throws RateLimitException when exceeded")
    void checkLimitThrowsWhenExceeded() {
        String clientId = "client3";

        // Exhaust the capacity
        for (int i = 0; i < 5; i++) {
            assertDoesNotThrow(() -> rateLimiter.checkLimit(clientId));
        }

        // Next should throw
        RateLimitException ex = assertThrows(RateLimitException.class,
            () -> rateLimiter.checkLimit(clientId));

        assertEquals(clientId, ex.getClientId());
        assertTrue(ex.getRetryAfterSeconds() >= 1);
    }

    @Test
    @DisplayName("Different clients have separate limits")
    void differentClientsSeparateLimits() {
        // Exhaust client1's limit
        for (int i = 0; i < 5; i++) {
            rateLimiter.isAllowed("client1");
        }
        assertFalse(rateLimiter.isAllowed("client1"));

        // client2 should still be allowed
        assertTrue(rateLimiter.isAllowed("client2"));
    }

    @Test
    @DisplayName("getRemainingRequests returns correct count")
    void getRemainingRequestsReturnsCorrectCount() {
        String clientId = "client4";

        // Initially should have full capacity
        assertEquals(5, rateLimiter.getRemainingRequests(clientId));

        // After one request
        rateLimiter.isAllowed(clientId);
        assertEquals(4, rateLimiter.getRemainingRequests(clientId));
    }

    @Test
    @DisplayName("resetClient restores capacity")
    void resetClientRestoresCapacity() {
        String clientId = "client5";

        // Exhaust capacity
        for (int i = 0; i < 5; i++) {
            rateLimiter.isAllowed(clientId);
        }
        assertFalse(rateLimiter.isAllowed(clientId));

        // Reset
        rateLimiter.resetClient(clientId);

        // Should be allowed again
        assertTrue(rateLimiter.isAllowed(clientId));
    }

    @Test
    @DisplayName("removeClient clears tracking")
    void removeClientClearsTracking() {
        String clientId = "client6";

        rateLimiter.isAllowed(clientId);
        assertEquals(1, rateLimiter.getTrackedClientCount());

        rateLimiter.removeClient(clientId);
        // New client starts fresh
        assertEquals(5, rateLimiter.getLimitCapacity(clientId));
    }

    @Test
    @DisplayName("setClientLimit allows custom limits")
    void setClientLimitAllowsCustomLimits() {
        String clientId = "premium";

        rateLimiter.setClientLimit(clientId, 100, 10);
        assertEquals(100, rateLimiter.getLimitCapacity(clientId));
    }
}
