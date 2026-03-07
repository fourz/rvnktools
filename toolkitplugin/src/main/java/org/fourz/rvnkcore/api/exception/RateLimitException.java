package org.fourz.rvnkcore.api.exception;

/**
 * Exception thrown when a client exceeds the rate limit.
 */
public class RateLimitException extends RuntimeException {

    private final long retryAfterSeconds;
    private final String clientId;

    public RateLimitException(String message) {
        super(message);
        this.retryAfterSeconds = 60;
        this.clientId = null;
    }

    public RateLimitException(String clientId, long retryAfterSeconds) {
        super(String.format("Rate limit exceeded. Retry after %d seconds.", retryAfterSeconds));
        this.clientId = clientId;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }

    public String getClientId() {
        return clientId;
    }
}
