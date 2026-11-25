package org.fourz.rvnkcore.service.whitelist;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyString;

import com.google.gson.Gson;
import jakarta.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.io.StringWriter;
import org.fourz.rvnkcore.api.controller.WhitelistControllerAccess;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** #2448: a Mojang 429 is "retry later" (503 + Retry-After), not a failed lookup (502). */
@DisplayName("whitelist lookup: Mojang rate limit maps to 503 + Retry-After (#2448)")
class MojangRateLimitTest {

    private static Throwable failure(int status, String retryAfter) {
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> MojangProfileLookup.parse(status, "", retryAfter));
        return MojangProfileLookup.toLookupFailure("HORNYC4T", ex);
    }

    @Test
    @DisplayName("429 with Retry-After: 120 carries 120 s")
    void rateLimitedWithHeader() {
        Throwable f = failure(429, "120");
        WhitelistMembership.LookupRateLimitedException limited =
                assertInstanceOf(WhitelistMembership.LookupRateLimitedException.class, f);
        assertEquals(120, limited.retryAfterSeconds());
        assertEquals("Mojang profile lookup returned HTTP 429", limited.getCause().getMessage(),
                "the console line keeps the upstream status");
    }

    @Test
    @DisplayName("429 without a usable Retry-After falls back to 60 s")
    void rateLimitedDefault() {
        for (String header : new String[] {null, "", "Wed, 21 Oct 2026 07:28:00 GMT", "0", "-5"}) {
            Throwable f = failure(429, header);
            assertEquals(60, assertInstanceOf(WhitelistMembership.LookupRateLimitedException.class, f)
                    .retryAfterSeconds(), "header " + header);
        }
    }

    @Test
    @DisplayName("500 and a network error stay a plain lookup failure")
    void otherFailures() {
        Throwable f = failure(500, "30");
        assertInstanceOf(WhitelistMembership.LookupFailedException.class, f);
        assertFalse(f instanceof WhitelistMembership.LookupRateLimitedException);
        Throwable net = MojangProfileLookup.toLookupFailure("x", new java.io.IOException("connect timed out"));
        assertFalse(net instanceof WhitelistMembership.LookupRateLimitedException);
    }

    @Test
    @DisplayName("404 is still 'no such account', not a failure")
    void notFound() {
        assertTrue(MojangProfileLookup.parse(404, "", "5").isEmpty());
    }

    private static HttpServletResponse response(StringWriter body) throws Exception {
        HttpServletResponse resp = mock(HttpServletResponse.class);
        when(resp.getWriter()).thenReturn(new PrintWriter(body));
        return resp;
    }

    @Test
    @DisplayName("controller: 429 -> 503 LOOKUP_RATE_LIMITED with Retry-After")
    void controller429() throws Exception {
        StringWriter body = new StringWriter();
        HttpServletResponse resp = response(body);
        WhitelistControllerAccess.sendLookupError(resp, new Gson(), failure(429, "45"));
        verify(resp).setHeader("Retry-After", "45");
        verify(resp).setStatus(503);
        assertTrue(body.toString().contains("LOOKUP_RATE_LIMITED"), body.toString());
    }

    @Test
    @DisplayName("controller: 500 -> 502 LOOKUP_FAILED, no Retry-After")
    void controller500() throws Exception {
        StringWriter body = new StringWriter();
        HttpServletResponse resp = response(body);
        WhitelistControllerAccess.sendLookupError(resp, new Gson(), failure(500, null));
        verify(resp, never()).setHeader(org.mockito.ArgumentMatchers.eq("Retry-After"), anyString());
        verify(resp).setStatus(502);
        assertTrue(body.toString().contains("LOOKUP_FAILED"), body.toString());
    }
}
