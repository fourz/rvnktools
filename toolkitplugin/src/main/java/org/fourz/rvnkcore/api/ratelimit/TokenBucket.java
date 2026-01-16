package org.fourz.rvnkcore.api.ratelimit;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Token bucket implementation for rate limiting.
 *
 * Tokens are added at a fixed rate up to a maximum capacity.
 * Each request consumes one token. If no tokens are available,
 * the request is rate-limited.
 */
public class TokenBucket {

    private final long capacity;
    private final long refillRate; // tokens per second
    private final AtomicLong tokens;
    private final AtomicLong lastRefillTime;

    /**
     * Creates a new token bucket.
     *
     * @param capacity Maximum number of tokens
     * @param refillRate Tokens added per second
     */
    public TokenBucket(long capacity, long refillRate) {
        this.capacity = capacity;
        this.refillRate = refillRate;
        this.tokens = new AtomicLong(capacity);
        this.lastRefillTime = new AtomicLong(System.nanoTime());
    }

    /**
     * Creates a token bucket with default settings (100 tokens, 1.67/sec = 100/min).
     *
     * @return A new token bucket with default rate limits
     */
    public static TokenBucket withDefaults() {
        return new TokenBucket(100, 2); // 100 capacity, 2 tokens/sec = 120/min burst
    }

    /**
     * Creates a token bucket for the specified requests per minute.
     *
     * @param requestsPerMinute Maximum requests allowed per minute
     * @return A new token bucket configured for the specified rate
     */
    public static TokenBucket forRequestsPerMinute(int requestsPerMinute) {
        long tokensPerSecond = Math.max(1, requestsPerMinute / 60);
        return new TokenBucket(requestsPerMinute, tokensPerSecond);
    }

    /**
     * Attempts to consume a token.
     *
     * @return true if a token was consumed, false if rate limited
     */
    public boolean tryConsume() {
        return tryConsume(1);
    }

    /**
     * Attempts to consume the specified number of tokens.
     *
     * @param tokensToConsume Number of tokens to consume
     * @return true if tokens were consumed, false if rate limited
     */
    public boolean tryConsume(long tokensToConsume) {
        refill();

        while (true) {
            long currentTokens = tokens.get();
            if (currentTokens < tokensToConsume) {
                return false;
            }
            if (tokens.compareAndSet(currentTokens, currentTokens - tokensToConsume)) {
                return true;
            }
            // CAS failed, retry
        }
    }

    /**
     * Gets the number of available tokens.
     *
     * @return Current token count
     */
    public long getAvailableTokens() {
        refill();
        return tokens.get();
    }

    /**
     * Gets the estimated seconds until a token is available.
     *
     * @return Seconds until next token, or 0 if tokens are available
     */
    public long getSecondsUntilRefill() {
        refill();
        long current = tokens.get();
        if (current > 0) {
            return 0;
        }
        return Math.max(1, 1 / refillRate);
    }

    /**
     * Gets the bucket capacity.
     *
     * @return Maximum number of tokens
     */
    public long getCapacity() {
        return capacity;
    }

    /**
     * Gets the refill rate.
     *
     * @return Tokens added per second
     */
    public long getRefillRate() {
        return refillRate;
    }

    /**
     * Refills tokens based on elapsed time since last refill.
     */
    private void refill() {
        long now = System.nanoTime();
        long lastRefill = lastRefillTime.get();
        long elapsedNanos = now - lastRefill;

        if (elapsedNanos <= 0) {
            return;
        }

        // Calculate tokens to add based on elapsed time
        long tokensToAdd = (elapsedNanos * refillRate) / 1_000_000_000L;

        if (tokensToAdd > 0) {
            if (lastRefillTime.compareAndSet(lastRefill, now)) {
                long currentTokens = tokens.get();
                long newTokens = Math.min(capacity, currentTokens + tokensToAdd);
                tokens.set(newTokens);
            }
        }
    }

    /**
     * Resets the bucket to full capacity.
     */
    public void reset() {
        tokens.set(capacity);
        lastRefillTime.set(System.nanoTime());
    }
}
