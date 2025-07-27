package org.fourz.rvnktools.dhlogfilter;

import org.bukkit.plugin.Plugin;
import org.fourz.rvnktools.util.log.LogManager;

import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;
import java.util.Map;
import java.util.List;

/**
 * Default implementation of DHLogFilterService.
 * 
 * Provides log filtering functionality using configurable rules and rate limiting
 * to reduce console spam from repetitive messages while maintaining visibility
 * of important warnings and errors.
 */
public class DefaultDHLogFilterService implements DHLogFilterService {
    
    private final Plugin plugin;
    private final LogManager logger;
    private final DHLogFilterConfigRepository configRepository;
    
    // Filter state
    private final AtomicBoolean filterActive = new AtomicBoolean(false);
    private volatile DHLogFilterConfig currentConfig;
    private volatile LocalDateTime filterStartTime;
    private volatile LocalDateTime lastStatsReset;
    
    // Statistics tracking
    private final AtomicLong messagesFiltered = new AtomicLong(0);
    private final AtomicLong messagesAllowed = new AtomicLong(0);
    private final AtomicLong totalProcessingTimeNanos = new AtomicLong(0);
    
    // Rate limiting cache
    private final Map<String, LocalDateTime> messageCache = new ConcurrentHashMap<>();
    
    // Compiled regex patterns cache
    private volatile List<Pattern> compiledPatterns;
    
    /**
     * Constructor for DefaultDHLogFilterService.
     * 
     * @param plugin The plugin instance
     */
    public DefaultDHLogFilterService(Plugin plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, getClass());
        this.configRepository = new DHLogFilterConfigRepository(plugin);
        this.lastStatsReset = LocalDateTime.now();
    }
    
    @Override
    public CompletableFuture<Void> applyFilter() {
        return configRepository.loadConfiguration()
            .thenCompose(config -> {
                this.currentConfig = config;
                
                if (!config.isEnabled()) {
                    logger.info("DH log filter is disabled in configuration");
                    return CompletableFuture.completedFuture(null);
                }
                
                return CompletableFuture.runAsync(() -> {
                    try {
                        // Compile regex patterns if needed
                        if (config.isUseRegexPatterns()) {
                            compileRegexPatterns(config.getKeywords());
                        }
                        
                        // Start filter
                        filterActive.set(true);
                        filterStartTime = LocalDateTime.now();
                        
                        logger.info("DH log filter applied with level: " + config.getLevel().getDisplayName());
                        logger.info("Filtering " + config.getKeywords().size() + " keyword patterns");
                        
                    } catch (Exception e) {
                        logger.error("Failed to apply DH log filter", e);
                        filterActive.set(false);
                        throw new RuntimeException("Filter application failed", e);
                    }
                });
            });
    }
    
    @Override
    public CompletableFuture<Void> removeFilter() {
        return CompletableFuture.runAsync(() -> {
            if (filterActive.compareAndSet(true, false)) {
                logger.info("DH log filter removed");
                
                // Clear cache
                messageCache.clear();
                compiledPatterns = null;
            } else {
                logger.warning("Attempted to remove filter that was not active");
            }
        });
    }
    
    @Override
    public CompletableFuture<Boolean> reloadConfiguration() {
        logger.info("Reloading DH log filter configuration...");
        
        return configRepository.loadConfiguration()
            .thenCompose(config -> {
                this.currentConfig = config;
                
                if (filterActive.get()) {
                    // Reapply filter with new configuration
                    return applyFilter().thenApply(v -> true);
                } else {
                    logger.info("Configuration reloaded, but filter is not active");
                    return CompletableFuture.completedFuture(true);
                }
            })
            .exceptionally(ex -> {
                logger.error("Failed to reload configuration", ex);
                return false;
            });
    }
    
    @Override
    public CompletableFuture<DHLogFilterStats> getFilterStats() {
        return CompletableFuture.supplyAsync(() -> {
            DHLogLevel currentLevel = currentConfig != null ? currentConfig.getLevel() : DHLogLevel.INFO;
            int keywordRules = currentConfig != null ? currentConfig.getKeywords().size() : 0;
            
            long totalMessages = messagesFiltered.get() + messagesAllowed.get();
            double avgProcessingTime = totalMessages > 0 ? 
                (double) totalProcessingTimeNanos.get() / totalMessages / 1_000_000.0 : 0.0;
            
            return new DHLogFilterStats(
                filterActive.get(),
                currentLevel,
                messagesFiltered.get(),
                messagesAllowed.get(),
                filterStartTime,
                lastStatsReset,
                keywordRules,
                messageCache.size(),
                avgProcessingTime
            );
        });
    }
    
    @Override
    public CompletableFuture<Void> setLogLevel(DHLogLevel level) {
        return CompletableFuture.runAsync(() -> {
            if (currentConfig != null) {
                currentConfig.setLevel(level);
                logger.info("Log level temporarily set to: " + level.getDisplayName());
            } else {
                throw new IllegalStateException("No configuration loaded");
            }
        });
    }
    
    @Override
    public CompletableFuture<Boolean> isFilterActive() {
        return CompletableFuture.completedFuture(filterActive.get());
    }
    
    /**
     * Evaluates whether a log message should be filtered.
     * This method is called by the log filtering mechanism.
     * 
     * @param message The log message to evaluate
     * @param level The log level of the message
     * @return true if the message should be filtered (blocked)
     */
    public boolean shouldFilterMessage(String message, DHLogLevel level) {
        if (!filterActive.get() || currentConfig == null) {
            return false;
        }
        
        long startTime = System.nanoTime();
        
        try {
            // Check log level filtering
            if (!currentConfig.getLevel().shouldShow(level)) {
                messagesFiltered.incrementAndGet();
                return true;
            }
            
            // Check keyword filtering with rate limiting
            if (isKeywordMatch(message)) {
                String cacheKey = generateCacheKey(message);
                LocalDateTime lastSeen = messageCache.get(cacheKey);
                LocalDateTime now = LocalDateTime.now();
                
                if (lastSeen != null) {
                    long secondsBetween = java.time.Duration.between(lastSeen, now).getSeconds();
                    if (secondsBetween < currentConfig.getRateLimitSeconds()) {
                        // Message is within rate limit window, filter it
                        messagesFiltered.incrementAndGet();
                        return true;
                    }
                }
                
                // Update cache with current time
                messageCache.put(cacheKey, now);
                
                // Clean up old cache entries periodically
                if (messageCache.size() > 1000) {
                    cleanupCache();
                }
            }
            
            messagesAllowed.incrementAndGet();
            return false;
            
        } finally {
            long processingTime = System.nanoTime() - startTime;
            totalProcessingTimeNanos.addAndGet(processingTime);
        }
    }
    
    /**
     * Checks if a message matches any of the configured keywords.
     * 
     * @param message The message to check
     * @return true if the message matches a keyword
     */
    private boolean isKeywordMatch(String message) {
        if (currentConfig.getKeywords().isEmpty()) {
            return false;
        }
        
        if (currentConfig.isUseRegexPatterns() && compiledPatterns != null) {
            // Use compiled regex patterns
            return compiledPatterns.stream()
                .anyMatch(pattern -> pattern.matcher(message).find());
        } else {
            // Use simple string contains matching
            return currentConfig.getKeywords().stream()
                .anyMatch(message::contains);
        }
    }
    
    /**
     * Compiles regex patterns from keyword strings.
     * 
     * @param keywords The keyword strings to compile
     */
    private void compileRegexPatterns(List<String> keywords) {
        try {
            compiledPatterns = keywords.stream()
                .map(Pattern::compile)
                .toList();
            logger.debug("Compiled " + compiledPatterns.size() + " regex patterns");
        } catch (Exception e) {
            logger.error("Failed to compile regex patterns, falling back to string matching", e);
            currentConfig.setUseRegexPatterns(false);
        }
    }
    
    /**
     * Generates a cache key for rate limiting based on message content.
     * 
     * @param message The message to generate a key for
     * @return A cache key string
     */
    private String generateCacheKey(String message) {
        // Use a hash of the message to avoid storing full messages in memory
        return String.valueOf(message.hashCode());
    }
    
    /**
     * Cleans up old entries from the rate limiting cache.
     */
    private void cleanupCache() {
        LocalDateTime cutoff = LocalDateTime.now().minusSeconds(currentConfig.getRateLimitSeconds() * 2);
        
        messageCache.entrySet().removeIf(entry -> entry.getValue().isBefore(cutoff));
        
        logger.debug("Cleaned up cache, current size: " + messageCache.size());
    }
    
    /**
     * Resets statistics counters.
     */
    public void resetStats() {
        messagesFiltered.set(0);
        messagesAllowed.set(0);
        totalProcessingTimeNanos.set(0);
        lastStatsReset = LocalDateTime.now();
        logger.info("DH log filter statistics reset");
    }
}