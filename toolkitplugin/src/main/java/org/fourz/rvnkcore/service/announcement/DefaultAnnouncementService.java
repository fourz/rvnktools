package org.fourz.rvnkcore.service.announcement;

import org.fourz.rvnkcore.api.model.AnnouncementDTO;
import org.fourz.rvnkcore.api.model.AnnouncementTypeDTO;
import org.fourz.rvnkcore.api.service.AnnouncementService;
import org.fourz.rvnkcore.api.webhook.WebhookNotifier;
import org.fourz.rvnkcore.database.repository.AnnouncementRepository;
import org.fourz.rvnkcore.database.repository.AnnouncementTypeRepository;
import org.fourz.rvnkcore.service.registry.ServiceRegistry;
import org.fourz.rvnkcore.util.log.LogManager;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Default implementation of AnnouncementService.
 * 
 * Provides comprehensive announcement management with caching, validation,
 * and performance optimization. All operations are asynchronous to prevent
 * blocking the main thread.
 * 
 * @since 1.0.0
 */
public class DefaultAnnouncementService implements AnnouncementService {
    
    private final AnnouncementRepository repository;
    private final AnnouncementTypeRepository typeRepository;
    private final LogManager logger;
    private final ServiceRegistry serviceRegistry;

    // Cache for frequently accessed announcements
    private final ConcurrentHashMap<String, AnnouncementDTO> cache;
    private final AtomicInteger cacheHits;
    private final AtomicInteger cacheMisses;

    /**
     * Constructor for DefaultAnnouncementService.
     *
     * @param repository The announcement repository for database operations
     * @param logger The logger instance
     */
    public DefaultAnnouncementService(AnnouncementRepository repository, LogManager logger) {
        this(repository, null, logger, null);
    }

    /**
     * Constructor with ServiceRegistry for lazy webhook resolution.
     * WebhookNotifier is resolved lazily because it is registered after core services.
     *
     * @param repository The announcement repository for database operations
     * @param logger The logger instance
     * @param serviceRegistry ServiceRegistry for resolving WebhookNotifier at call time (may be null)
     */
    public DefaultAnnouncementService(AnnouncementRepository repository, LogManager logger, ServiceRegistry serviceRegistry) {
        this(repository, null, logger, serviceRegistry);
    }

    /**
     * Full constructor with type repository support.
     *
     * @param repository The announcement repository for database operations
     * @param typeRepository The announcement type repository (may be null for backwards compatibility)
     * @param logger The logger instance
     * @param serviceRegistry ServiceRegistry for resolving WebhookNotifier at call time (may be null)
     */
    public DefaultAnnouncementService(AnnouncementRepository repository, AnnouncementTypeRepository typeRepository,
                                      LogManager logger, ServiceRegistry serviceRegistry) {
        this.repository = repository;
        this.typeRepository = typeRepository;
        this.logger = logger;
        this.serviceRegistry = serviceRegistry;
        this.cache = new ConcurrentHashMap<>();
        this.cacheHits = new AtomicInteger(0);
        this.cacheMisses = new AtomicInteger(0);

        logger.info("DefaultAnnouncementService initialized");
    }
    
    @Override
    public CompletableFuture<AnnouncementDTO> createAnnouncement(AnnouncementDTO announcement) {
        if (announcement == null) {
            throw new IllegalArgumentException("Announcement cannot be null");
        }
        
        // Validate required fields
        if (announcement.getMessage() == null || announcement.getMessage().trim().isEmpty()) {
            throw new IllegalArgumentException("Announcement message cannot be null or empty");
        }
        
        if (announcement.getType() == null || announcement.getType().trim().isEmpty()) {
            throw new IllegalArgumentException("Announcement type cannot be null or empty");
        }
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Generate ID if not provided
                if (announcement.getId() == null) {
                    announcement.setId(generateAnnouncementId());
                }
                
                // Set creation timestamps
                Timestamp now = Timestamp.valueOf(LocalDateTime.now());
                announcement.setCreatedAt(now);
                announcement.setUpdatedAt(now);
                
                // Ensure active by default if not set
                if (!announcement.isActive()) {
                    announcement.setActive(true);
                }
                
                logger.debug("Creating announcement with ID: " + announcement.getId());
                
                return repository.save(announcement)
                    .thenApply(savedAnnouncement -> {
                        // Update cache
                        cache.put(savedAnnouncement.getId(), savedAnnouncement);
                        logger.info("Created announcement: " + savedAnnouncement.getId());
                        notifyWebhook(savedAnnouncement.getId());
                        return savedAnnouncement;
                    })
                    .join();

            } catch (Exception e) {
                logger.error("Failed to create announcement", e);
                throw new org.fourz.rvnkcore.api.exception.ServiceException("Announcement creation failed", e);
            }
        });
    }
    
    @Override
    public CompletableFuture<Optional<AnnouncementDTO>> getAnnouncement(String id) {
        if (id == null) {
            throw new IllegalArgumentException("Announcement ID cannot be null");
        }
        
        // Check cache first
        AnnouncementDTO cached = cache.get(id);
        if (cached != null) {
            cacheHits.incrementAndGet();
            logger.debug("Cache hit for announcement: " + id);
            return CompletableFuture.completedFuture(Optional.of(cached));
        }
        
        cacheMisses.incrementAndGet();
        return repository.findById(id)
            .thenApply(optional -> {
                // Update cache if found
                optional.ifPresent(announcement -> {
                    cache.put(id, announcement);
                    logger.debug("Cached announcement: " + id);
                });
                return optional;
            })
            .exceptionally(ex -> {
                logger.error("Failed to retrieve announcement: " + id, ex);
                throw new org.fourz.rvnkcore.api.exception.ServiceException("Announcement retrieval failed", ex);
            });
    }
    
    @Override
    public CompletableFuture<AnnouncementDTO> updateAnnouncement(AnnouncementDTO announcement) {
        if (announcement == null) {
            throw new IllegalArgumentException("Announcement cannot be null");
        }
        
        if (announcement.getId() == null) {
            throw new IllegalArgumentException("Announcement ID cannot be null for updates");
        }
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Fetch existing record to preserve fields not in the update request
                AnnouncementDTO existing = repository.findById(announcement.getId()).join().orElse(null);
                if (existing != null) {
                    if (announcement.getCreatedAt() == null) {
                        announcement.setCreatedAt(existing.getCreatedAt());
                    }
                    if (announcement.getType() == null) {
                        announcement.setType(existing.getType());
                    }
                    if (announcement.getMessage() == null) {
                        announcement.setMessage(existing.getMessage());
                    }
                    if (announcement.getTitle() == null) {
                        announcement.setTitle(existing.getTitle());
                    }
                    if (announcement.getOwnerUuid() == null) {
                        announcement.setOwnerUuid(existing.getOwnerUuid());
                    }
                }

                // Update timestamp
                announcement.setUpdatedAt(Timestamp.valueOf(LocalDateTime.now()));

                return repository.save(announcement)
                    .thenApply(updatedAnnouncement -> {
                        // Update cache
                        cache.put(updatedAnnouncement.getId(), updatedAnnouncement);
                        logger.info("Updated announcement: " + updatedAnnouncement.getId());
                        notifyWebhook(updatedAnnouncement.getId());
                        return updatedAnnouncement;
                    })
                    .join();

            } catch (Exception e) {
                logger.error("Failed to update announcement: " + announcement.getId(), e);
                throw new org.fourz.rvnkcore.api.exception.ServiceException("Announcement update failed", e);
            }
        });
    }
    
    @Override
    public CompletableFuture<Void> deleteAnnouncement(String id) {
        if (id == null) {
            throw new IllegalArgumentException("Announcement ID cannot be null");
        }
        
        return repository.deleteById(id)
            .thenRun(() -> {
                // Remove from cache
                cache.remove(id);
                logger.info("Deleted announcement: " + id);
                notifyWebhook(id);
            })
            .exceptionally(ex -> {
                logger.error("Failed to delete announcement: " + id, ex);
                throw new org.fourz.rvnkcore.api.exception.ServiceException("Announcement deletion failed", ex);
            });
    }
    
    @Override
    public CompletableFuture<List<AnnouncementDTO>> getAnnouncementsPage(int limit, int offset) {
        logger.debug("Retrieving announcements page (limit=" + limit + ", offset=" + offset + ")");
        return repository.findPage(limit, offset)
            .exceptionally(ex -> {
                logger.error("Failed to retrieve announcements page", ex);
                throw new org.fourz.rvnkcore.api.exception.ServiceException("Failed to retrieve announcements page", ex);
            });
    }

    @Override
    public CompletableFuture<List<AnnouncementDTO>> getAllAnnouncements() {
        logger.debug("Retrieving all announcements");
        return repository.findAll()
            .exceptionally(ex -> {
                logger.error("Failed to retrieve all announcements", ex);
                throw new org.fourz.rvnkcore.api.exception.ServiceException("Failed to retrieve announcements", ex);
            });
    }
    
    @Override
    public CompletableFuture<List<AnnouncementDTO>> getActiveAnnouncements() {
        logger.debug("Retrieving active announcements");
        return repository.findActiveAnnouncements()
            .exceptionally(ex -> {
                logger.error("Failed to retrieve active announcements", ex);
                throw new org.fourz.rvnkcore.api.exception.ServiceException("Failed to retrieve active announcements", ex);
            });
    }
    
    @Override
    public CompletableFuture<List<AnnouncementDTO>> getAnnouncementsByType(String type) {
        if (type == null) {
            throw new IllegalArgumentException("Announcement type cannot be null");
        }
        
        logger.debug("Retrieving announcements by type: " + type);
        return repository.findByType(type)
            .exceptionally(ex -> {
                logger.error("Failed to retrieve announcements by type: " + type, ex);
                throw new org.fourz.rvnkcore.api.exception.ServiceException("Failed to retrieve announcements by type", ex);
            });
    }
    
    @Override
    public CompletableFuture<List<AnnouncementDTO>> searchAnnouncements(String searchPattern) {
        if (searchPattern == null) {
            throw new IllegalArgumentException("Search pattern cannot be null");
        }
        
        logger.debug("Searching announcements with pattern: " + searchPattern);
        return repository.searchByContent(searchPattern)
            .exceptionally(ex -> {
                logger.error("Failed to search announcements with pattern: " + searchPattern, ex);
                throw new org.fourz.rvnkcore.api.exception.ServiceException("Announcement search failed", ex);
            });
    }
    
    @Override
    public CompletableFuture<List<AnnouncementDTO>> getAnnouncementsForWorld(String worldName) {
        if (worldName == null) {
            throw new IllegalArgumentException("World name cannot be null");
        }
        
        logger.debug("Retrieving announcements for world: " + worldName);
        return repository.findByTargetWorld(worldName)
            .exceptionally(ex -> {
                logger.error("Failed to retrieve announcements for world: " + worldName, ex);
                throw new org.fourz.rvnkcore.api.exception.ServiceException("Failed to retrieve announcements for world", ex);
            });
    }
    
    @Override
    public CompletableFuture<List<AnnouncementDTO>> getAnnouncementsForGroup(String groupName) {
        if (groupName == null) {
            throw new IllegalArgumentException("Group name cannot be null");
        }
        
        logger.debug("Retrieving announcements for group: " + groupName);
        return repository.findByTargetGroup(groupName)
            .exceptionally(ex -> {
                logger.error("Failed to retrieve announcements for group: " + groupName, ex);
                throw new org.fourz.rvnkcore.api.exception.ServiceException("Failed to retrieve announcements for group", ex);
            });
    }
    
    @Override
    public CompletableFuture<Long> getAnnouncementCount() {
        logger.debug("Getting total announcement count");
        return repository.count()
            .exceptionally(ex -> {
                logger.error("Failed to count announcements", ex);
                throw new org.fourz.rvnkcore.api.exception.ServiceException("Failed to count announcements", ex);
            });
    }
    
    @Override
    public CompletableFuture<Long> getActiveAnnouncementCount() {
        logger.debug("Getting active announcement count");
        return repository.countActiveAnnouncements()
            .exceptionally(ex -> {
                logger.error("Failed to count active announcements", ex);
                throw new org.fourz.rvnkcore.api.exception.ServiceException("Failed to count active announcements", ex);
            });
    }
    
    @Override
    public CompletableFuture<Boolean> announcementExists(String id) {
        if (id == null) {
            throw new IllegalArgumentException("Announcement ID cannot be null");
        }
        
        // Check cache first
        if (cache.containsKey(id)) {
            return CompletableFuture.completedFuture(true);
        }
        
        return repository.existsById(id)
            .exceptionally(ex -> {
                logger.error("Failed to check if announcement exists: " + id, ex);
                throw new org.fourz.rvnkcore.api.exception.ServiceException("Failed to check announcement existence", ex);
            });
    }
    
    @Override
    public CompletableFuture<Void> activateAnnouncement(String id) {
        if (id == null) {
            throw new IllegalArgumentException("Announcement ID cannot be null");
        }
        
        logger.info("Activating announcement: " + id);
        return repository.updateActiveStatus(id, true)
            .thenRun(() -> {
                // Update cache if present
                AnnouncementDTO cached = cache.get(id);
                if (cached != null) {
                    cached.setActive(true);
                    cached.setUpdatedAt(Timestamp.valueOf(LocalDateTime.now()));
                }
                logger.info("Activated announcement: " + id);
                notifyWebhook(id);
            })
            .exceptionally(ex -> {
                // Handle "not found" errors more gracefully than system errors
                if (ex.getCause() instanceof org.fourz.rvnkcore.api.exception.DatabaseException &&
                    ex.getCause().getMessage().contains("No announcement found with ID:")) {
                    logger.warning("Announcement not found for activation: " + id);
                } else {
                    logger.error("Failed to activate announcement: " + id, ex);
                }
                throw new org.fourz.rvnkcore.api.exception.ServiceException("Failed to activate announcement", ex);
            });
    }
    
    @Override
    public CompletableFuture<Void> deactivateAnnouncement(String id) {
        if (id == null) {
            throw new IllegalArgumentException("Announcement ID cannot be null");
        }
        
        logger.info("Deactivating announcement: " + id);
        return repository.updateActiveStatus(id, false)
            .thenRun(() -> {
                // Update cache if present
                AnnouncementDTO cached = cache.get(id);
                if (cached != null) {
                    cached.setActive(false);
                    cached.setUpdatedAt(Timestamp.valueOf(LocalDateTime.now()));
                }
                logger.info("Deactivated announcement: " + id);
                notifyWebhook(id);
            })
            .exceptionally(ex -> {
                // Handle "not found" errors more gracefully than system errors
                if (ex.getCause() instanceof org.fourz.rvnkcore.api.exception.DatabaseException &&
                    ex.getCause().getMessage().contains("No announcement found with ID:")) {
                    logger.warning("Announcement not found for deactivation: " + id);
                } else {
                    logger.error("Failed to deactivate announcement: " + id, ex);
                }
                throw new org.fourz.rvnkcore.api.exception.ServiceException("Failed to deactivate announcement", ex);
            });
    }
    
    @Override
    public CompletableFuture<Void> updateAnnouncementMetadata(String id, String key, Object value) {
        if (id == null) {
            throw new IllegalArgumentException("Announcement ID cannot be null");
        }
        if (key == null) {
            throw new IllegalArgumentException("Metadata key cannot be null");
        }
        
        return getAnnouncement(id)
            .thenCompose(optional -> {
                if (!optional.isPresent()) {
                    throw new org.fourz.rvnkcore.api.exception.ServiceException("Announcement not found: " + id);
                }
                
                AnnouncementDTO announcement = optional.get();
                announcement.setMetadata(key, value);
                return updateAnnouncement(announcement);
            })
            .thenRun(() -> logger.debug("Updated metadata for announcement: " + id))
            .exceptionally(ex -> {
                logger.error("Failed to update metadata for announcement: " + id, ex);
                throw new org.fourz.rvnkcore.api.exception.ServiceException("Failed to update announcement metadata", ex);
            });
    }
    
    @Override
    public CompletableFuture<List<AnnouncementDTO>> bulkCreateAnnouncements(List<AnnouncementDTO> announcements) {
        if (announcements == null) {
            throw new IllegalArgumentException("Announcements list cannot be null");
        }
        
        logger.info("Starting bulk creation of " + announcements.size() + " announcements");
        
        return CompletableFuture.supplyAsync(() -> {
            List<AnnouncementDTO> createdAnnouncements = new ArrayList<>();
            
            for (AnnouncementDTO announcement : announcements) {
                try {
                    // Create the announcement (this will generate ID and set timestamps)
                    AnnouncementDTO created = createAnnouncement(announcement).join();
                    createdAnnouncements.add(created);
                    
                } catch (Exception e) {
                    logger.warning("Failed to create announcement: " + e.getMessage());
                }
            }
            
            int count = createdAnnouncements.size();
            logger.info("Successfully created " + count + " announcements");
            return createdAnnouncements;
        });
    }

    @Override
    public CompletableFuture<Integer> bulkImportAnnouncements(List<AnnouncementDTO> announcements) {
        if (announcements == null) {
            throw new IllegalArgumentException("Announcements list cannot be null");
        }
        
        logger.info("Starting bulk import of " + announcements.size() + " announcements");
        
        return CompletableFuture.supplyAsync(() -> {
            AtomicInteger imported = new AtomicInteger(0);
            
            for (AnnouncementDTO announcement : announcements) {
                try {
                    // Check if already exists (skip for new announcements without ID)
                    if (announcement.getId() != null && announcementExists(announcement.getId()).join()) {
                        logger.debug("Skipping existing announcement: " + announcement.getId());
                        continue;
                    }

                    // Create the announcement
                    createAnnouncement(announcement).join();
                    imported.incrementAndGet();
                    
                } catch (Exception e) {
                    logger.warning("Failed to import announcement: " + announcement.getId() + " - " + e.getMessage());
                }
            }
            
            int count = imported.get();
            logger.info("Successfully imported " + count + " announcements");
            return count;
        });
    }
    
    @Override
    public CompletableFuture<java.util.Map<String, Object>> getAnnouncementMetrics() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                java.util.Map<String, Object> metrics = new java.util.HashMap<>();
                
                // Basic counts
                CompletableFuture<Long> totalCountFuture = getAnnouncementCount();
                CompletableFuture<Long> activeCountFuture = getActiveAnnouncementCount();
                CompletableFuture<List<AnnouncementDTO>> allAnnouncementsFuture = getAllAnnouncements();
                
                // Wait for all basic metrics
                Long totalCount = totalCountFuture.join();
                Long activeCount = activeCountFuture.join();
                List<AnnouncementDTO> allAnnouncements = allAnnouncementsFuture.join();
                
                metrics.put("total_announcements", totalCount);
                metrics.put("active_announcements", activeCount);
                metrics.put("inactive_announcements", totalCount - activeCount);
                
                // Type breakdown
                java.util.Map<String, Long> typeBreakdown = new java.util.HashMap<>();
                java.util.Map<String, Long> activeTypeBreakdown = new java.util.HashMap<>();
                
                for (AnnouncementDTO announcement : allAnnouncements) {
                    String type = announcement.getType() != null ? announcement.getType() : "unknown";
                    typeBreakdown.merge(type, 1L, Long::sum);
                    
                    if (announcement.isActive()) {
                        activeTypeBreakdown.merge(type, 1L, Long::sum);
                    }
                }
                
                metrics.put("type_breakdown", typeBreakdown);
                metrics.put("active_type_breakdown", activeTypeBreakdown);
                
                // Cache statistics
                java.util.Map<String, Object> cacheMetrics = new java.util.HashMap<>();
                cacheMetrics.put("size", cache.size());
                cacheMetrics.put("hits", cacheHits.get());
                cacheMetrics.put("misses", cacheMisses.get());
                long totalRequests = cacheHits.get() + cacheMisses.get();
                double hitRate = totalRequests > 0 ? (double) cacheHits.get() / totalRequests : 0.0;
                cacheMetrics.put("hit_rate", hitRate);
                metrics.put("cache", cacheMetrics);
                
                // Recent activity (last 24 hours)
                java.time.LocalDateTime yesterday = java.time.LocalDateTime.now().minusDays(1);
                java.sql.Timestamp yesterdayTs = java.sql.Timestamp.valueOf(yesterday);
                
                long recentCount = allAnnouncements.stream()
                    .filter(a -> a.getCreatedAt() != null && a.getCreatedAt().after(yesterdayTs))
                    .count();
                    
                long recentUpdates = allAnnouncements.stream()
                    .filter(a -> a.getUpdatedAt() != null && a.getUpdatedAt().after(yesterdayTs))
                    .count();
                
                java.util.Map<String, Object> recentActivity = new java.util.HashMap<>();
                recentActivity.put("created_last_24h", recentCount);
                recentActivity.put("updated_last_24h", recentUpdates);
                metrics.put("recent_activity", recentActivity);
                
                // Generation timestamp
                metrics.put("generated_at", java.time.Instant.now().toString());
                
                return metrics;
                
            } catch (Exception e) {
                logger.error("Failed to generate announcement metrics", e);
                throw new RuntimeException("Failed to generate announcement metrics", e);
            }
        });
    }
    
    @Override
    public CompletableFuture<List<AnnouncementTypeDTO>> getAnnouncementTypes() {
        // Use type repository if available (DB-backed types)
        if (typeRepository != null) {
            return typeRepository.findAll()
                .thenApply(types -> {
                    if (!types.isEmpty()) {
                        return types;
                    }
                    // Fallback: derive types from existing announcements if types table is empty
                    return deriveTypesFromAnnouncements();
                })
                .exceptionally(ex -> {
                    logger.warning("Failed to load types from DB, deriving from announcements: " + ex.getMessage());
                    return deriveTypesFromAnnouncements();
                });
        }

        // Legacy fallback: derive types dynamically from announcements
        return CompletableFuture.completedFuture(deriveTypesFromAnnouncements());
    }

    /**
     * Gets the type repository for direct access (used by AnnounceManager for migration).
     *
     * @return The AnnouncementTypeRepository, or null if not configured
     */
    public AnnouncementTypeRepository getTypeRepository() {
        return typeRepository;
    }

    private List<AnnouncementTypeDTO> deriveTypesFromAnnouncements() {
        try {
            List<AnnouncementDTO> announcements = getAllAnnouncements().join();
            java.util.Map<String, AnnouncementTypeDTO> typeMap = new java.util.LinkedHashMap<>();
            for (AnnouncementDTO a : announcements) {
                String type = a.getType();
                if (type != null && !type.isEmpty() && !typeMap.containsKey(type)) {
                    AnnouncementTypeDTO dto = new AnnouncementTypeDTO.Builder()
                        .id(type)
                        .name(type.substring(0, 1).toUpperCase() + type.substring(1))
                        .build();
                    typeMap.put(type, dto);
                }
            }
            return new ArrayList<>(typeMap.values());
        } catch (Exception e) {
            logger.error("Failed to derive types from announcements", e);
            return new ArrayList<>();
        }
    }

    /**
     * Generates a unique announcement ID.
     * 
     * @return A unique announcement ID
     */
    private String generateAnnouncementId() {
        return "ann_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }
    
    /**
     * Gets cache statistics for debugging.
     * 
     * @return String containing cache statistics
     */
    public String getCacheStats() {
        int hits = cacheHits.get();
        int misses = cacheMisses.get();
        int total = hits + misses;
        double hitRate = total > 0 ? (double) hits / total * 100 : 0;
        
        return String.format("Cache stats - Size: %d, Hits: %d, Misses: %d, Hit Rate: %.2f%%", 
                           cache.size(), hits, misses, hitRate);
    }
    
    /**
     * Deactivates an announcement due to payment depletion.
     * Sets active=false and adds deactivation_reason=payment_depleted to metadata.
     *
     * @param id The announcement ID
     * @return CompletableFuture that completes when deactivation is finished
     */
    public CompletableFuture<Void> deactivateForPayment(String id) {
        return deactivateAnnouncement(id)
            .thenCompose(v -> updateAnnouncementMetadata(id, "deactivation_reason", "payment_depleted"));
    }

    /**
     * Reactivates an announcement that was deactivated for payment.
     * Sets active=true and clears the deactivation_reason metadata.
     *
     * @param id The announcement ID
     * @return CompletableFuture that completes when reactivation is finished
     */
    public CompletableFuture<Void> reactivateFromPayment(String id) {
        return activateAnnouncement(id)
            .thenCompose(v -> updateAnnouncementMetadata(id, "deactivation_reason", ""));
    }

    /**
     * Clears the announcement cache.
     */
    public void clearCache() {
        cache.clear();
        cacheHits.set(0);
        cacheMisses.set(0);
        logger.info("Announcement cache cleared");
    }

    /**
     * Notifies the webhook of an announcement change if configured.
     * Resolves WebhookNotifier lazily from ServiceRegistry since it is
     * registered after core services during initialization.
     *
     * @param announcementId The ID of the changed announcement for targeted cache invalidation
     */
    private void notifyWebhook(String announcementId) {
        if (serviceRegistry == null || !serviceRegistry.hasService(WebhookNotifier.class)) return;
        serviceRegistry.getService(WebhookNotifier.class).notifyAnnouncementChange(announcementId);
    }
}
