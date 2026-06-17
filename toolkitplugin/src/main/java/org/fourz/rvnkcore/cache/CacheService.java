package org.fourz.rvnkcore.cache;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * In-memory cache service for frequently accessed data.
 *
 * Provides a simple key-value cache with configurable TTL,
 * automatic expiration cleanup, and basic statistics.
 *
 * @param <K> The key type
 * @param <V> The value type
 */
public class CacheService<K, V> {

    // Access-order LinkedHashMap makes evictOldest() O(1): eldest entry is always at head.
    // Wrapped in synchronizedMap for thread safety; iteration must synchronize on cache.
    private final Map<K, CacheEntry<V>> cache;
    private final long defaultTtlMillis;
    private final int maxSize;
    private final ScheduledExecutorService cleanupExecutor;

    // Statistics
    private volatile long hits;
    private volatile long misses;
    private volatile long evictions;

    /**
     * Creates a cache with default settings (5 minute TTL, 1000 max entries).
     */
    public CacheService() {
        this(TimeUnit.MINUTES.toMillis(5), 1000);
    }

    /**
     * Creates a cache with custom settings.
     *
     * @param defaultTtlMillis Default time-to-live for entries
     * @param maxSize Maximum number of entries
     */
    public CacheService(long defaultTtlMillis, int maxSize) {
        this.cache = Collections.synchronizedMap(new LinkedHashMap<>(maxSize, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, CacheEntry<V>> eldest) {
                if (size() > maxSize) {
                    evictions++;
                    return true;
                }
                return false;
            }
        });
        this.defaultTtlMillis = defaultTtlMillis;
        this.maxSize = maxSize;
        this.hits = 0;
        this.misses = 0;
        this.evictions = 0;

        // Start cleanup task
        this.cleanupExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "CacheService-Cleanup");
            t.setDaemon(true);
            return t;
        });
        cleanupExecutor.scheduleAtFixedRate(this::cleanupExpired, 1, 1, TimeUnit.MINUTES);
    }

    /**
     * Creates a cache for the specified duration.
     *
     * @param ttl The TTL value
     * @param unit The time unit
     * @param <K> Key type
     * @param <V> Value type
     * @return A new cache service
     */
    public static <K, V> CacheService<K, V> withTtl(long ttl, TimeUnit unit) {
        return new CacheService<>(unit.toMillis(ttl), 1000);
    }

    /**
     * Creates a cache with no expiration.
     *
     * @param maxSize Maximum entries
     * @param <K> Key type
     * @param <V> Value type
     * @return A new cache service
     */
    public static <K, V> CacheService<K, V> noExpiration(int maxSize) {
        return new CacheService<>(Long.MAX_VALUE, maxSize);
    }

    /**
     * Gets a value from the cache.
     *
     * @param key The cache key
     * @return The cached value, or empty if not found or expired
     */
    public Optional<V> get(K key) {
        CacheEntry<V> entry = cache.get(key);

        if (entry == null) {
            misses++;
            return Optional.empty();
        }

        if (entry.isExpired()) {
            cache.remove(key);
            misses++;
            evictions++;
            return Optional.empty();
        }

        hits++;
        return Optional.of(entry.getValue());
    }

    /**
     * Gets a value from cache, or computes and caches it if absent.
     *
     * @param key The cache key
     * @param supplier Supplier to compute the value if not cached
     * @return The cached or computed value
     */
    public V getOrCompute(K key, Supplier<V> supplier) {
        return get(key).orElseGet(() -> {
            V value = supplier.get();
            put(key, value);
            return value;
        });
    }

    /**
     * Gets a value from cache, or computes and caches it with custom TTL.
     *
     * @param key The cache key
     * @param supplier Supplier to compute the value if not cached
     * @param ttlMillis Custom TTL for this entry
     * @return The cached or computed value
     */
    public V getOrCompute(K key, Supplier<V> supplier, long ttlMillis) {
        return get(key).orElseGet(() -> {
            V value = supplier.get();
            put(key, value, ttlMillis);
            return value;
        });
    }

    /**
     * Puts a value in the cache with default TTL.
     *
     * @param key The cache key
     * @param value The value to cache
     */
    public void put(K key, V value) {
        put(key, value, defaultTtlMillis);
    }

    /**
     * Puts a value in the cache with custom TTL.
     *
     * @param key The cache key
     * @param value The value to cache
     * @param ttlMillis Time-to-live in milliseconds
     */
    public void put(K key, V value, long ttlMillis) {
        // LinkedHashMap with removeEldestEntry handles size enforcement automatically.
        cache.put(key, new CacheEntry<>(value, ttlMillis));
    }

    /**
     * Puts a value in the cache that never expires.
     *
     * @param key The cache key
     * @param value The value to cache
     */
    public void putPermanent(K key, V value) {
        cache.put(key, new CacheEntry<>(value));
    }

    /**
     * Removes a value from the cache.
     *
     * @param key The cache key
     * @return The removed value, or empty if not found
     */
    public Optional<V> remove(K key) {
        CacheEntry<V> entry = cache.remove(key);
        return entry != null ? Optional.of(entry.peekValue()) : Optional.empty();
    }

    /**
     * Checks if a key exists and is not expired.
     *
     * @param key The cache key
     * @return true if the key exists and is valid
     */
    public boolean containsKey(K key) {
        CacheEntry<V> entry = cache.get(key);
        if (entry == null) {
            return false;
        }
        if (entry.isExpired()) {
            cache.remove(key);
            evictions++;
            return false;
        }
        return true;
    }

    /**
     * Gets the current cache size.
     *
     * @return Number of entries (may include expired)
     */
    public int size() {
        return cache.size();
    }

    /**
     * Clears all entries from the cache.
     */
    public void clear() {
        cache.clear();
    }

    /**
     * Invalidates all entries matching a key prefix (for String keys).
     *
     * @param prefix The key prefix to match
     * @return Number of entries invalidated
     */
    public int invalidateByPrefix(String prefix) {
        int count = 0;
        synchronized (cache) {
            java.util.Iterator<K> it = cache.keySet().iterator();
            while (it.hasNext()) {
                K key = it.next();
                if (key.toString().startsWith(prefix)) {
                    it.remove();
                    count++;
                }
            }
        }
        return count;
    }

    /**
     * Gets cache statistics.
     *
     * @return A CacheStats object
     */
    public CacheStats getStats() {
        return new CacheStats(hits, misses, evictions, cache.size(), maxSize);
    }

    /**
     * Resets cache statistics.
     */
    public void resetStats() {
        hits = 0;
        misses = 0;
        evictions = 0;
    }

    /**
     * Shuts down the cache and releases resources.
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
        cache.clear();
    }

    private void cleanupExpired() {
        synchronized (cache) {
            cache.entrySet().removeIf(entry -> {
                if (entry.getValue().isExpired()) {
                    evictions++;
                    return true;
                }
                return false;
            });
        }
    }

    /**
     * Cache statistics snapshot.
     */
    public record CacheStats(
        long hits,
        long misses,
        long evictions,
        int currentSize,
        int maxSize
    ) {
        /**
         * Gets the cache hit ratio.
         *
         * @return Hit ratio (0.0 to 1.0)
         */
        public double hitRatio() {
            long total = hits + misses;
            return total > 0 ? (double) hits / total : 0.0;
        }

        /**
         * Gets the fill percentage.
         *
         * @return Fill percentage (0.0 to 1.0)
         */
        public double fillRatio() {
            return maxSize > 0 ? (double) currentSize / maxSize : 0.0;
        }

        @Override
        public String toString() {
            return String.format(
                "CacheStats{hits=%d, misses=%d, evictions=%d, size=%d/%d, hitRatio=%.2f%%}",
                hits, misses, evictions, currentSize, maxSize, hitRatio() * 100
            );
        }
    }
}
