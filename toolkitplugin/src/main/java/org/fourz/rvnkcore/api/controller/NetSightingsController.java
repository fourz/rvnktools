package org.fourz.rvnkcore.api.controller;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import jakarta.servlet.AsyncContext;
import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.fourz.rvnkcore.RVNKCore;
import org.fourz.rvnkcore.api.model.response.ApiResponse;
import org.fourz.rvnkcore.api.util.ApiUtils;
import org.fourz.rvnkcore.service.sightings.Sighting;
import org.fourz.rvnkcore.service.sightings.SightingService;
import org.fourz.rvnkcore.service.sightings.SightingStore;
import org.fourz.rvnkcore.util.log.LogManager;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/**
 * {@code GET /v1/net-sightings?after=<cursor>&limit=<n>} (#2351). z-office pulls this server's
 * sightings in id order and keeps the returned {@code next} as its cursor.
 *
 * <p>AuthFilter opens this path with the {@code X-Net-Sightings-Token} read token only: the shared
 * {@code X-API-Key} does not open it, and the IP allowlist does not apply.</p>
 */
public class NetSightingsController extends HttpServlet {

    static final int DEFAULT_LIMIT = 100;
    private static final long TIMEOUT_MS = 15_000L;

    private final Gson gson;
    private final LogManager logger;
    private final Supplier<SightingService> service;

    public NetSightingsController(Gson gson, LogManager logger) {
        this(gson, logger, () -> RVNKCore.getServiceSafe(SightingService.class));
    }

    NetSightingsController(Gson gson, LogManager logger, Supplier<SightingService> service) {
        this.gson = gson;
        this.logger = logger;
        this.service = service;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) {
        long after;
        int limit;
        try {
            after = parseCursor(req.getParameter("after"));
            String l = req.getParameter("limit");
            limit = l == null || l.isBlank() ? DEFAULT_LIMIT : Integer.parseInt(l.trim());
        } catch (NumberFormatException e) {
            ApiUtils.sendError(resp, gson, 400, "INVALID_CURSOR", "after must be a cursor from 'next'; limit a number");
            return;
        }
        SightingService svc = service.get();
        if (svc == null) {
            ApiUtils.sendError(resp, gson, 503, "UNAVAILABLE", "Sightings are not available on this server");
            return;
        }
        final long cursor = after;
        final String server = svc.config().server();
        java.util.concurrent.CompletableFuture<SightingStore.Page> page;
        try {
            page = svc.page(cursor, limit);
        } catch (RejectedExecutionException e) {
            ApiUtils.sendError(resp, gson, 503, "BUSY", "Too many sightings reads in flight; retry shortly");
            return;
        }

        AsyncContext ctx = req.startAsync();
        ctx.setTimeout(TIMEOUT_MS);
        AtomicBoolean written = new AtomicBoolean();
        ctx.addListener(new AsyncListener() {
            @Override public void onTimeout(AsyncEvent event) {
                if (!written.compareAndSet(false, true)) return;
                try {
                    ApiUtils.sendError((HttpServletResponse) ctx.getResponse(), gson, 504, "TIMEOUT", "Sightings read timed out");
                } finally {
                    ctx.complete();
                }
            }
            @Override public void onComplete(AsyncEvent event) { }
            @Override public void onError(AsyncEvent event) { written.set(true); }
            @Override public void onStartAsync(AsyncEvent event) { }
        });
        page.whenComplete((p, ex) -> {
            if (!written.compareAndSet(false, true)) return;
            HttpServletResponse out = (HttpServletResponse) ctx.getResponse();
            try {
                if (ex != null) {
                    logger.warning("[net-sightings] read failed: " + ex.getMessage());
                    ApiUtils.sendError(out, gson, 500, "INTERNAL_ERROR", "Sightings read failed");
                } else {
                    out.setStatus(200);
                    out.setContentType("application/json");
                    out.setCharacterEncoding("UTF-8");
                    try {
                        out.getWriter().write(json(gson, body(server, cursor, p)));
                    } catch (java.io.IOException e) {
                        logger.warning("[net-sightings] could not write the response: " + e.getMessage());
                    }
                }
            } finally {
                ctx.complete();
            }
        });
    }

    /**
     * The success envelope as JSON, with {@code "address": null} kept on a revoke row for an offline
     * player (#2375). The shared Gson drops null values, so the key would vanish; the tree has no
     * other nulls, so writing it with serializeNulls adds only these.
     */
    static String json(Gson gson, Map<String, Object> data) {
        JsonObject tree = gson.toJsonTree(ApiResponse.success(data)).getAsJsonObject();
        for (JsonElement row : tree.getAsJsonObject("data").getAsJsonArray("sightings")) {
            JsonObject o = row.getAsJsonObject();
            if (!o.has("address")) o.add("address", JsonNull.INSTANCE);
        }
        return gson.newBuilder().serializeNulls().create().toJson(tree);
    }

    /** {@code null}/blank/{@code 0} = from the start. Decimal digits only. */
    static long parseCursor(String raw) {
        if (raw == null || raw.isBlank()) return 0L;
        String s = raw.trim();
        if (!s.matches("[0-9]{1,18}")) throw new NumberFormatException("bad cursor");
        return Long.parseLong(s);
    }

    static Map<String, Object> body(String server, long after, SightingStore.Page p) {
        List<Map<String, Object>> rows = new ArrayList<>();
        long next = after;
        for (Sighting s : p.sightings()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", Long.toString(s.id()));
            row.put("uuid", s.uuid().toString());
            row.put("name", s.name());
            row.put("address", s.address());
            row.put("server", s.server());
            row.put("at", Instant.ofEpochMilli(s.at()).toString());
            row.put("reason", s.reason());
            row.put("lists", s.lists());
            // #2375: removed lists on a revoke row; [] on join and entitlement rows.
            row.put("revoked", s.revoked());
            rows.add(row);
            next = s.id();
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("server", server);
        data.put("sightings", rows);
        data.put("next", Long.toString(next));
        data.put("more", p.more());
        data.put("gap", SightingService.gap(after, p.oldestId()));
        return data;
    }
}
