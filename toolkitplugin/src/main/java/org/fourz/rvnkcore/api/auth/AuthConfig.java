package org.fourz.rvnkcore.api.auth;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * Configuration for JWT authentication.
 *
 * Provides settings for token generation, validation, and expiration.
 */
public class AuthConfig {

    private String secretKey;
    private String issuer;
    private long tokenExpirationMinutes;
    private long refreshTokenExpirationDays;

    /**
     * Creates a default auth configuration.
     *
     * IMPORTANT: In production, the secret key should be loaded from
     * environment variables or a secure configuration file.
     */
    public AuthConfig() {
        this.secretKey = generateRandomSecret();
        this.issuer = "RVNKCore";
        this.tokenExpirationMinutes = 60; // 1 hour
        this.refreshTokenExpirationDays = 7; // 1 week
    }

    /**
     * Creates an auth configuration with a specific secret.
     *
     * @param secretKey The secret key for signing tokens
     */
    public AuthConfig(String secretKey) {
        this();
        this.secretKey = secretKey;
    }

    /**
     * Gets the secret key for signing tokens.
     *
     * @return The secret key
     */
    public String getSecretKey() {
        return secretKey;
    }

    /**
     * Sets the secret key for signing tokens.
     *
     * @param secretKey The secret key (should be at least 256 bits)
     */
    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    /**
     * Gets the token issuer claim.
     *
     * @return The issuer identifier
     */
    public String getIssuer() {
        return issuer;
    }

    /**
     * Sets the token issuer claim.
     *
     * @param issuer The issuer identifier
     */
    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    /**
     * Gets the access token expiration time in minutes.
     *
     * @return Expiration time in minutes
     */
    public long getTokenExpirationMinutes() {
        return tokenExpirationMinutes;
    }

    /**
     * Sets the access token expiration time.
     *
     * @param tokenExpirationMinutes Expiration time in minutes
     */
    public void setTokenExpirationMinutes(long tokenExpirationMinutes) {
        this.tokenExpirationMinutes = tokenExpirationMinutes;
    }

    /**
     * Gets the refresh token expiration time in days.
     *
     * @return Expiration time in days
     */
    public long getRefreshTokenExpirationDays() {
        return refreshTokenExpirationDays;
    }

    /**
     * Sets the refresh token expiration time.
     *
     * @param refreshTokenExpirationDays Expiration time in days
     */
    public void setRefreshTokenExpirationDays(long refreshTokenExpirationDays) {
        this.refreshTokenExpirationDays = refreshTokenExpirationDays;
    }

    /**
     * Generates a cryptographically secure random secret key.
     *
     * @return A Base64-encoded 256-bit random secret
     */
    public static String generateRandomSecret() {
        SecureRandom random = new SecureRandom();
        byte[] secret = new byte[32]; // 256 bits
        random.nextBytes(secret);
        return Base64.getEncoder().encodeToString(secret);
    }

    /**
     * Creates a builder for AuthConfig.
     *
     * @return A new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for AuthConfig.
     */
    public static class Builder {
        private String secretKey;
        private String issuer = "RVNKCore";
        private long tokenExpirationMinutes = 60;
        private long refreshTokenExpirationDays = 7;

        public Builder secretKey(String secretKey) {
            this.secretKey = secretKey;
            return this;
        }

        public Builder issuer(String issuer) {
            this.issuer = issuer;
            return this;
        }

        public Builder tokenExpirationMinutes(long minutes) {
            this.tokenExpirationMinutes = minutes;
            return this;
        }

        public Builder refreshTokenExpirationDays(long days) {
            this.refreshTokenExpirationDays = days;
            return this;
        }

        public AuthConfig build() {
            AuthConfig config = new AuthConfig();
            if (secretKey != null) {
                config.setSecretKey(secretKey);
            }
            config.setIssuer(issuer);
            config.setTokenExpirationMinutes(tokenExpirationMinutes);
            config.setRefreshTokenExpirationDays(refreshTokenExpirationDays);
            return config;
        }
    }
}
