package org.fourz.rvnktools.api.server.jetty;

import com.google.gson.Gson;
import org.fourz.rvnktools.announceManager.AnnounceManager;
import org.fourz.rvnktools.announceManager.Announcement;
import org.fourz.rvnktools.api.model.CreateAnnouncementRequest;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.List;

public class JettyServerAnnouncement extends HttpServlet {
    private final AnnounceManager announceManager;
    private final Gson gson;

    public JettyServerAnnouncement(AnnounceManager announceManager, Gson gson) {
        this.announceManager = announceManager;
        this.gson = gson;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String pathInfo = req.getPathInfo();
        resp.setContentType("application/json");

        if (pathInfo == null || pathInfo.equals("/")) {
            // Return list of all announcements
            List<Announcement> announcements = announceManager.getAnnouncements();
            resp.setStatus(HttpServletResponse.SC_OK);
            resp.getWriter().println(gson.toJson(announcements));
            return;
        }

        // Handle single announcement request
        String id = pathInfo.substring(1); // Remove leading slash
        Announcement announcement = announceManager.getAnnouncement(id);

        if (announcement == null) {
            resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
            resp.getWriter().println("{\"status\":\"error\",\"message\":\"Announcement not found\"}");
            return;
        }

        resp.setStatus(HttpServletResponse.SC_OK);
        resp.getWriter().println(gson.toJson(announcement));
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String pathInfo = req.getPathInfo();
        resp.setContentType("application/json");

        if (pathInfo == null || pathInfo.equals("/")) {
            BufferedReader reader = req.getReader();
            CreateAnnouncementRequest request = gson.fromJson(reader, CreateAnnouncementRequest.class);
            
            if (!request.isValid()) {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                resp.getWriter().println("{\"status\":\"error\",\"message\":\"Missing required fields: id, type, and message\"}");
                return;
            }

            // Validate announcement type
            if (!announceManager.validateAnnounceType(request.getType())) {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                resp.getWriter().println("{\"status\":\"error\",\"message\":\"Invalid announcement type\"}");
                return;
            }

            Announcement announcement = new Announcement();
            announcement.setId(request.getId());
            announcement.setType(request.getType());
            announcement.setMessage(request.getMessage());
            
            // Set optional fields if present
            if (request.getPermission() != null) {
                announcement.setPermission(request.getPermission());
            }
            if (request.getRecurrence() != null) {
                announcement.setRecurrence(request.getRecurrence());
            }
            if (request.getRecurrenceString() != null) {
                announcement.setRecurrenceString(request.getRecurrenceString());
            }
            if (request.getDate() != null) {
                announcement.setDate(request.getDate().toLocalDate());
            }
            if (request.getTime() != null) {
                announcement.setTime(request.getTime());
            }
            if (request.getExpiration() != null) {
                announcement.setExpiration(request.getExpiration());
            }
            if (request.getOwner() != null) {
                announcement.setOwner(request.getOwner());
            }
            
            boolean success = announceManager.addAnnouncement(announcement);
            
            if (success) {
                resp.setStatus(HttpServletResponse.SC_CREATED);
                resp.getWriter().println(gson.toJson(announcement));
            } else {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                resp.getWriter().println("{\"status\":\"error\",\"message\":\"Failed to create announcement\"}");
            }
        }
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String pathInfo = req.getPathInfo();
        if (pathInfo != null && pathInfo.length() > 1) {
            String id = pathInfo.substring(1); // Remove leading slash
            boolean success = announceManager.deleteAnnouncement(id);
            
            resp.setContentType("application/json");
            if (success) {
                resp.setStatus(HttpServletResponse.SC_OK);
                resp.getWriter().println("{\"status\":\"success\",\"message\":\"Announcement deleted\"}");
            } else {
                resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
                resp.getWriter().println("{\"status\":\"error\",\"message\":\"Announcement not found\"}");
            }
        }
    }
}
