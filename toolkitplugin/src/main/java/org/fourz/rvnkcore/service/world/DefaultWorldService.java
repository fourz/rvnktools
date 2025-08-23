package org.fourz.rvnkcore.service.world;

import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;
import org.fourz.rvnkcore.api.dto.WorldDTO;
import org.fourz.rvnkcore.api.service.WorldService;
import org.fourz.rvnkcore.database.repository.WorldRepository;
import org.fourz.rvnktools.util.log.LogManager;

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
        // For now, return completed future - implement when needed
        return CompletableFuture.completedFuture(null);
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
        // For now, return completed future - implement when needed
        return CompletableFuture.completedFuture(null);
    }
    
    @Override
    public CompletableFuture<Void> removeWorldTracking(String worldName) {
        return worldRepository.delete(worldName);
    }
}
