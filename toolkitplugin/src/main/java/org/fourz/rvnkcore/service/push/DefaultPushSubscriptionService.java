package org.fourz.rvnkcore.service.push;

import org.fourz.rvnkcore.api.model.PushSubscriptionDTO;
import org.fourz.rvnkcore.api.service.PushSubscriptionService;
import org.fourz.rvnkcore.database.repository.PushSubscriptionRepository;
import org.fourz.rvnkcore.util.log.LogManager;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Default implementation of {@link PushSubscriptionService}.
 * Delegates all operations to {@link PushSubscriptionRepository}.
 *
 * @since 1.6.0
 */
public class DefaultPushSubscriptionService implements PushSubscriptionService {

    private final PushSubscriptionRepository repository;
    private final LogManager logger;

    public DefaultPushSubscriptionService(PushSubscriptionRepository repository, LogManager logger) {
        this.repository = repository;
        this.logger = logger;

        // Ensure table exists on service creation
        repository.createTable()
                .exceptionally(ex -> {
                    logger.error("Failed to initialize push subscriptions table", (Throwable) ex);
                    return null;
                });
    }

    @Override
    public CompletableFuture<Void> saveSubscription(PushSubscriptionDTO subscription) {
        return repository.save(subscription).thenApply(saved -> null);
    }

    @Override
    public CompletableFuture<Void> deleteByEndpoint(String endpoint) {
        return repository.deleteByEndpoint(endpoint);
    }

    @Override
    public CompletableFuture<List<PushSubscriptionDTO>> getAllSubscriptions() {
        return repository.findAll();
    }

    @Override
    public CompletableFuture<List<PushSubscriptionDTO>> getSubscriptionsByPlayer(String playerId) {
        return repository.findByPlayerId(playerId);
    }
}
