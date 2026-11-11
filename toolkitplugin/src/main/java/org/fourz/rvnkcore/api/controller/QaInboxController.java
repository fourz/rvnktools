package org.fourz.rvnkcore.api.controller;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.fourz.rvnkcore.RVNKCore;
import org.fourz.rvnkcore.api.util.ApiUtils;
import org.fourz.rvnkcore.service.qa.QaInbox;
import org.fourz.rvnkcore.service.qa.QaTapService;
import org.fourz.rvnkcore.util.log.LogManager;

import com.google.gson.Gson;

/**
 * Reads the QA message tap (#2359).
 *
 * <ul>
 *   <li>{@code GET /v1/qa/inbox/{player}?since=&limit=} — lines sent to a QA subject after the cursor,
 *       oldest first. {@code player} is a name (a subject seen since the last restart) or a UUID.
 *       Answer: {@code active, player, uuid, subject, cursor, more, lines[{seq, at, kind, text}]}.
 *       Send {@code cursor} back as {@code since}.</li>
 *   <li>{@code GET /v1/qa/status} — whether the tap runs, and why not.</li>
 * </ul>
 *
 * <p>Behind the {@code /v1/*} API-key filter like every other route. Answers from memory only, so it
 * never blocks a Jetty thread.</p>
 */
public class QaInboxController extends HttpServlet {

    static final int DEFAULT_LIMIT = 100;

    private final Gson gson;
    private final LogManager logger;

    public QaInboxController(Gson gson, LogManager logger) {
        this.gson = gson;
        this.logger = logger;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        try {
            QaTapService tap = RVNKCore.getServiceSafe(QaTapService.class);
            String path = req.getPathInfo() == null ? "" : req.getPathInfo();
            if (path.equals("/status")) {
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("active", tap != null && tap.isActive());
                data.put("reason", tap == null ? "QA tap not registered" : tap.getReason());
                data.put("readFailures", tap == null ? 0 : tap.getFailures());
                ApiUtils.sendSuccess(resp, gson, data);
                return;
            }
            if (!path.startsWith("/inbox/") || path.length() <= "/inbox/".length()) {
                ApiUtils.sendError(resp, gson, 404, "NOT_FOUND", "Unknown QA endpoint: GET " + path);
                return;
            }
            if (tap == null || !tap.isActive()) {
                ApiUtils.sendError(resp, gson, 503, "QA_INBOX_OFF",
                        "QA inbox is off: " + (tap == null ? "not registered" : tap.getReason()));
                return;
            }
            String who = path.substring("/inbox/".length());
            UUID uuid = tap.resolve(who);
            if (uuid == null) {
                ApiUtils.sendError(resp, gson, 404, "UNKNOWN_SUBJECT", "No QA subject named '" + who
                        + "' since the last restart. The player needs " + QaTapService.PERM_QA_SUBJECT
                        + " and must be online once.");
                return;
            }
            long since;
            int limit;
            try {
                String rawSince = req.getParameter("since");
                String rawLimit = req.getParameter("limit");
                since = rawSince == null || rawSince.isBlank() ? 0L : Long.parseLong(rawSince.trim());
                limit = rawLimit == null || rawLimit.isBlank() ? DEFAULT_LIMIT : Integer.parseInt(rawLimit.trim());
            } catch (NumberFormatException e) {
                ApiUtils.sendError(resp, gson, 400, "BAD_REQUEST", "since and limit must be integers");
                return;
            }

            QaInbox.Page page = tap.getInbox().since(uuid, since, limit);
            List<Map<String, Object>> lines = new ArrayList<>(page.lines().size());
            for (QaInbox.Line l : page.lines()) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("seq", l.seq());
                m.put("at", Instant.ofEpochMilli(l.at()).toString());
                m.put("kind", l.kind());
                m.put("text", l.text());
                lines.add(m);
            }
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("active", true);
            data.put("player", who);
            data.put("uuid", uuid.toString());
            data.put("subject", tap.isSubject(uuid));
            data.put("cursor", page.cursor());
            data.put("more", page.more());
            data.put("lines", lines);
            ApiUtils.sendSuccess(resp, gson, data);
        } catch (Exception e) {
            logger.error("Error handling QA inbox GET request", e);
            ApiUtils.sendError(resp, gson, 500, "INTERNAL_ERROR", "Request failed");
        }
    }
}
