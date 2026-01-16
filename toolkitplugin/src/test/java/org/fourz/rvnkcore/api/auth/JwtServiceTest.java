package org.fourz.rvnkcore.api.auth;

import org.fourz.rvnkcore.api.exception.AuthorizationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the JwtService class.
 */
class JwtServiceTest {

    private JwtService jwtService;
    private AuthConfig config;

    @BeforeEach
    void setUp() {
        config = AuthConfig.builder()
            .secretKey("test-secret-key-at-least-256-bits-long-for-hmac256")
            .issuer("test-issuer")
            .tokenExpirationMinutes(60)
            .refreshTokenExpirationDays(7)
            .build();

        jwtService = new JwtService(config);
    }

    @Test
    @DisplayName("generateToken creates valid token")
    void generateTokenCreatesValidToken() throws AuthorizationException {
        String pluginId = "test-plugin";
        List<String> permissions = List.of("api:read", "api:write");

        String token = jwtService.generateToken(pluginId, permissions);

        assertNotNull(token);
        assertFalse(token.isEmpty());

        // Validate the token
        String extractedPluginId = jwtService.getPluginId(token);
        assertEquals(pluginId, extractedPluginId);
    }

    @Test
    @DisplayName("getPermissions returns correct permissions")
    void getPermissionsReturnsCorrectPermissions() throws AuthorizationException {
        List<String> permissions = List.of("admin", "player:read");
        String token = jwtService.generateToken("plugin", permissions);

        List<String> extracted = jwtService.getPermissions(token);

        assertEquals(2, extracted.size());
        assertTrue(extracted.contains("admin"));
        assertTrue(extracted.contains("player:read"));
    }

    @Test
    @DisplayName("hasPermission returns true for granted permission")
    void hasPermissionReturnsTrueForGranted() throws AuthorizationException {
        String token = jwtService.generateToken("plugin", List.of("api:read"));

        assertTrue(jwtService.hasPermission(token, "api:read"));
        assertFalse(jwtService.hasPermission(token, "api:write"));
    }

    @Test
    @DisplayName("validateToken throws for invalid token")
    void validateTokenThrowsForInvalid() {
        assertThrows(AuthorizationException.class, () ->
            jwtService.validateToken("not-a-valid-token"));
    }

    @Test
    @DisplayName("validateToken throws for tampered token")
    void validateTokenThrowsForTampered() {
        String token = jwtService.generateToken("plugin");
        String tampered = token + "tampered";

        assertThrows(AuthorizationException.class, () ->
            jwtService.validateToken(tampered));
    }

    @Test
    @DisplayName("generateRefreshToken creates refresh token type")
    void generateRefreshTokenCreatesRefreshType() throws AuthorizationException {
        String refreshToken = jwtService.generateRefreshToken("plugin");

        assertTrue(jwtService.isRefreshToken(refreshToken));
        assertEquals("refresh", jwtService.getTokenType(refreshToken));
    }

    @Test
    @DisplayName("access token is not a refresh token")
    void accessTokenIsNotRefreshToken() throws AuthorizationException {
        String accessToken = jwtService.generateToken("plugin");

        assertFalse(jwtService.isRefreshToken(accessToken));
        assertEquals("access", jwtService.getTokenType(accessToken));
    }

    @Test
    @DisplayName("getExpiration returns future time for new token")
    void getExpirationReturnsFutureTime() throws AuthorizationException {
        String token = jwtService.generateToken("plugin");

        Instant expiration = jwtService.getExpiration(token);

        assertTrue(expiration.isAfter(Instant.now()));
    }

    @Test
    @DisplayName("isExpired returns false for new token")
    void isExpiredReturnsFalseForNewToken() {
        String token = jwtService.generateToken("plugin");

        assertFalse(jwtService.isExpired(token));
    }

    @Test
    @DisplayName("refreshAccessToken creates new access token")
    void refreshAccessTokenCreatesNewToken() throws AuthorizationException {
        String refreshToken = jwtService.generateRefreshToken("plugin");
        List<String> permissions = List.of("api:read");

        String newAccessToken = jwtService.refreshAccessToken(refreshToken, permissions);

        assertNotNull(newAccessToken);
        assertEquals("plugin", jwtService.getPluginId(newAccessToken));
        assertEquals("access", jwtService.getTokenType(newAccessToken));
    }

    @Test
    @DisplayName("refreshAccessToken rejects access token")
    void refreshAccessTokenRejectsAccessToken() {
        String accessToken = jwtService.generateToken("plugin");

        assertThrows(AuthorizationException.class, () ->
            jwtService.refreshAccessToken(accessToken, List.of("api:read")));
    }

    @Test
    @DisplayName("withDefaults creates working service")
    void withDefaultsCreatesWorkingService() throws AuthorizationException {
        JwtService defaultService = JwtService.withDefaults();

        String token = defaultService.generateToken("plugin");
        String pluginId = defaultService.getPluginId(token);

        assertEquals("plugin", pluginId);
    }
}
