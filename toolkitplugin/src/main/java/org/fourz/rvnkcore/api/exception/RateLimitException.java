package org.fourz.rvnkcore.api.exception;

/**
 * Exception thrown when a client exceeds the rate limit.
 *
 * This exception indicates that the client has made too many requests
 * in a given time period and should wait before retrying.
 */
public class RateLimitException extends ApiException {

    private static final int STATUS_TOO_MANY_REQUESTS = 429;
    private static final String ERROR_CODE = "RATE_LIMIT_EXCEEDED";

    private final long retryAfterSeconds;
    private final String clientId;

    /**
     * Creates a new RateLimitException.
     *
     * @param message The detail message
     */
    public RateLimitException(String message) {
        super(STATUS_TOO_MANY_REQUESTS, ERROR_CODE, message);
        this.retryAfterSeconds = 60;
        this.clientId = null;
    }

    /**
     * Creates a new RateLimitException with retry information.
     *
     * @param clientId The identifier of the rate-limited client
     * @param retryAfterSeconds Seconds until the client can retry
     */
    public RateLimitException(String clientId, long retryAfterSeconds) {
        super(STATUS_TOO_MANY_REQUESTS, ERROR_CODE,
              String.format("Rate limit exceeded. Retry after %d seconds.", retryAfterSeconds));
        this.clientId = clientId;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    /**
     * Gets the number of seconds the client should wait before retrying.
     *
     * @return Seconds until retry is allowed
     */
    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }

    /**
     * Gets the identifier of the rate-limited client.
     *
     * @return The client ID, or null if not specified
     */
    public String getClientId() {
        return clientId;
    }
}
