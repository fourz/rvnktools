package org.fourz.rvnkcore.api.auth;

import org.bukkit.Server;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.fourz.rvnkcore.api.auth.AuthTokenStore.ConsumeOutcome;
import org.fourz.rvnkcore.api.auth.AuthTokenStore.ConsumeResult;
import org.fourz.rvnkcore.api.auth.AuthTokenStore.TokenKind;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link AuthTokenStore}, focused on the TokenKind + per-call TTL
 * contract added for {@code /link invite} (issue tracked separately).
 */
class AuthTokenStoreTest {

    private Plugin plugin;
    private AuthTokenStore store;

    private static final UUID TEST_UUID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final String TEST_NAME = "TestUser";
    private static final List<String> TEST_GROUPS = List.of("default", "member");

    @BeforeEach
    void setUp() {
        plugin = mock(Plugin.class);
        Server server = mock(Server.class);
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        when(plugin.getServer()).thenReturn(server);
        when(server.getScheduler()).thenReturn(scheduler);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("AuthTokenStoreTest"));
        when(plugin.getName()).thenReturn("RVNKCore");
        store = new AuthTokenStore(plugin);
    }

    @Test
    @DisplayName("Default LOGIN token consumes with kind=LOGIN and correct identity")
    void loginTokenRoundTrip() {
        String token = store.generateToken(TEST_UUID, TEST_NAME, TEST_GROUPS);
        ConsumeOutcome outcome = store.consumeToken(token);

        assertEquals(ConsumeResult.SUCCESS, outcome.result());
        assertNotNull(outcome.token());
        assertEquals(TEST_UUID, outcome.token().playerUuid());
        assertEquals(TEST_NAME, outcome.token().playerName());
        assertEquals(TEST_GROUPS, outcome.token().groups());
        assertEquals(TokenKind.LOGIN, outcome.token().kind());
    }

    @Test
    @DisplayName("INVITE token consumes with kind=INVITE and honours per-call TTL")
    void inviteTokenRoundTrip() {
        Instant before = Instant.now();
        long ttl = 2 * 60 * 60L; // 2h in seconds
        String token = store.generateToken(TEST_UUID, TEST_NAME, TEST_GROUPS, TokenKind.INVITE, ttl);

        ConsumeOutcome outcome = store.consumeToken(token);
        assertEquals(ConsumeResult.SUCCESS, outcome.result());
        assertEquals(TokenKind.INVITE, outcome.token().kind());

        // expiresAt should be ~2h after generation, not the 15m LOGIN default.
        long secondsUntilExpiry = outcome.token().expiresAt().getEpochSecond() - before.getEpochSecond();
        assertTrue(secondsUntilExpiry >= ttl - 2,
                "expiresAt should be ~" + ttl + "s out, got " + secondsUntilExpiry);
        assertTrue(secondsUntilExpiry <= ttl + 2,
                "expiresAt should not exceed " + ttl + "s, got " + secondsUntilExpiry);
    }

    @Test
    @DisplayName("INVITE token rejects reuse (one-time)")
    void inviteTokenOneTime() {
        String token = store.generateToken(TEST_UUID, TEST_NAME, TEST_GROUPS, TokenKind.INVITE, 3600);
        assertEquals(ConsumeResult.SUCCESS, store.consumeToken(token).result());
        assertEquals(ConsumeResult.ALREADY_USED, store.consumeToken(token).result());
    }

    @Test
    @DisplayName("INVITE token with TTL above MAX is clamped, not rejected")
    void inviteTtlClamped() {
        // 365 days way above MAX_TTL_SECONDS (7 days)
        long huge = 365L * 24L * 60L * 60L;
        String token = store.generateToken(TEST_UUID, TEST_NAME, TEST_GROUPS, TokenKind.INVITE, huge);
        ConsumeOutcome outcome = store.consumeToken(token);
        assertEquals(ConsumeResult.SUCCESS, outcome.result());

        long sevenDays = 7L * 24L * 60L * 60L;
        long secondsUntilExpiry = outcome.token().expiresAt().getEpochSecond()
                - outcome.token().createdAt().getEpochSecond();
        assertEquals(sevenDays, secondsUntilExpiry, "TTL must be clamped to 7 days");
    }

    @Test
    @DisplayName("INVITE generation does not trigger rate limit on target")
    void inviteDoesNotRateLimitTarget() {
        // Invite first — admin issues for TEST_UUID
        store.generateToken(TEST_UUID, TEST_NAME, TEST_GROUPS, TokenKind.INVITE, 3600);
        // Target immediately does /link login themselves — must not be rate-limited.
        assertFalse(store.isRateLimited(TEST_UUID),
                "INVITE generation must not apply the LOGIN rate-limit cooldown to the target.");
    }

    @Test
    @DisplayName("LOGIN generation still triggers rate limit for that UUID")
    void loginAppliesRateLimit() {
        store.generateToken(TEST_UUID, TEST_NAME, TEST_GROUPS);
        assertTrue(store.isRateLimited(TEST_UUID),
                "LOGIN generation must apply rate limit for replay-protection of self-serve flow.");
    }
}
