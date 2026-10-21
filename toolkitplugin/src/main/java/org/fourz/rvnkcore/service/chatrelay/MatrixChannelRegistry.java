package org.fourz.rvnkcore.service.chatrelay;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.fourz.rvnkcore.api.config.ChatRelayConfig;
import org.fourz.rvnkcore.api.model.MatrixChannel;
import org.fourz.rvnkcore.util.log.LogManager;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Cached channel list of the MATRIX chat room (#2380, #2381).
 *
 * <p>The Matrix bridge publishes the list at {@code GET /v1/channels}. This tier fetches it at
 * startup, every 5 minutes and on {@code rvnkcore reload}, with the Matrix peer's own
 * {@code api-key} as {@code X-API-Key}. The URL is the Matrix peer's url with
 * {@code /chat/inbound} replaced by {@code /channels}, unless {@code chat-relay.matrix.channels-url}
 * is set. The fetch always verifies TLS: the key must never go to a certificate nobody checked.</p>
 *
 * <p>A failed fetch keeps the last good list. Before the first good fetch, only {@code general} is
 * offered, so a tier with no bridge (or a bridge not yet serving the route) behaves as before.</p>
 */
public final class MatrixChannelRegistry {

    /** Refetch period. */
    static final long PERIOD_SECONDS = 300;
    /** Upper bound on the entries kept from one response. */
    static final int MAX_CHANNELS = 64;
    static final int MAX_LABEL = 24;

    private final LogManager logger;
    private final HttpClient http;
    private volatile ScheduledExecutorService scheduler;

    private volatile URI url;
    private volatile String apiKey;
    private volatile int timeoutMs = 3000;

    private volatile List<MatrixChannel> channels = List.of(MatrixChannel.DEFAULT_GENERAL);
    private volatile long fetchedAt;
    private volatile String lastProblem;

    public MatrixChannelRegistry(LogManager logger) {
        this.logger = logger;
        this.http = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    /** Picks the URL and key from the config. No Matrix peer = no fetch, only {@code general}. */
    public void configure(ChatRelayConfig config) {
        ChatRelayConfig.Peer peer = null;
        if (config != null && config.isEnabled()) {
            for (ChatRelayConfig.Peer p : config.getPeers()) {
                if (config.isMatrixOrigin(p.id())) {
                    peer = p;
                    break;
                }
            }
        }
        if (peer == null || peer.apiKey() == null || peer.apiKey().isBlank()) {
            this.url = null;
            this.apiKey = null;
            return;
        }
        String raw = config.getMatrixChannelsUrl();
        if (raw == null || raw.isBlank()) raw = deriveUrl(peer.url());
        this.url = (raw != null && raw.startsWith("https://")) ? URI.create(raw) : null;
        this.apiKey = peer.apiKey();
        this.timeoutMs = Math.max(1000, config.getTimeoutMs());
        if (this.url == null) {
            logger.warning("Matrix channels: no https list URL for peer '" + peer.id() + "' - offering general only");
        }
    }

    /**
     * {@code https://host/v1/chat/inbound} becomes {@code https://host/v1/channels}. Null when the
     * peer url has no {@code /chat/inbound} ending.
     */
    static String deriveUrl(String peerUrl) {
        if (peerUrl == null) return null;
        String u = peerUrl.trim();
        while (u.endsWith("/")) u = u.substring(0, u.length() - 1);
        String tail = "/chat/inbound";
        if (!u.endsWith(tail)) return null;
        return u.substring(0, u.length() - tail.length()) + "/channels";
    }

    /** Starts the startup fetch and the 5-minute refetch. Safe to call once. */
    public synchronized void start() {
        if (scheduler != null) return;
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "RVNKCore-MatrixChannels");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleWithFixedDelay(this::fetchQuietly, 5, PERIOD_SECONDS, TimeUnit.SECONDS);
    }

    /** Fetches now, off the calling thread (used by {@code rvnkcore reload}). */
    public void refreshNow() {
        ScheduledExecutorService s = scheduler;
        if (s != null && !s.isShutdown()) s.execute(this::fetchQuietly);
    }

    public synchronized void stop() {
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }
    }

    private void fetchQuietly() {
        try {
            fetch();
        } catch (RuntimeException e) {
            problem("fetch failed: " + e.getClass().getSimpleName());
        }
    }

    private void fetch() {
        URI target = this.url;
        String key = this.apiKey;
        if (target == null || key == null) return;
        HttpRequest request = HttpRequest.newBuilder(target)
                .header("X-API-Key", key)
                .header("Accept", "application/json")
                .timeout(Duration.ofMillis(timeoutMs))
                .GET()
                .build();
        HttpResponse<String> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        } catch (Exception e) {
            problem("fetch failed: " + e.getClass().getSimpleName());
            return;
        }
        if (response.statusCode() != 200) {
            problem("HTTP " + response.statusCode());
            return;
        }
        List<MatrixChannel> parsed = parse(response.body(), msg -> logger.warning("Matrix channels: " + msg));
        if (parsed == null) {
            problem("response is not a channel list");
            return;
        }
        apply(parsed, System.currentTimeMillis());
    }

    /** Installs a fetched list. Logs only when the set of names changes or a problem clears. */
    void apply(List<MatrixChannel> list, long now) {
        List<String> before = names();
        this.channels = List.copyOf(list);
        this.fetchedAt = now;
        String cleared = this.lastProblem;
        this.lastProblem = null;
        if (!before.equals(names()) || cleared != null) {
            logger.info("Matrix channels: " + names().size() + " " + names());
        }
    }

    /** Records a fetch problem; logs it once until it changes or clears. The cache is kept. */
    private void problem(String what) {
        if (!what.equals(lastProblem)) {
            logger.warning("Matrix channels: " + what + " from " + url + " - keeping "
                    + names().size() + " cached channel(s)");
        } else {
            logger.debug("Matrix channels: " + what + " (repeat)");
        }
        lastProblem = what;
    }

    /**
     * Parses a {@code {"channels":[...]}} body. Bad entries are dropped with a warning; the result
     * always holds {@code general}, first. Null when the body is not a channel list at all.
     */
    static List<MatrixChannel> parse(String body, Consumer<String> warn) {
        JsonArray arr;
        try {
            JsonElement root = JsonParser.parseString(body == null ? "" : body);
            if (!root.isJsonObject()) return null;
            JsonElement el = root.getAsJsonObject().get("channels");
            if (el == null || !el.isJsonArray()) return null;
            arr = el.getAsJsonArray();
        } catch (RuntimeException e) {
            return null;
        }
        List<MatrixChannel> out = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (JsonElement e : arr) {
            if (out.size() >= MAX_CHANNELS) {
                warn.accept("more than " + MAX_CHANNELS + " channels - the rest are ignored");
                break;
            }
            if (!e.isJsonObject()) continue;
            JsonObject o = e.getAsJsonObject();
            String name = str(o, "name");
            name = name == null ? null : name.trim().toLowerCase(Locale.ROOT);
            if (!MatrixChannel.isValidName(name)) {
                warn.accept("dropped a channel with an invalid name");
                continue;
            }
            if (!seen.add(name)) {
                warn.accept("dropped duplicate channel '" + name + "'");
                continue;
            }
            String join = normalizeJoin(str(o, "join"));
            if (join == null) {
                warn.accept("dropped channel '" + name + "': join must be all, staff or perm:<node>");
                continue;
            }
            String label = MatrixInbound.clean(str(o, "label"), MAX_LABEL);
            boolean readOnly = o.has("readOnly") && o.get("readOnly").isJsonPrimitive()
                    && o.get("readOnly").getAsBoolean();
            out.add(new MatrixChannel(name, label.isEmpty() ? name : label, readOnly, join));
        }
        if (!seen.contains(MatrixChannel.GENERAL)) {
            warn.accept("list has no 'general' - added the default");
            out.add(0, MatrixChannel.DEFAULT_GENERAL);
        } else if (!MatrixChannel.GENERAL.equals(out.get(0).name())) {
            MatrixChannel general = out.stream()
                    .filter(c -> MatrixChannel.GENERAL.equals(c.name())).findFirst().orElseThrow();
            out.remove(general);
            out.add(0, general);
        }
        return out;
    }

    /** {@code all}, {@code staff} or {@code perm:<node>} (node {@code [a-z0-9._-]+}); null otherwise. */
    static String normalizeJoin(String raw) {
        if (raw == null || raw.isBlank()) return MatrixChannel.JOIN_ALL;
        String j = raw.trim().toLowerCase(Locale.ROOT);
        if (MatrixChannel.JOIN_ALL.equals(j) || MatrixChannel.JOIN_STAFF.equals(j)) return j;
        if (j.startsWith(MatrixChannel.JOIN_PERM_PREFIX)) {
            String node = j.substring(MatrixChannel.JOIN_PERM_PREFIX.length());
            if (!node.isEmpty() && node.length() <= 128 && node.matches("[a-z0-9._-]+")) return j;
        }
        return null;
    }

    private static String str(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return (e != null && e.isJsonPrimitive()) ? e.getAsString() : null;
    }

    // ── Reads (any thread) ──

    /** The cached list, {@code general} first. Never empty. */
    public List<MatrixChannel> list() {
        return channels;
    }

    public Optional<MatrixChannel> get(String name) {
        String n = MatrixChannel.resolveName(name);
        if (n == null) return Optional.empty();
        for (MatrixChannel c : channels) {
            if (c.name().equals(n)) return Optional.of(c);
        }
        return Optional.empty();
    }

    public boolean contains(String name) {
        return get(name).isPresent();
    }

    public List<String> names() {
        List<String> out = new ArrayList<>(channels.size());
        for (MatrixChannel c : channels) out.add(c.name());
        return out;
    }

    /** Epoch ms of the last good fetch; 0 = never (only {@code general} is offered). */
    public long getFetchedAt() {
        return fetchedAt;
    }

    /** The current fetch problem, or null. */
    public String getLastProblem() {
        return lastProblem;
    }

    /** The list URL in use, or null when this tier has no Matrix peer. */
    public URI getUrl() {
        return url;
    }
}
