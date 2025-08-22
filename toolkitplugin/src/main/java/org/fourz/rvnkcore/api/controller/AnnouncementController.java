package org.fourz.rvnkcore.api.controller;

import org.fourz.rvnkcore.api.model.AnnouncementDTO;
import org.fourz.rvnkcore.api.service.AnnouncementService;
import org.fourz.rvnktools.util.log.LogManager;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * REST API controller for announcement management.
 * 
 * Provides comprehensive REST endpoints for announcement CRUD operations,
 * search functionality, and administrative management.
 * 
 * @since 1.0.0
 */
public class AnnouncementController extends HttpServlet {
    
    private final AnnouncementService announcementService;
    private final LogManager logger;
    
    /**
     * Constructor for AnnouncementController.
     * 
     * @param announcementService The announcement service for business logic
     * @param logger The logger instance
     */
    public AnnouncementController(AnnouncementService announcementService, LogManager logger) {
        this.announcementService = announcementService;
        this.logger = logger;
    }
    
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String pathInfo = request.getPathInfo();
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        
        try {
            if (pathInfo == null || pathInfo.equals("/")) {
                // GET /api/v1/announcements - List all announcements
                handleGetAllAnnouncements(request, response);
            } else if (pathInfo.equals("/active")) {
                // GET /api/v1/announcements/active - Get active announcements
                handleGetActiveAnnouncements(request, response);
            } else if (pathInfo.equals("/count")) {
                // GET /api/v1/announcements/count - Get total count
                handleGetAnnouncementCount(request, response);
            } else if (pathInfo.equals("/count/active")) {
                // GET /api/v1/announcements/count/active - Get active count
                handleGetActiveAnnouncementCount(request, response);
            } else if (pathInfo.startsWith("/type/")) {
                // GET /api/v1/announcements/type/{type} - Get announcements by type
                String type = pathInfo.substring(6); // Remove "/type/"
                handleGetAnnouncementsByType(type, request, response);
            } else if (pathInfo.startsWith("/world/")) {
                // GET /api/v1/announcements/world/{world} - Get announcements for world
                String world = pathInfo.substring(7); // Remove "/world/"
                handleGetAnnouncementsForWorld(world, request, response);
            } else if (pathInfo.startsWith("/group/")) {
                // GET /api/v1/announcements/group/{group} - Get announcements for group
                String group = pathInfo.substring(7); // Remove "/group/"
                handleGetAnnouncementsForGroup(group, request, response);
            } else if (pathInfo.equals("/search")) {
                // GET /api/v1/announcements/search?q=pattern - Search announcements
                String query = request.getParameter("q");
                if (query != null) {
                    handleSearchAnnouncements(query, request, response);
                } else {
                    sendErrorResponse(response, 400, "Missing required parameter: q");
                }
            } else if (pathInfo.startsWith("/")) {
                // GET /api/v1/announcements/{id} - Get announcement by ID
                String id = pathInfo.substring(1);
                handleGetAnnouncementById(id, request, response);
            } else {
                sendErrorResponse(response, 404, "Endpoint not found");
            }
        } catch (Exception e) {
            logger.error("Error handling GET request: " + pathInfo, e);
            sendErrorResponse(response, 500, "Internal server error: " + e.getMessage());
        }
    }
    
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String pathInfo = request.getPathInfo();
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        
        try {
            if (pathInfo == null || pathInfo.equals("/")) {
                // POST /api/v1/announcements - Create new announcement
                handleCreateAnnouncement(request, response);
            } else if (pathInfo.equals("/bulk-import")) {
                // POST /api/v1/announcements/bulk-import - Bulk import announcements
                handleBulkImportAnnouncements(request, response);
            } else {
                sendErrorResponse(response, 404, "Endpoint not found");
            }
        } catch (Exception e) {
            logger.error("Error handling POST request: " + pathInfo, e);
            sendErrorResponse(response, 500, "Internal server error: " + e.getMessage());
        }
    }
    
    @Override
    protected void doPut(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String pathInfo = request.getPathInfo();
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        
        try {
            if (pathInfo != null && pathInfo.startsWith("/")) {
                String[] parts = pathInfo.substring(1).split("/");
                if (parts.length == 1) {
                    // PUT /api/v1/announcements/{id} - Update announcement
                    String id = parts[0];
                    handleUpdateAnnouncement(id, request, response);
                } else if (parts.length == 2 && parts[1].equals("activate")) {
                    // PUT /api/v1/announcements/{id}/activate - Activate announcement
                    String id = parts[0];
                    handleActivateAnnouncement(id, request, response);
                } else if (parts.length == 2 && parts[1].equals("deactivate")) {
                    // PUT /api/v1/announcements/{id}/deactivate - Deactivate announcement
                    String id = parts[0];
                    handleDeactivateAnnouncement(id, request, response);
                } else {
                    sendErrorResponse(response, 404, "Endpoint not found");
                }
            } else {
                sendErrorResponse(response, 404, "Endpoint not found");
            }
        } catch (Exception e) {
            logger.error("Error handling PUT request: " + pathInfo, e);
            sendErrorResponse(response, 500, "Internal server error: " + e.getMessage());
        }
    }
    
    @Override
    protected void doDelete(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String pathInfo = request.getPathInfo();
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        
        try {
            if (pathInfo != null && pathInfo.startsWith("/")) {
                String id = pathInfo.substring(1);
                // DELETE /api/v1/announcements/{id} - Delete announcement
                handleDeleteAnnouncement(id, request, response);
            } else {
                sendErrorResponse(response, 404, "Endpoint not found");
            }
        } catch (Exception e) {
            logger.error("Error handling DELETE request: " + pathInfo, e);
            sendErrorResponse(response, 500, "Internal server error: " + e.getMessage());
        }
    }
    
    /**
     * Handles GET /api/v1/announcements - List all announcements
     */
    private void handleGetAllAnnouncements(HttpServletRequest request, HttpServletResponse response) throws IOException {
        CompletableFuture<List<AnnouncementDTO>> future = announcementService.getAllAnnouncements();
        
        future.thenAccept(announcements -> {
            try {
                String json = buildAnnouncementListResponse(announcements);
                sendSuccessResponse(response, json);
            } catch (IOException e) {
                logger.error("Error sending announcement list response", e);
            }
        }).exceptionally(ex -> {
            try {
                sendErrorResponse(response, 500, "Failed to retrieve announcements: " + ex.getMessage());
            } catch (IOException e) {
                logger.error("Error sending error response", e);
            }
            return null;
        });
    }
    
    /**
     * Handles GET /api/v1/announcements/active - Get active announcements
     */
    private void handleGetActiveAnnouncements(HttpServletRequest request, HttpServletResponse response) throws IOException {
        CompletableFuture<List<AnnouncementDTO>> future = announcementService.getActiveAnnouncements();
        
        future.thenAccept(announcements -> {
            try {
                String json = buildAnnouncementListResponse(announcements);
                sendSuccessResponse(response, json);
            } catch (IOException e) {
                logger.error("Error sending active announcements response", e);
            }
        }).exceptionally(ex -> {
            try {
                sendErrorResponse(response, 500, "Failed to retrieve active announcements: " + ex.getMessage());
            } catch (IOException e) {
                logger.error("Error sending error response", e);
            }
            return null;
        });
    }
    
    /**
     * Handles GET /api/v1/announcements/{id} - Get announcement by ID
     */
    private void handleGetAnnouncementById(String id, HttpServletRequest request, HttpServletResponse response) throws IOException {
        CompletableFuture<Optional<AnnouncementDTO>> future = announcementService.getAnnouncement(id);
        
        future.thenAccept(optional -> {
            try {
                if (optional.isPresent()) {
                    String json = buildAnnouncementResponse(optional.get());
                    sendSuccessResponse(response, json);
                } else {
                    sendErrorResponse(response, 404, "Announcement not found: " + id);
                }
            } catch (IOException e) {
                logger.error("Error sending announcement response", e);
            }
        }).exceptionally(ex -> {
            try {
                sendErrorResponse(response, 500, "Failed to retrieve announcement: " + ex.getMessage());
            } catch (IOException e) {
                logger.error("Error sending error response", e);
            }
            return null;
        });
    }
    
    /**
     * Handles GET /api/v1/announcements/count - Get total count
     */
    private void handleGetAnnouncementCount(HttpServletRequest request, HttpServletResponse response) throws IOException {
        CompletableFuture<Long> future = announcementService.getAnnouncementCount();
        
        future.thenAccept(count -> {
            try {
                String json = String.format("{\"count\": %d}", count);
                sendSuccessResponse(response, json);
            } catch (IOException e) {
                logger.error("Error sending count response", e);
            }
        }).exceptionally(ex -> {
            try {
                sendErrorResponse(response, 500, "Failed to get announcement count: " + ex.getMessage());
            } catch (IOException e) {
                logger.error("Error sending error response", e);
            }
            return null;
        });
    }
    
    /**
     * Handles GET /api/v1/announcements/count/active - Get active count
     */
    private void handleGetActiveAnnouncementCount(HttpServletRequest request, HttpServletResponse response) throws IOException {
        CompletableFuture<Long> future = announcementService.getActiveAnnouncementCount();
        
        future.thenAccept(count -> {
            try {
                String json = String.format("{\"count\": %d}", count);
                sendSuccessResponse(response, json);
            } catch (IOException e) {
                logger.error("Error sending active count response", e);
            }
        }).exceptionally(ex -> {
            try {
                sendErrorResponse(response, 500, "Failed to get active announcement count: " + ex.getMessage());
            } catch (IOException e) {
                logger.error("Error sending error response", e);
            }
            return null;
        });
    }
    
    /**
     * Handles GET /api/v1/announcements/type/{type} - Get announcements by type
     */
    private void handleGetAnnouncementsByType(String type, HttpServletRequest request, HttpServletResponse response) throws IOException {
        CompletableFuture<List<AnnouncementDTO>> future = announcementService.getAnnouncementsByType(type);
        
        future.thenAccept(announcements -> {
            try {
                String json = buildAnnouncementListResponse(announcements);
                sendSuccessResponse(response, json);
            } catch (IOException e) {
                logger.error("Error sending announcements by type response", e);
            }
        }).exceptionally(ex -> {
            try {
                sendErrorResponse(response, 500, "Failed to retrieve announcements by type: " + ex.getMessage());
            } catch (IOException e) {
                logger.error("Error sending error response", e);
            }
            return null;
        });
    }
    
    /**
     * Handles search announcements
     */
    private void handleSearchAnnouncements(String query, HttpServletRequest request, HttpServletResponse response) throws IOException {
        CompletableFuture<List<AnnouncementDTO>> future = announcementService.searchAnnouncements(query);
        
        future.thenAccept(announcements -> {
            try {
                String json = buildAnnouncementListResponse(announcements);
                sendSuccessResponse(response, json);
            } catch (IOException e) {
                logger.error("Error sending search announcements response", e);
            }
        }).exceptionally(ex -> {
            try {
                sendErrorResponse(response, 500, "Failed to search announcements: " + ex.getMessage());
            } catch (IOException e) {
                logger.error("Error sending error response", e);
            }
            return null;
        });
    }
    
    /**
     * Handles GET /api/v1/announcements/world/{world} - Get announcements for world
     */
    private void handleGetAnnouncementsForWorld(String world, HttpServletRequest request, HttpServletResponse response) throws IOException {
        CompletableFuture<List<AnnouncementDTO>> future = announcementService.getAnnouncementsForWorld(world);
        
        future.thenAccept(announcements -> {
            try {
                String json = buildAnnouncementListResponse(announcements);
                sendSuccessResponse(response, json);
            } catch (IOException e) {
                logger.error("Error sending announcements for world response", e);
            }
        }).exceptionally(ex -> {
            try {
                sendErrorResponse(response, 500, "Failed to retrieve announcements for world: " + ex.getMessage());
            } catch (IOException e) {
                logger.error("Error sending error response", e);
            }
            return null;
        });
    }
    
    /**
     * Handles GET /api/v1/announcements/group/{group} - Get announcements for group
     */
    private void handleGetAnnouncementsForGroup(String group, HttpServletRequest request, HttpServletResponse response) throws IOException {
        CompletableFuture<List<AnnouncementDTO>> future = announcementService.getAnnouncementsForGroup(group);
        
        future.thenAccept(announcements -> {
            try {
                String json = buildAnnouncementListResponse(announcements);
                sendSuccessResponse(response, json);
            } catch (IOException e) {
                logger.error("Error sending announcements for group response", e);
            }
        }).exceptionally(ex -> {
            try {
                sendErrorResponse(response, 500, "Failed to retrieve announcements for group: " + ex.getMessage());
            } catch (IOException e) {
                logger.error("Error sending error response", e);
            }
            return null;
        });
    }
    
    /**
     * Handles POST /api/v1/announcements - Create new announcement
     */
    private void handleCreateAnnouncement(HttpServletRequest request, HttpServletResponse response) throws IOException {
        // This is a simplified implementation. In production, you'd parse JSON from request body
        String title = request.getParameter("title");
        String message = request.getParameter("message");
        String type = request.getParameter("type");
        
        if (message == null || type == null) {
            sendErrorResponse(response, 400, "Missing required parameters: message, type");
            return;
        }
        
        AnnouncementDTO announcement = new AnnouncementDTO.Builder()
            .title(title)
            .message(message)
            .type(type)
            .active(true)
            .build();
        
        CompletableFuture<AnnouncementDTO> future = announcementService.createAnnouncement(announcement);
        
        future.thenAccept(created -> {
            try {
                response.setStatus(201); // Created
                String json = buildAnnouncementResponse(created);
                sendSuccessResponse(response, json);
            } catch (IOException e) {
                logger.error("Error sending create announcement response", e);
            }
        }).exceptionally(ex -> {
            try {
                sendErrorResponse(response, 500, "Failed to create announcement: " + ex.getMessage());
            } catch (IOException e) {
                logger.error("Error sending error response", e);
            }
            return null;
        });
    }
    
    /**
     * Handles DELETE /api/v1/announcements/{id} - Delete announcement
     */
    private void handleDeleteAnnouncement(String id, HttpServletRequest request, HttpServletResponse response) throws IOException {
        CompletableFuture<Void> future = announcementService.deleteAnnouncement(id);
        
        future.thenRun(() -> {
            try {
                response.setStatus(204); // No Content
                response.getWriter().flush();
            } catch (IOException e) {
                logger.error("Error sending delete response", e);
            }
        }).exceptionally(ex -> {
            try {
                sendErrorResponse(response, 500, "Failed to delete announcement: " + ex.getMessage());
            } catch (IOException e) {
                logger.error("Error sending error response", e);
            }
            return null;
        });
    }
    
    /**
     * Handles PUT /api/v1/announcements/{id}/activate - Activate announcement
     */
    private void handleActivateAnnouncement(String id, HttpServletRequest request, HttpServletResponse response) throws IOException {
        CompletableFuture<Void> future = announcementService.activateAnnouncement(id);
        
        future.thenRun(() -> {
            try {
                String json = String.format("{\"status\": \"success\", \"message\": \"Announcement %s activated\"}", id);
                sendSuccessResponse(response, json);
            } catch (IOException e) {
                logger.error("Error sending activate response", e);
            }
        }).exceptionally(ex -> {
            try {
                sendErrorResponse(response, 500, "Failed to activate announcement: " + ex.getMessage());
            } catch (IOException e) {
                logger.error("Error sending error response", e);
            }
            return null;
        });
    }
    
    /**
     * Handles PUT /api/v1/announcements/{id}/deactivate - Deactivate announcement
     */
    private void handleDeactivateAnnouncement(String id, HttpServletRequest request, HttpServletResponse response) throws IOException {
        CompletableFuture<Void> future = announcementService.deactivateAnnouncement(id);
        
        future.thenRun(() -> {
            try {
                String json = String.format("{\"status\": \"success\", \"message\": \"Announcement %s deactivated\"}", id);
                sendSuccessResponse(response, json);
            } catch (IOException e) {
                logger.error("Error sending deactivate response", e);
            }
        }).exceptionally(ex -> {
            try {
                sendErrorResponse(response, 500, "Failed to deactivate announcement: " + ex.getMessage());
            } catch (IOException e) {
                logger.error("Error sending error response", e);
            }
            return null;
        });
    }
    
    /**
     * Handles PUT /api/v1/announcements/{id} - Update announcement (stub)
     */
    private void handleUpdateAnnouncement(String id, HttpServletRequest request, HttpServletResponse response) throws IOException {
        // This is a stub implementation. In production, you'd parse JSON from request body
        sendErrorResponse(response, 501, "Update announcement endpoint not yet implemented");
    }
    
    /**
     * Handles POST /api/v1/announcements/bulk-import - Bulk import announcements (stub)
     */
    private void handleBulkImportAnnouncements(HttpServletRequest request, HttpServletResponse response) throws IOException {
        // This is a stub implementation. In production, you'd parse JSON array from request body
        sendErrorResponse(response, 501, "Bulk import endpoint not yet implemented");
    }
    
    /**
     * Builds a JSON response for a single announcement.
     */
    private String buildAnnouncementResponse(AnnouncementDTO announcement) {
        StringBuilder json = new StringBuilder();
        json.append("{");
        json.append("\"id\":\"").append(escapeJson(announcement.getId())).append("\",");
        json.append("\"title\":\"").append(escapeJson(announcement.getTitle())).append("\",");
        json.append("\"message\":\"").append(escapeJson(announcement.getMessage())).append("\",");
        json.append("\"type\":\"").append(escapeJson(announcement.getType())).append("\",");
        json.append("\"active\":").append(announcement.isActive()).append(",");
        json.append("\"created_at\":\"").append(announcement.getCreatedAt()).append("\",");
        json.append("\"updated_at\":\"").append(announcement.getUpdatedAt()).append("\",");
        json.append("\"interval_seconds\":").append(announcement.getIntervalSeconds());
        json.append("}");
        return json.toString();
    }
    
    /**
     * Builds a JSON response for a list of announcements.
     */
    private String buildAnnouncementListResponse(List<AnnouncementDTO> announcements) {
        StringBuilder json = new StringBuilder();
        json.append("{\"announcements\":[");
        
        for (int i = 0; i < announcements.size(); i++) {
            if (i > 0) json.append(",");
            json.append(buildAnnouncementResponse(announcements.get(i)));
        }
        
        json.append("],\"count\":").append(announcements.size()).append("}");
        return json.toString();
    }
    
    /**
     * Sends a successful JSON response.
     */
    private void sendSuccessResponse(HttpServletResponse response, String json) throws IOException {
        response.setStatus(200);
        PrintWriter writer = response.getWriter();
        writer.write(json);
        writer.flush();
    }
    
    /**
     * Sends an error response with the specified status code and message.
     */
    private void sendErrorResponse(HttpServletResponse response, int statusCode, String message) throws IOException {
        response.setStatus(statusCode);
        PrintWriter writer = response.getWriter();
        String json = String.format("{\"error\":\"%s\"}", escapeJson(message));
        writer.write(json);
        writer.flush();
    }
    
    /**
     * Escapes JSON special characters in a string.
     */
    private String escapeJson(String input) {
        if (input == null) return "";
        return input.replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r")
                    .replace("\t", "\\t");
    }
}
