package org.fourz.rvnkcore.api.controller;

import com.google.gson.Gson;
import jakarta.servlet.AsyncContext;
import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.fourz.rvnkcore.api.util.ApiUtils;
import org.fourz.rvnkcore.service.whitelist.WhitelistMembership;
import org.fourz.rvnkcore.service.whitelist.WhitelistMembership.AddResult;
import org.fourz.rvnkcore.service.whitelist.WhitelistMembership.CheckResult;
import org.fourz.rvnkcore.service.whitelist.WhitelistMembership.UnknownPlayerException;
import org.fourz.rvnkcore.util.log.LogManager;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

/**
 * REST controller for server whitelist management.
 * POST /v1/whitelist/add       — whitelist a player, optionally add a member group: {"ign": "...", "group": "resident"}
 * DELETE /v1/whitelist/{ign}   — remove a player from the whitelist
 * GET /v1/whitelist/{ign}      — check whether a player is whitelisted
 * Protected by AuthFilter (X-API-Key header). Called by fourzorg-api on application acceptance.
 *
 * <p>Every route resolves the name to a UUID first and acts on the UUID, so a renamed player reads
 * correctly (#2347). The group step adds a LuckPerms inheritance node by UUID and never removes one
 * (#2346). Responses complete from the future; no Jetty thread blocks on it (#1552).</p>
 */
public class WhitelistController extends HttpServlet {
    private static final long TIMEOUT_MS = 20_000;

    private final Gson gson;
    private final LogManager logger;
    private final WhitelistMembership membership;

    public WhitelistController(Gson gson, LogManager logger, WhitelistMembership membership) {
        this.gson = gson;
        this.logger = logger;
        this.membership = membership;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String ign = extractIgn(req.getPathInfo());
        if (ign == null) {
            ApiUtils.sendError(resp, gson, 400, "MISSING_IGN", "Path must be /v1/whitelist/{ign}");
            return;
        }
        // Unwrapped body, as before: {ign, whitelisted} plus the resolved uuid and current name.
        respond(req, resp, membership.check(ign), r -> checkBody(r, "whitelisted", r.whitelisted()), false);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        if (!"/add".equals(req.getPathInfo())) {
            ApiUtils.sendError(resp, gson, 404, "NOT_FOUND", "Unknown whitelist endpoint: " + req.getPathInfo());
            return;
        }
        Map<?, ?> body;
        try {
            body = gson.fromJson(req.getReader(), Map.class);
        } catch (Exception e) {
            ApiUtils.sendError(resp, gson, 400, "INVALID_JSON", "Request body must be valid JSON");
            return;
        }
        if (body == null || !body.containsKey("ign")) {
            ApiUtils.sendError(resp, gson, 400, "MISSING_FIELD", "Body must contain 'ign'");
            return;
        }
        String ign = body.get("ign").toString().trim();
        if (!WhitelistMembership.IGN.matcher(ign).matches()) {
            ApiUtils.sendError(resp, gson, 400, "INVALID_IGN",
                    "IGN must be 3-16 characters (letters, numbers, underscores)");
            return;
        }
        String group;
        try {
            group = WhitelistMembership.normaliseGroup(body.get("group")).orElse(null);
        } catch (IllegalArgumentException e) {
            ApiUtils.sendError(resp, gson, 400, "INVALID_GROUP", e.getMessage());
            return;
        }
        respond(req, resp, membership.add(ign, group), WhitelistController::addBody, true);
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String ign = extractIgn(req.getPathInfo());
        if (ign == null) {
            ApiUtils.sendError(resp, gson, 400, "MISSING_IGN", "Path must be /v1/whitelist/{ign}");
            return;
        }
        respond(req, resp, membership.remove(ign), r -> checkBody(r, "removed", true), false);
    }

    static Map<String, Object> addBody(AddResult r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ign", r.ign());
        m.put("uuid", r.uuid().toString());
        m.put("name", r.name());
        m.put("whitelisted", r.whitelisted());
        m.put("group", r.group());
        m.put("groupResult", r.groupResult() == null ? null : r.groupResult().name());
        m.put("ok", r.ok());
        return m;
    }

    private static Map<String, Object> checkBody(CheckResult r, String flag, boolean value) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ign", r.ign());
        m.put("uuid", r.uuid().toString());
        m.put("name", r.name());
        m.put(flag, value);
        return m;
    }

    /**
     * Writes the result of {@code future} when it completes. {@code wrap} puts the body in the
     * {@code ApiResponse} envelope (POST, as before); GET and DELETE keep their bare bodies.
     */
    private <T> void respond(HttpServletRequest req, HttpServletResponse resp, CompletableFuture<T> future,
                             Function<T, Map<String, Object>> body, boolean wrap) {
        if (!req.isAsyncSupported()) {
            try {
                write(resp, req, future.get(TIMEOUT_MS, TimeUnit.MILLISECONDS), null, body, wrap);
            } catch (Exception e) {
                write(resp, req, null, e, body, wrap);
            }
            return;
        }
        AsyncContext ctx = req.startAsync();
        ctx.setTimeout(TIMEOUT_MS);
        AtomicBoolean written = new AtomicBoolean();
        ctx.addListener(new AsyncListener() {
            @Override public void onTimeout(AsyncEvent event) {
                if (!written.compareAndSet(false, true)) return;
                try {
                    ApiUtils.sendError((HttpServletResponse) ctx.getResponse(), gson, 504, "TIMEOUT",
                            "Whitelist operation timed out");
                } finally {
                    ctx.complete();
                }
            }
            @Override public void onComplete(AsyncEvent event) { }
            @Override public void onError(AsyncEvent event) { written.set(true); }
            @Override public void onStartAsync(AsyncEvent event) { }
        });
        future.whenComplete((result, ex) -> {
            if (!written.compareAndSet(false, true)) return;
            try {
                write((HttpServletResponse) ctx.getResponse(), req, result, ex, body, wrap);
            } finally {
                ctx.complete();
            }
        });
    }

    private <T> void write(HttpServletResponse out, HttpServletRequest req, T result, Throwable ex,
                           Function<T, Map<String, Object>> body, boolean wrap) {
        if (ex != null) {
            Throwable cause = unwrap(ex);
            if (cause instanceof UnknownPlayerException) {
                ApiUtils.sendError(out, gson, 404, "PLAYER_NOT_FOUND", cause.getMessage());
            } else if (cause instanceof WhitelistMembership.LookupFailedException) {
                logger.warning("[whitelist] " + cause.getMessage() + ": " + cause.getCause());
                ApiUtils.sendError(out, gson, 502, "LOOKUP_FAILED", cause.getMessage());
            } else if (cause instanceof java.util.concurrent.TimeoutException) {
                ApiUtils.sendError(out, gson, 504, "TIMEOUT", "Whitelist operation timed out");
            } else {
                logger.error("[whitelist] " + req.getMethod() + " " + req.getPathInfo() + " failed", cause);
                ApiUtils.sendError(out, gson, 500, "INTERNAL_ERROR",
                        "Whitelist operation failed: " + cause.getClass().getSimpleName());
            }
            return;
        }
        Map<String, Object> data = body.apply(result);
        logger.info("[whitelist] " + req.getMethod() + " " + data);
        if (wrap) {
            ApiUtils.sendSuccess(out, gson, data);
        } else {
            ApiUtils.sendJson(out, gson, 200, data);
        }
    }

    private static Throwable unwrap(Throwable ex) {
        Throwable t = ex;
        while ((t instanceof CompletionException || t instanceof java.util.concurrent.ExecutionException)
                && t.getCause() != null) {
            t = t.getCause();
        }
        return t;
    }

    private String extractIgn(String pathInfo) {
        if (pathInfo == null || pathInfo.length() < 2) return null;
        String ign = pathInfo.startsWith("/") ? pathInfo.substring(1) : pathInfo;
        return WhitelistMembership.IGN.matcher(ign).matches() ? ign : null;
    }
}
