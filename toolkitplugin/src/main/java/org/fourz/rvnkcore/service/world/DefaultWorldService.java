package org.fourz.rvnkcore.service.world;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.plugin.java.JavaPlugin;
import org.fourz.rvnkcore.api.dto.WorldDTO;
import org.fourz.rvnkcore.api.service.WorldService;
import org.fourz.rvnkcore.database.repository.WorldRepository;
import org.fourz.rvnkcore.util.log.LogManager;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Default implementation of WorldService.
 * Provides basic world tracking and management functionality.
 */
public class DefaultWorldService implements WorldService {
    
    private final WorldRepository worldRepository;
    private final LogManager logger;
    
    public DefaultWorldService(WorldRepository worldRepository, JavaPlugin plugin) {
        this.worldRepository = worldRepository;
        this.logger = LogManager.getInstance(plugin, getClass());
    }
    
    @Override
    public CompletableFuture<Optional<WorldDTO>> getWorld(String worldName) {
        return worldRepository.findByName(worldName);
    }
    
    @Override
    public CompletableFuture<List<WorldDTO>> getAllWorlds() {
        return worldRepository.findAll();
    }
    
    @Override
    public CompletableFuture<List<WorldDTO>> getActiveWorlds() {
        return worldRepository.findActiveWorlds();
    }
    
    @Override
    public CompletableFuture<List<WorldDTO>> getWorldsByEnvironment(String environment) {
        return worldRepository.findByEnvironment(environment);
    }
    
    @Override
    public CompletableFuture<List<WorldDTO>> getRecentlyAccessedWorlds(int limit) {
        return worldRepository.findRecentlyAccessed(limit);
    }
    
    @Override
    public CompletableFuture<List<WorldDTO>> getWorldsWithPlayers() {
        return worldRepository.findWorldsWithPlayers();
    }
    
    @Override
    public CompletableFuture<Void> registerWorld(World world) {
        return CompletableFuture.supplyAsync(() -> {
            // Create WorldDTO from Bukkit World
            WorldDTO worldDTO = createWorldDTOFromBukkit(world);
            return worldDTO;
        }).thenCompose(worldDTO -> {
            // Save to repository
            return worldRepository.save(worldDTO);
        });
    }
    
    /**
     * Register a world with existence checking for proper logging.
     * Used internally to differentiate between new registrations and metadata updates.
     * 
     * @param world The Bukkit World to register
     * @return CompletableFuture containing boolean indicating if world was new (true) or updated (false)
     */
    private CompletableFuture<Boolean> registerWorldWithExistenceCheck(World world) {
        return worldRepository.exists(world.getName())
            .thenCompose(exists -> {
                WorldDTO worldDTO = createWorldDTOFromBukkit(world);
                return worldRepository.save(worldDTO)
                    .thenApply(v -> !exists); // Return true if world was new (didn't exist before)
            });
    }
    
    @Override
    public CompletableFuture<Void> updatePlayerCount(String worldName, int playerCount) {
        return worldRepository.updatePlayerCount(worldName, playerCount);
    }
    
    @Override
    public CompletableFuture<Void> addPlaytime(String worldName, long playtimeSeconds) {
        // For now, return completed future - implement when needed
        return CompletableFuture.completedFuture(null);
    }
    
    @Override
    public CompletableFuture<Void> setActiveStatus(String worldName, boolean isActive) {
        return worldRepository.updateActiveStatus(worldName, isActive);
    }
    
    @Override
    public CompletableFuture<List<WorldDTO>> getWorldStatistics() {
        return worldRepository.getWorldStatistics();
    }
    
    @Override
    public CompletableFuture<List<WorldDTO>> getWorldsForPlayer(String playerUuid) {
        return worldRepository.findWorldsForPlayer(playerUuid);
    }
    
    @Override
    public CompletableFuture<List<WorldPlayerCorrelation>> getWorldPlayerCorrelation(String playerUuid) {
        // For now, return empty list - implement when needed
        return CompletableFuture.completedFuture(new ArrayList<>());
    }
    
    @Override
    public CompletableFuture<Void> syncLoadedWorlds() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Get all currently loaded worlds from Bukkit
                List<World> loadedWorlds = new ArrayList<>(Bukkit.getWorlds());
                
                if (loadedWorlds.isEmpty()) {
                    logger.info("No worlds currently loaded for sync");
                    return null;
                }
                
                logger.info("Starting world sync - found " + loadedWorlds.size() + " loaded worlds");
                
                // Track completion of all world registrations
                List<CompletableFuture<Void>> registrationFutures = new ArrayList<>();
                
                for (World world : loadedWorlds) {
                    logger.debug("Processing world for sync: " + world.getName() + " [" + world.getEnvironment() + "]");
                    
                    // Register each world individually with existence checking for proper logging
                    CompletableFuture<Void> registration = registerWorldWithExistenceCheck(world)
                        .thenCompose(isNew -> setActiveStatus(world.getName(), true)
                            .thenApply(v -> isNew)) // Pass through the isNew flag
                        .whenComplete((isNew, throwable) -> {
                            if (throwable != null) {
                                logger.error("Failed to process world: " + world.getName(), throwable);
                            } else {
                                int currentPlayers = world.getPlayers().size();
                                String action = isNew ? "registered" : "updated metadata for";
                                logger.info("Successfully " + action + " world: " + world.getName() + 
                                          " [" + world.getEnvironment() + ", " + world.getDifficulty() + 
                                          ", Players: " + currentPlayers + "/" + currentPlayers + " max]");
                            }
                        })
                        .thenApply(v -> null); // Convert back to Void for compatibility
                    
                    registrationFutures.add(registration);
                }
                
                // Wait for all registrations to complete
                CompletableFuture.allOf(registrationFutures.toArray(new CompletableFuture[0]))
                    .whenComplete((result, throwable) -> {
                        if (throwable != null) {
                            logger.error("Some worlds failed to process during sync", throwable);
                        } else {
                            logger.info("World sync completed successfully - all " + loadedWorlds.size() + " worlds processed");
                        }
                    });
                
                return null;
                
            } catch (Exception e) {
                logger.error("Failed to sync loaded worlds", e);
                throw new RuntimeException("World sync failed", e);
            }
        });
    }
    
    @Override
    public CompletableFuture<Void> removeWorldTracking(String worldName) {
        return worldRepository.delete(worldName);
    }
    
    /**
     * Creates a WorldDTO from a Bukkit World object.
     * Extracts comprehensive world metadata for database storage.
     * 
     * @param world The Bukkit World to convert
     * @return WorldDTO populated with world data
     */
    private WorldDTO createWorldDTOFromBukkit(World world) {
        WorldDTO dto = new WorldDTO();
        
        // Basic information
        dto.setName(world.getName());
        dto.setDisplayName(world.getName()); // Use world name as display name
        dto.setEnvironment(world.getEnvironment().toString());
        dto.setWorldFolder(world.getWorldFolder().getAbsolutePath());
        dto.setSeed(world.getSeed());
        
        // World type and generator
        dto.setWorldType(world.getWorldType().toString());
        if (world.getGenerator() != null) {
            dto.setGeneratorName(world.getGenerator().getClass().getSimpleName());
        }
        
        // Difficulty
        dto.setDifficulty(world.getDifficulty().toString());
        
        // Spawn location
        Location spawnLocation = world.getSpawnLocation();
        dto.setSpawnX(spawnLocation.getX());
        dto.setSpawnY(spawnLocation.getY());
        dto.setSpawnZ(spawnLocation.getZ());
        
        // World border
        WorldBorder border = world.getWorldBorder();
        dto.setWorldBorderSize(border.getSize());
        dto.setWorldBorderCenterX(border.getCenter().getX());
        dto.setWorldBorderCenterZ(border.getCenter().getZ());
        
        // World settings
        dto.setIsActive(true); // World is being registered, so it's active
        dto.setIsAutoSave(world.isAutoSave());
        dto.setKeepSpawnInMemory(world.getKeepSpawnInMemory());
        dto.setAllowAnimals(world.getAllowAnimals());
        dto.setAllowMonsters(world.getAllowMonsters());
        dto.setAllowPvp(world.getPVP());
        dto.setWeatherEnabled(true); // Assume weather enabled by default
        dto.setThunderEnabled(world.isThundering());
        
        // Tracking data
        LocalDateTime now = LocalDateTime.now();
        dto.setFirstLoaded(now);
        dto.setLastAccessed(now);
        dto.setTotalPlaytimeSeconds(0L);
        dto.setPlayerCount(world.getPlayers().size());
        dto.setMaxPlayersSeen(world.getPlayers().size());
        dto.setCreatedAt(now);
        dto.setUpdatedAt(now);
        
        return dto;
    }
}
