package org.fourz.rvnkcore.api.service;

import org.fourz.rvnkcore.api.model.PushSubscriptionDTO;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Service interface for managing web push notification subscriptions.
 *
 * @since 1.6.0
 */
public interface PushSubscriptionService {

    CompletableFuture<Void> saveSubscription(PushSubscriptionDTO subscription);

    CompletableFuture<Void> deleteByEndpoint(String endpoint);

    CompletableFuture<List<PushSubscriptionDTO>> getAllSubscriptions();

    CompletableFuture<List<PushSubscriptionDTO>> getSubscriptionsByPlayer(String playerId);
}
