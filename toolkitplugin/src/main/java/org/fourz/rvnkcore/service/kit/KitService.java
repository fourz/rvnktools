package org.fourz.rvnkcore.service.kit;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Kit definitions (#2415), off the main thread. Item encoding and the grant itself stay on the main
 * thread in the caller; this class only moves encoded kits to and from the {@link KitStore}.
 *
 * <p>Keeps the kit names from the last read for tab completion, so completion never touches the
 * database.</p>
 */
public class KitService {

    private final KitStore store;
    private final ThreadPoolExecutor pool;
    private volatile List<String> names = List.of();

    public KitService(KitStore store) {
        this.store = store;
        this.pool = new ThreadPoolExecutor(1, 2, 30L, TimeUnit.SECONDS, new ArrayBlockingQueue<>(32), r -> {
            Thread t = new Thread(r, "rvnkcore-kits");
            t.setDaemon(true);
            return t;
        }, new ThreadPoolExecutor.AbortPolicy());
    }

    public CompletableFuture<List<Kit>> list() {
        return call(() -> {
            List<Kit> kits = store.list();
            names = kits.stream().map(Kit::name).toList();
            return kits;
        });
    }

    public CompletableFuture<Optional<Kit>> get(String name) {
        return call(() -> store.get(name));
    }

    /** @return the stored version */
    public CompletableFuture<Integer> save(Kit kit) {
        return call(() -> {
            int v = store.save(kit);
            refreshNames();
            return v;
        });
    }

    public CompletableFuture<Boolean> delete(String name) {
        return call(() -> {
            boolean gone = store.delete(name);
            refreshNames();
            return gone;
        });
    }

    /** Kit names from the last read; never blocks. */
    public List<String> cachedNames() {
        return names;
    }

    /** Fills the name cache; failures leave it as it was. */
    public void warmNames() {
        list().exceptionally(ex -> List.of());
    }

    public void shutdown() {
        pool.shutdownNow();
    }

    private void refreshNames() throws SQLException {
        names = store.list().stream().map(Kit::name).toList();
    }

    private interface Sql<T> {
        T run() throws SQLException;
    }

    /** @throws java.util.concurrent.RejectedExecutionException when the queue is full */
    private <T> CompletableFuture<T> call(Sql<T> work) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return work.run();
            } catch (SQLException e) {
                throw new CompletionException(e);
            }
        }, pool);
    }
}
