package org.fourz.rvnkcore.api.ratelimit;

import org.fourz.rvnkcore.api.exception.RateLimitException;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Rate limiter service for protecting API endpoints.
 *
 * Manages token buckets per client identifier (IP address, API key, etc.)
 * and provides methods to check and enforce rate limits.
 */
public class RateLimiter {

    private final ConcurrentHashMap<String, TokenBucket> clientBuckets;
    private final long defaultCapacity;
    private final long defaultRefillRate;
    private final ScheduledExecutorService cleanupExecutor;
    private final long bucketExpirationMs;

    // Track last access time for cleanup
    private final ConcurrentHashMap<String, Long> lastAccessTime;

    /**
     * Creates a rate limiter with default settings (100 requests/minute).
     */
    public RateLimiter() {
        this(100, 2); // 100 capacity, 2 tokens/sec
    }

    /**
     * Creates a rate limiter with custom settings.
     *
     * @param defaultCapacity Maximum tokens per client
     * @param defaultRefillRate Tokens added per second per client
     */
    public RateLimiter(long defaultCapacity, long defaultRefillRate) {
        this.clientBuckets = new ConcurrentHashMap<>();
        this.lastAccessTime = new ConcurrentHashMap<>();
        this.defaultCapacity = defaultCapacity;
        this.defaultRefillRate = defaultRefillRate;
        this.bucketExpirationMs = TimeUnit.MINUTES.toMillis(10); // Clean up inactive buckets

        // Start cleanup task
        this.cleanupExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "RateLimiter-Cleanup");
            t.setDaemon(true);
            return t;
        });
        cleanupExecutor.scheduleAtFixedRate(this::cleanupExpiredBuckets, 5, 5, TimeUnit.MINUTES);
    }

    /**
     * Creates a rate limiter for the specified requests per minute.
     *
     * @param requestsPerMinute Maximum requests per minute per client
     * @return A new rate limiter
     */
    public static RateLimiter forRequestsPerMinute(int requestsPerMinute) {
        long tokensPerSecond = Math.max(1, requestsPerMinute / 60);
        return new RateLimiter(requestsPerMinute, tokensPerSecond);
    }

    /**
     * Checks if a client is allowed to make a request.
     *
     * @param clientId The client identifier
     * @return true if allowed, false if rate limited
     */
    public boolean isAllowed(String clientId) {
        TokenBucket bucket = getOrCreateBucket(clientId);
        lastAccessTime.put(clientId, System.currentTimeMillis());
        return bucket.tryConsume();
    }

    /**
     * Checks rate limit and throws RateLimitException if exceeded.
     *
     * @param clientId The client identifier
     * @throws RateLimitException if rate limit is exceeded
     */
    public void checkLimit(String clientId) throws RateLimitException {
        if (!isAllowed(clientId)) {
            TokenBucket bucket = getOrCreateBucket(clientId);
            long retryAfter = bucket.getSecondsUntilRefill();
            throw new RateLimitException(clientId, Math.max(1, retryAfter));
        }
    }

    /**
     * Gets the remaining allowed requests for a client.
     *
     * @param clientId The client identifier
     * @return Number of remaining requests
     */
    public long getRemainingRequests(String clientId) {
        TokenBucket bucket = clientBuckets.get(clientId);
        return bucket != null ? bucket.getAvailableTokens() : defaultCapacity;
    }

    /**
     * Gets the rate limit capacity for a client.
     *
     * @param clientId The client identifier
     * @return The client's rate limit capacity
     */
    public long getLimitCapacity(String clientId) {
        TokenBucket bucket = clientBuckets.get(clientId);
        return bucket != null ? bucket.getCapacity() : defaultCapacity;
    }

    /**
     * Resets the rate limit for a specific client.
     *
     * @param clientId The client identifier
     */
    public void resetClient(String clientId) {
        TokenBucket bucket = clientBuckets.get(clientId);
        if (bucket != null) {
            bucket.reset();
        }
    }

    /**
     * Removes a client's rate limit tracking.
     *
     * @param clientId The client identifier
     */
    public void removeClient(String clientId) {
        clientBuckets.remove(clientId);
        lastAccessTime.remove(clientId);
    }

    /**
     * Sets a custom rate limit for a specific client.
     *
     * @param clientId The client identifier
     * @param capacity Maximum tokens
     * @param refillRate Tokens per second
     */
    public void setClientLimit(String clientId, long capacity, long refillRate) {
        clientBuckets.put(clientId, new TokenBucket(capacity, refillRate));
        lastAccessTime.put(clientId, System.currentTimeMillis());
    }

    /**
     * Gets the number of tracked clients.
     *
     * @return Number of clients with active rate limit buckets
     */
    public int getTrackedClientCount() {
        return clientBuckets.size();
    }

    /**
     * Shuts down the rate limiter and releases resources.
     */
    public void shutdown() {
        cleanupExecutor.shutdown();
        try {
            if (!cleanupExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                cleanupExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            cleanupExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        clientBuckets.clear();
        lastAccessTime.clear();
    }

    private TokenBucket getOrCreateBucket(String clientId) {
        return clientBuckets.computeIfAbsent(clientId,
            k -> new TokenBucket(defaultCapacity, defaultRefillRate));
    }

    private void cleanupExpiredBuckets() {
        long now = System.currentTimeMillis();
        lastAccessTime.entrySet().removeIf(entry -> {
            if (now - entry.getValue() > bucketExpirationMs) {
                clientBuckets.remove(entry.getKey());
                return true;
            }
            return false;
        });
    }
}
