package org.fourz.rvnkcore.cache;

import java.time.Instant;

/**
 * Represents a cached value with expiration tracking.
 *
 * @param <V> The type of the cached value
 */
public class CacheEntry<V> {

    private final V value;
    private final Instant createdAt;
    private final Instant expiresAt;
    private volatile Instant lastAccessedAt;
    private volatile int hitCount;

    /**
     * Creates a cache entry with a specific TTL.
     *
     * @param value The value to cache
     * @param ttlMillis Time-to-live in milliseconds
     */
    public CacheEntry(V value, long ttlMillis) {
        this.value = value;
        this.createdAt = Instant.now();
        this.expiresAt = createdAt.plusMillis(ttlMillis);
        this.lastAccessedAt = createdAt;
        this.hitCount = 0;
    }

    /**
     * Creates a cache entry that never expires.
     *
     * @param value The value to cache
     */
    public CacheEntry(V value) {
        this.value = value;
        this.createdAt = Instant.now();
        this.expiresAt = Instant.MAX;
        this.lastAccessedAt = createdAt;
        this.hitCount = 0;
    }

    /**
     * Gets the cached value and updates access statistics.
     *
     * @return The cached value
     */
    public V getValue() {
        lastAccessedAt = Instant.now();
        hitCount++;
        return value;
    }

    /**
     * Gets the cached value without updating access statistics.
     *
     * @return The cached value
     */
    public V peekValue() {
        return value;
    }

    /**
     * Checks if this entry has expired.
     *
     * @return true if expired
     */
    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    /**
     * Gets the time remaining until expiration.
     *
     * @return Milliseconds until expiration, or 0 if expired
     */
    public long getTimeToLiveMillis() {
        long remaining = expiresAt.toEpochMilli() - Instant.now().toEpochMilli();
        return Math.max(0, remaining);
    }

    /**
     * Gets the creation timestamp.
     *
     * @return When this entry was created
     */
    public Instant getCreatedAt() {
        return createdAt;
    }

    /**
     * Gets the expiration timestamp.
     *
     * @return When this entry expires
     */
    public Instant getExpiresAt() {
        return expiresAt;
    }

    /**
     * Gets the last access timestamp.
     *
     * @return When this entry was last accessed
     */
    public Instant getLastAccessedAt() {
        return lastAccessedAt;
    }

    /**
     * Gets the number of times this entry has been accessed.
     *
     * @return The hit count
     */
    public int getHitCount() {
        return hitCount;
    }

    /**
     * Gets the age of this entry in milliseconds.
     *
     * @return Milliseconds since creation
     */
    public long getAgeMillis() {
        return Instant.now().toEpochMilli() - createdAt.toEpochMilli();
    }

    @Override
    public String toString() {
        return "CacheEntry{" +
            "expired=" + isExpired() +
            ", hitCount=" + hitCount +
            ", ttlMillis=" + getTimeToLiveMillis() +
            '}';
    }
}
