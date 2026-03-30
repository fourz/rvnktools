package org.fourz.rvnktools.listener;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.event.EventBus;
import net.luckperms.api.event.EventSubscription;
import net.luckperms.api.event.user.UserDataRecalculateEvent;
import net.luckperms.api.model.user.User;
import org.fourz.rvnkcore.RVNKCore;
import org.fourz.rvnkcore.api.service.PlayerService;
import org.fourz.rvnktools.permission.LuckPermsGroupResolver;
import org.fourz.rvnktools.permission.LuckPermsGroupResolver.GroupResult;
import org.fourz.rvnktools.permission.LuckPermsManager;
import org.fourz.rvnkcore.util.log.LogManager;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Listens to LuckPerms events and updates player data in RVNKCore.
 * 
 * This integration ensures that player permission group changes are
 * automatically synchronized with the RVNKCore player data system.
 * 
 * @since 1.0.0
 */
public class LuckPermsIntegrationListener {
    
    private final RVNKCore rvnkCore;
    private final LogManager logger;
    private final LuckPerms luckPerms;
    private final EventBus eventBus;
    private final List<EventSubscription<?>> subscriptions = new ArrayList<>();
    
    /**
     * Constructor for LuckPermsIntegrationListener.
     * 
     * @param rvnkCore The RVNKCore instance
     * @param plugin The plugin instance for logging
     */
    public LuckPermsIntegrationListener(RVNKCore rvnkCore, Plugin plugin) {
        this.rvnkCore = rvnkCore;
        this.logger = LogManager.getInstance(plugin, getClass());
        
        try {
            this.luckPerms = LuckPermsManager.getLuckPerms();
            this.eventBus = luckPerms.getEventBus();
            registerEventListeners();
            logger.info("LuckPerms integration listener initialized successfully");
        } catch (IllegalStateException e) {
            logger.warning("LuckPerms not available, permission group integration disabled: " + e.getMessage());
            throw e;
        }
    }
    
    /**
     * Registers event listeners with the LuckPerms event bus.
     */
    private void registerEventListeners() {
        subscriptions.add(
            eventBus.subscribe(UserDataRecalculateEvent.class, this::onUserDataRecalculate)
        );

        logger.debug("Registered LuckPerms event listeners");
    }
    
    /**
     * Handles user data recalculation events from LuckPerms.
     * This includes permission group changes, promotions, demotions, etc.
     * 
     * @param event The user data recalculate event
     */
    private void onUserDataRecalculate(UserDataRecalculateEvent event) {
        User user = event.getUser();
        UUID playerId = user.getUniqueId();
        
        logger.debug("Processing LuckPerms user data recalculation for: " + user.getFriendlyName() + " (" + playerId + ")");
        
        try {
            PlayerService playerService = rvnkCore.getService(PlayerService.class);
            
            // Check if player exists in RVNKCore first
            playerService.playerExists(playerId)
                .thenCompose(exists -> {
                    if (!exists) {
                        logger.debug("Player " + user.getFriendlyName() + " not found in RVNKCore, skipping group update");
                        return CompletableFuture.completedFuture(null);
                    }
                    
                    GroupResult groups = LuckPermsGroupResolver.resolveGroups(user);

                    logger.debug("Updating groups for " + user.getFriendlyName() +
                               ": primary=" + groups.primaryGroup() + ", all=" + groups.allGroups());

                    return playerService.updatePlayerGroups(playerId, groups.primaryGroup(), groups.allGroups());
                })
                .thenRun(() -> {
                    logger.debug("Successfully updated groups for " + user.getFriendlyName());
                })
                .exceptionally(throwable -> {
                    logger.error("Failed to update groups for " + user.getFriendlyName(), throwable);
                    return null;
                });
                
        } catch (Exception e) {
            logger.error("Failed to get PlayerService for LuckPerms group update", e);
        }
    }
    
    /**
     * Manually updates player groups for a specific player.
     * This can be called when needed to ensure synchronization.
     *
     * @param playerId The UUID of the player
     * @return CompletableFuture that completes when update is finished
     */
    public CompletableFuture<Void> updatePlayerGroups(UUID playerId) {
        return LuckPermsGroupResolver.resolveGroupsAsync(playerId)
            .thenCompose(groups -> {
                try {
                    PlayerService playerService = rvnkCore.getService(PlayerService.class);
                    return playerService.updatePlayerGroups(playerId, groups.primaryGroup(), groups.allGroups());
                } catch (Exception e) {
                    CompletableFuture<Void> future = new CompletableFuture<>();
                    future.completeExceptionally(e);
                    return future;
                }
            });
    }
    
    /**
     * Cleans up the event listeners when shutting down.
     */
    public void shutdown() {
        for (EventSubscription<?> sub : subscriptions) {
            try {
                sub.close();
            } catch (Exception e) {
                logger.debug("Error closing LuckPerms subscription: " + e.getMessage());
            }
        }
        subscriptions.clear();
        logger.info("LuckPerms event listeners unsubscribed");
    }
}
