package org.fourz.rvnkcore.api.auth;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import org.fourz.rvnkcore.api.exception.AuthorizationException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Service for JWT token generation and validation.
 *
 * Handles creation, verification, and parsing of JWT tokens for
 * cross-plugin API authentication.
 */
public class JwtService {

    private final AuthConfig config;
    private final Algorithm algorithm;
    private final JWTVerifier verifier;

    /**
     * Creates a new JwtService with the given configuration.
     *
     * @param config The authentication configuration
     */
    public JwtService(AuthConfig config) {
        this.config = config;
        this.algorithm = Algorithm.HMAC256(config.getSecretKey());
        this.verifier = JWT.require(algorithm)
            .withIssuer(config.getIssuer())
            .build();
    }

    /**
     * Creates a JwtService with default configuration.
     *
     * @return A new JwtService
     */
    public static JwtService withDefaults() {
        return new JwtService(new AuthConfig());
    }

    /**
     * Generates an access token for a plugin.
     *
     * @param pluginId The plugin identifier
     * @param permissions List of permissions granted to the plugin
     * @return The JWT access token
     */
    public String generateToken(String pluginId, List<String> permissions) {
        Instant now = Instant.now();
        Instant expiry = now.plus(config.getTokenExpirationMinutes(), ChronoUnit.MINUTES);

        return JWT.create()
            .withIssuer(config.getIssuer())
            .withSubject(pluginId)
            .withIssuedAt(Date.from(now))
            .withExpiresAt(Date.from(expiry))
            .withClaim("permissions", permissions)
            .withClaim("type", "access")
            .sign(algorithm);
    }

    /**
     * Generates an access token for a plugin with default permissions.
     *
     * @param pluginId The plugin identifier
     * @return The JWT access token
     */
    public String generateToken(String pluginId) {
        return generateToken(pluginId, List.of("api:read", "api:write"));
    }

    /**
     * Generates a refresh token for obtaining new access tokens.
     *
     * @param pluginId The plugin identifier
     * @return The JWT refresh token
     */
    public String generateRefreshToken(String pluginId) {
        Instant now = Instant.now();
        Instant expiry = now.plus(config.getRefreshTokenExpirationDays(), ChronoUnit.DAYS);

        return JWT.create()
            .withIssuer(config.getIssuer())
            .withSubject(pluginId)
            .withIssuedAt(Date.from(now))
            .withExpiresAt(Date.from(expiry))
            .withJWTId(UUID.randomUUID().toString())
            .withClaim("type", "refresh")
            .sign(algorithm);
    }

    /**
     * Validates a token and returns the decoded JWT.
     *
     * @param token The JWT token to validate
     * @return The decoded JWT
     * @throws AuthorizationException if token is invalid or expired
     */
    public DecodedJWT validateToken(String token) throws AuthorizationException {
        try {
            return verifier.verify(token);
        } catch (JWTVerificationException e) {
            throw AuthorizationException.invalidToken();
        }
    }

    /**
     * Extracts the plugin ID from a token.
     *
     * @param token The JWT token
     * @return The plugin ID (subject claim)
     * @throws AuthorizationException if token is invalid
     */
    public String getPluginId(String token) throws AuthorizationException {
        DecodedJWT jwt = validateToken(token);
        return jwt.getSubject();
    }

    /**
     * Extracts permissions from a token.
     *
     * @param token The JWT token
     * @return List of permissions
     * @throws AuthorizationException if token is invalid
     */
    public List<String> getPermissions(String token) throws AuthorizationException {
        DecodedJWT jwt = validateToken(token);
        return jwt.getClaim("permissions").asList(String.class);
    }

    /**
     * Checks if a token has a specific permission.
     *
     * @param token The JWT token
     * @param permission The permission to check
     * @return true if the token has the permission
     * @throws AuthorizationException if token is invalid
     */
    public boolean hasPermission(String token, String permission) throws AuthorizationException {
        List<String> permissions = getPermissions(token);
        return permissions != null && permissions.contains(permission);
    }

    /**
     * Checks if a token is a refresh token.
     *
     * @param token The JWT token
     * @return true if it's a refresh token
     * @throws AuthorizationException if token is invalid
     */
    public boolean isRefreshToken(String token) throws AuthorizationException {
        DecodedJWT jwt = validateToken(token);
        String type = jwt.getClaim("type").asString();
        return "refresh".equals(type);
    }

    /**
     * Gets the token type (access or refresh).
     *
     * @param token The JWT token
     * @return The token type
     * @throws AuthorizationException if token is invalid
     */
    public String getTokenType(String token) throws AuthorizationException {
        DecodedJWT jwt = validateToken(token);
        return jwt.getClaim("type").asString();
    }

    /**
     * Gets the token expiration time.
     *
     * @param token The JWT token
     * @return The expiration instant
     * @throws AuthorizationException if token is invalid
     */
    public Instant getExpiration(String token) throws AuthorizationException {
        DecodedJWT jwt = validateToken(token);
        return jwt.getExpiresAt().toInstant();
    }

    /**
     * Checks if a token is expired.
     *
     * @param token The JWT token
     * @return true if expired
     */
    public boolean isExpired(String token) {
        try {
            validateToken(token);
            return false;
        } catch (AuthorizationException e) {
            return true;
        }
    }

    /**
     * Refreshes an access token using a refresh token.
     *
     * @param refreshToken The refresh token
     * @param permissions The permissions for the new access token
     * @return A new access token
     * @throws AuthorizationException if refresh token is invalid
     */
    public String refreshAccessToken(String refreshToken, List<String> permissions)
            throws AuthorizationException {
        DecodedJWT jwt = validateToken(refreshToken);

        if (!"refresh".equals(jwt.getClaim("type").asString())) {
            throw new AuthorizationException("Not a refresh token");
        }

        return generateToken(jwt.getSubject(), permissions);
    }
}
