package org.fourz.rvnkcore.service.whitelist;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** The server whitelist by UUID. Every change runs on the main thread. */
public class BukkitWhitelistStore implements WhitelistMembership.WhitelistStore {

    private final Plugin plugin;

    public BukkitWhitelistStore(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public CompletableFuture<Boolean> isWhitelisted(UUID uuid) {
        return onMain(() -> Bukkit.getOfflinePlayer(uuid).isWhitelisted());
    }

    /**
     * Adds by UUID with the account name. {@code getOfflinePlayer(UUID)} for a player who never joined
     * carries an empty name, and whitelist.json would store {@code "name": ""}. The named lookup can
     * block on a web call, so it runs off the main thread; it is used only when its UUID matches.
     */
    @Override
    public CompletableFuture<Void> add(WhitelistMembership.Profile profile) {
        CompletableFuture<OfflinePlayer> named = new CompletableFuture<>();
        if (profile.name() == null || profile.name().isBlank()) {
            named.complete(null);
        } else {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                try {
                    named.complete(Bukkit.getOfflinePlayer(profile.name()));
                } catch (RuntimeException e) {
                    named.complete(null);
                }
            });
        }
        return named.thenCompose(op -> onMain(() -> {
            OfflinePlayer target = op != null && profile.uuid().equals(op.getUniqueId())
                ? op : Bukkit.getOfflinePlayer(profile.uuid());
            target.setWhitelisted(true);
            return null;
        }));
    }

    @Override
    public CompletableFuture<Void> remove(UUID uuid) {
        return onMain(() -> {
            Bukkit.getOfflinePlayer(uuid).setWhitelisted(false);
            return null;
        });
    }

    private <T> CompletableFuture<T> onMain(java.util.function.Supplier<T> work) {
        CompletableFuture<T> f = new CompletableFuture<>();
        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                f.complete(work.get());
            } catch (RuntimeException e) {
                f.completeExceptionally(e);
            }
        });
        return f;
    }
}
