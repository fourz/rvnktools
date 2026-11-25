package org.fourz.rvnkcore.api.service;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;

/**
 * Player name colors (#2468). RVNKCore stores and checks them; RVNKEvents draws them in chat and the tab list; the
 * WebUI sets them through {@code /v1/players/{uuid}/name-color}.
 *
 * <p>Values are lowercase {@code #rrggbb} (see {@link org.fourz.rvnkcore.api.model.NameColor}). They live in the
 * player preferences of plugin {@code rvnkcore}, metadata key {@code chat.namecolor}, so they follow the
 * {@code share-player-preferences} setting.</p>
 */
public interface NameColorService {

    /** Loads the stored color (and caches it). Empty when the player has none. */
    CompletableFuture<Optional<String>> getColor(UUID player);

    /** The cached color, or null. Safe on any thread; filled by {@link #getColor} and every change. */
    String cachedColor(UUID player);

    /**
     * Sets the color from what a player typed ({@code #RRGGBB}, {@code RRGGBB}, a name, or a reset word).
     * Completes with the stored {@code #rrggbb}, or null after a reset. Completes exceptionally with
     * {@link IllegalArgumentException} (message for the player) when the value is refused.
     */
    CompletableFuture<String> setColor(UUID player, String raw);

    /** Removes the color. */
    CompletableFuture<Void> reset(UUID player);

    /**
     * Whether the player holds {@code rvnk.chat.namecolor}, checked in LuckPerms so it works for offline players.
     * Completes with null when LuckPerms is not available.
     */
    CompletableFuture<Boolean> mayUse(UUID player);

    /** Called after every change with the new color (null after a reset), on the thread that saved it. */
    void addChangeListener(BiConsumer<UUID, String> listener);

    /** Drops the cached color (call on quit). */
    void forget(UUID player);
}
