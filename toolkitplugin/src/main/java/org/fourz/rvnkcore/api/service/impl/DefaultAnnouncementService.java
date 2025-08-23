package org.fourz.rvnkcore.api.service.impl;

import org.fourz.rvnkcore.api.service.AnnouncementService;
import org.fourz.rvnkcore.api.model.AnnouncementDTO;
import org.fourz.rvnkcore.database.repository.AnnouncementRepository;
import org.fourz.rvnkcore.api.exception.ServiceException;
import org.fourz.rvnktools.util.log.LogManager;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.UUID;

/**
 * Default implementation of AnnouncementService with caching and error handling.
 * Implements all 17 required methods from the AnnouncementService interface.
 */
public class DefaultAnnouncementService implements AnnouncementService {
    private final AnnouncementRepository repository;
    private final ConcurrentHashMap<String, CachedAnnouncement> cache;
    private final ScheduledExecutorService cacheMaintenanceExecutor;
    private final LogManager logger;
    private static final long CACHE_TTL_MS = 300_000; // 5 min
    private static final int MAX_CACHE_SIZE = 1000;
    private final AtomicLong cacheHits = new AtomicLong(0);
    private final AtomicLong cacheMisses = new AtomicLong(0);
    private volatile boolean shutdown = false;

    private static class CachedAnnouncement {
        private final AnnouncementDTO announcement;
        private final long expiresAt;
        CachedAnnouncement(AnnouncementDTO announcement, long ttlMs) {
            this.announcement = announcement;
            this.expiresAt = System.currentTimeMillis() + ttlMs;
        }
        boolean isExpired(long now) { return now > expiresAt; }
        AnnouncementDTO getAnnouncement() { return announcement; }
    }

    public DefaultAnnouncementService(AnnouncementRepository repository, LogManager logger) {
        this.repository = repository;
        this.logger = logger;
        this.cache = new ConcurrentHashMap<>();
        this.cacheMaintenanceExecutor = Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "AnnouncementService-CacheMaintenance"));
        cacheMaintenanceExecutor.scheduleAtFixedRate(this::evictExpiredEntries, 60, 60, TimeUnit.SECONDS);
    }

    private void evictExpiredEntries() {
        long now = System.currentTimeMillis();
        cache.entrySet().removeIf(e -> e.getValue().isExpired(now));
        if (cache.size() > MAX_CACHE_SIZE) {
            cache.clear();
        }
    }

    private Optional<AnnouncementDTO> getCached(String id) {
        CachedAnnouncement cached = cache.get(id);
        if (cached != null && !cached.isExpired(System.currentTimeMillis())) {
            cacheHits.incrementAndGet();
            return Optional.of(cached.getAnnouncement());
        }
        cacheMisses.incrementAndGet();
        return Optional.empty();
    }

    // === Core CRUD Operations ===

    @Override
    public CompletableFuture<AnnouncementDTO> createAnnouncement(AnnouncementDTO announcement) {
        if (shutdown) throw new ServiceException("Service is shut down");
        
        // Generate ID if not present
        if (announcement.getId() == null || announcement.getId().isEmpty()) {
            announcement.setId(UUID.randomUUID().toString());
        }
        
        return repository.save(announcement)
            .thenApply(saved -> {
                cache.put(saved.getId(), new CachedAnnouncement(saved, CACHE_TTL_MS));
                return saved;
            });
    }

    @Override
    public CompletableFuture<Optional<AnnouncementDTO>> getAnnouncement(String id) {
        if (shutdown) throw new ServiceException("Service is shut down");
        Optional<AnnouncementDTO> cached = getCached(id);
        if (cached.isPresent()) return CompletableFuture.completedFuture(cached);
        return repository.findById(id).thenApply(opt -> {
            opt.ifPresent(a -> cache.put(id, new CachedAnnouncement(a, CACHE_TTL_MS)));
            return opt;
        });
    }

    @Override
    public CompletableFuture<AnnouncementDTO> updateAnnouncement(AnnouncementDTO announcement) {
        if (shutdown) throw new ServiceException("Service is shut down");
        return repository.save(announcement).thenApply(updated -> {
            cache.put(announcement.getId(), new CachedAnnouncement(updated, CACHE_TTL_MS));
            return updated;
        });
    }

    @Override
    public CompletableFuture<Void> deleteAnnouncement(String id) {
        if (shutdown) throw new ServiceException("Service is shut down");
        return repository.deleteById(id).thenAccept(v -> cache.remove(id));
    }

    @Override
    public CompletableFuture<List<AnnouncementDTO>> getAllAnnouncements() {
        if (shutdown) throw new ServiceException("Service is shut down");
        return repository.findAll();
    }

    // === Query Operations ===

    @Override
    public CompletableFuture<List<AnnouncementDTO>> getActiveAnnouncements() {
        if (shutdown) throw new ServiceException("Service is shut down");
        return repository.findActiveAnnouncements();
    }

    @Override
    public CompletableFuture<List<AnnouncementDTO>> getAnnouncementsByType(String type) {
        if (shutdown) throw new ServiceException("Service is shut down");
        return repository.findByType(type);
    }

    @Override
    public CompletableFuture<List<AnnouncementDTO>> searchAnnouncements(String searchPattern) {
        if (shutdown) throw new ServiceException("Service is shut down");
        // TODO: Implement repository.searchByPattern(searchPattern)
        return CompletableFuture.completedFuture(List.of());
    }

    @Override
    public CompletableFuture<List<AnnouncementDTO>> getAnnouncementsForWorld(String worldName) {
        if (shutdown) throw new ServiceException("Service is shut down");
        return repository.findByTargetWorld(worldName);
    }

    @Override
    public CompletableFuture<List<AnnouncementDTO>> getAnnouncementsForGroup(String groupName) {
        if (shutdown) throw new ServiceException("Service is shut down");
        return repository.findByTargetGroup(groupName);
    }

    // === Count Operations ===

    @Override
    public CompletableFuture<Long> getAnnouncementCount() {
        if (shutdown) throw new ServiceException("Service is shut down");
        return repository.count();
    }

    @Override
    public CompletableFuture<Long> getActiveAnnouncementCount() {
        if (shutdown) throw new ServiceException("Service is shut down");
        return repository.countActiveAnnouncements();
    }

    @Override
    public CompletableFuture<Boolean> announcementExists(String id) {
        if (shutdown) throw new ServiceException("Service is shut down");
        return repository.existsById(id);
    }

    // === Status Management Operations ===

    @Override
    public CompletableFuture<Void> activateAnnouncement(String id) {
        if (shutdown) throw new ServiceException("Service is shut down");
        return repository.updateActiveStatus(id, true).thenAccept(v -> cache.remove(id));
    }

    @Override
    public CompletableFuture<Void> deactivateAnnouncement(String id) {
        if (shutdown) throw new ServiceException("Service is shut down");
        return repository.updateActiveStatus(id, false).thenAccept(v -> cache.remove(id));
    }

    @Override
    public CompletableFuture<Void> updateAnnouncementMetadata(String id, String key, Object value) {
        if (shutdown) throw new ServiceException("Service is shut down");
        return repository.updateMetadata(id, key, value).thenAccept(v -> cache.remove(id));
    }

    // === Bulk Operations ===

    @Override
    public CompletableFuture<Integer> bulkImportAnnouncements(List<AnnouncementDTO> announcements) {
        if (shutdown) throw new ServiceException("Service is shut down");
        return repository.batchInsert(announcements);
    }

    // === Service Management ===

    public void shutdown() {
        shutdown = true;
        cacheMaintenanceExecutor.shutdown();
        cache.clear();
    }

    public CacheStatistics getCacheStatistics() {
        long hits = cacheHits.get();
        long misses = cacheMisses.get();
        long total = hits + misses;
        double hitRate = total == 0 ? 0.0 : (double) hits / total;
        return new CacheStatistics(cache.size(), hitRate);
    }

    public static class CacheStatistics {
        public final int cacheSize;
        public final double hitRate;
        public CacheStatistics(int cacheSize, double hitRate) {
            this.cacheSize = cacheSize;
            this.hitRate = hitRate;
        }
    }
}
