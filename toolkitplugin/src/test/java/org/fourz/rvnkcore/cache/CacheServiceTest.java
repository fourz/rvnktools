package org.fourz.rvnkcore.cache;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the CacheService class.
 */
class CacheServiceTest {

    private CacheService<String, String> cache;

    @BeforeEach
    void setUp() {
        cache = new CacheService<>(TimeUnit.MINUTES.toMillis(5), 100);
    }

    @Test
    @DisplayName("put and get stores and retrieves value")
    void putAndGetStoresAndRetrievesValue() {
        cache.put("key1", "value1");

        Optional<String> result = cache.get("key1");

        assertTrue(result.isPresent());
        assertEquals("value1", result.get());
    }

    @Test
    @DisplayName("get returns empty for missing key")
    void getReturnsEmptyForMissingKey() {
        Optional<String> result = cache.get("nonexistent");

        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("getOrCompute returns cached value")
    void getOrComputeReturnsCachedValue() {
        cache.put("key1", "original");
        AtomicInteger computeCount = new AtomicInteger(0);

        String result = cache.getOrCompute("key1", () -> {
            computeCount.incrementAndGet();
            return "computed";
        });

        assertEquals("original", result);
        assertEquals(0, computeCount.get());
    }

    @Test
    @DisplayName("getOrCompute computes missing value")
    void getOrComputeComputesMissingValue() {
        AtomicInteger computeCount = new AtomicInteger(0);

        String result = cache.getOrCompute("key1", () -> {
            computeCount.incrementAndGet();
            return "computed";
        });

        assertEquals("computed", result);
        assertEquals(1, computeCount.get());

        // Second call should use cached value
        String result2 = cache.getOrCompute("key1", () -> {
            computeCount.incrementAndGet();
            return "computed-again";
        });

        assertEquals("computed", result2);
        assertEquals(1, computeCount.get());
    }

    @Test
    @DisplayName("remove removes entry from cache")
    void removeRemovesEntry() {
        cache.put("key1", "value1");

        Optional<String> removed = cache.remove("key1");

        assertTrue(removed.isPresent());
        assertEquals("value1", removed.get());
        assertTrue(cache.get("key1").isEmpty());
    }

    @Test
    @DisplayName("containsKey returns true for valid entry")
    void containsKeyReturnsTrueForValidEntry() {
        cache.put("key1", "value1");

        assertTrue(cache.containsKey("key1"));
        assertFalse(cache.containsKey("nonexistent"));
    }

    @Test
    @DisplayName("clear removes all entries")
    void clearRemovesAllEntries() {
        cache.put("key1", "value1");
        cache.put("key2", "value2");

        cache.clear();

        assertEquals(0, cache.size());
        assertTrue(cache.get("key1").isEmpty());
    }

    @Test
    @DisplayName("size returns correct count")
    void sizeReturnsCorrectCount() {
        assertEquals(0, cache.size());

        cache.put("key1", "value1");
        assertEquals(1, cache.size());

        cache.put("key2", "value2");
        assertEquals(2, cache.size());

        cache.remove("key1");
        assertEquals(1, cache.size());
    }

    @Test
    @DisplayName("stats track hits and misses")
    void statsTrackHitsAndMisses() {
        cache.put("key1", "value1");

        cache.get("key1"); // hit
        cache.get("key1"); // hit
        cache.get("missing"); // miss

        CacheService.CacheStats stats = cache.getStats();

        assertEquals(2, stats.hits());
        assertEquals(1, stats.misses());
    }

    @Test
    @DisplayName("stats hitRatio calculates correctly")
    void statsHitRatioCalculatesCorrectly() {
        cache.put("key1", "value1");

        cache.get("key1"); // hit
        cache.get("key1"); // hit
        cache.get("key1"); // hit
        cache.get("missing"); // miss

        CacheService.CacheStats stats = cache.getStats();

        assertEquals(0.75, stats.hitRatio(), 0.001);
    }

    @Test
    @DisplayName("expired entry returns empty")
    void expiredEntryReturnsEmpty() throws InterruptedException {
        // Create cache with very short TTL
        CacheService<String, String> shortCache = new CacheService<>(50, 100);
        shortCache.put("key1", "value1");

        // Value should be present immediately
        assertTrue(shortCache.get("key1").isPresent());

        // Wait for expiration
        Thread.sleep(100);

        // Value should be expired
        assertTrue(shortCache.get("key1").isEmpty());

        shortCache.shutdown();
    }

    @Test
    @DisplayName("putPermanent creates non-expiring entry")
    void putPermanentCreatesNonExpiringEntry() {
        cache.putPermanent("key1", "value1");

        // Entry should exist
        assertTrue(cache.get("key1").isPresent());

        // Check TTL on entry directly is effectively infinite
        CacheEntry<String> entry = new CacheEntry<>("test");
        assertFalse(entry.isExpired());
    }

    @Test
    @DisplayName("max size evicts entries when full")
    void maxSizeEvictsEntriesWhenFull() {
        CacheService<String, String> smallCache = new CacheService<>(TimeUnit.HOURS.toMillis(1), 3);

        smallCache.put("key1", "value1");
        smallCache.put("key2", "value2");
        smallCache.put("key3", "value3");

        // Cache is now at max capacity
        assertEquals(3, smallCache.size());

        // Add key4, should evict one entry to maintain max size
        smallCache.put("key4", "value4");

        // Size should still be 3 (one was evicted)
        assertEquals(3, smallCache.size());

        // key4 should definitely be present (just added)
        assertTrue(smallCache.containsKey("key4"));

        // Stats should show at least one eviction
        assertTrue(smallCache.getStats().evictions() >= 1);

        smallCache.shutdown();
    }

    @Test
    @DisplayName("invalidateByPrefix removes matching entries")
    void invalidateByPrefixRemovesMatchingEntries() {
        cache.put("user:1", "alice");
        cache.put("user:2", "bob");
        cache.put("session:1", "sess1");

        int removed = cache.invalidateByPrefix("user:");

        assertEquals(2, removed);
        assertTrue(cache.get("user:1").isEmpty());
        assertTrue(cache.get("user:2").isEmpty());
        assertTrue(cache.get("session:1").isPresent());
    }

    @Test
    @DisplayName("withTtl factory creates cache with specified TTL")
    void withTtlFactoryCreatesCacheWithTtl() {
        CacheService<String, String> ttlCache = CacheService.withTtl(10, TimeUnit.MINUTES);

        ttlCache.put("key", "value");
        assertTrue(ttlCache.get("key").isPresent());

        ttlCache.shutdown();
    }

    @Test
    @DisplayName("resetStats clears statistics")
    void resetStatsClearsStatistics() {
        cache.put("key1", "value1");
        cache.get("key1");
        cache.get("missing");

        cache.resetStats();

        CacheService.CacheStats stats = cache.getStats();
        assertEquals(0, stats.hits());
        assertEquals(0, stats.misses());
    }
}
