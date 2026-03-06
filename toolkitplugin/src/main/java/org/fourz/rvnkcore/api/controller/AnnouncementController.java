package org.fourz.rvnkcore.api.controller;

import org.fourz.rvnkcore.api.model.AnnouncementDTO;
import org.fourz.rvnkcore.api.model.response.ApiResponse;
import org.fourz.rvnkcore.api.service.AnnouncementService;
import org.fourz.rvnkcore.util.log.LogManager;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

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
    private final Gson gson;

    /**
     * Constructor for AnnouncementController.
     *
     * @param announcementService The announcement service for business logic
     * @param logger The logger instance
     * @param gson JSON serializer instance
     */
    public AnnouncementController(AnnouncementService announcementService, LogManager logger, Gson gson) {
        this.announcementService = announcementService;
        this.logger = logger;
        this.gson = gson;
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
            } else if (pathInfo.equals("/metrics")) {
                // GET /api/v1/announcements/metrics - Get announcement system metrics
                handleGetAnnouncementMetrics(request, response);
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
            } else if (pathInfo.equals("/search")) {
                // POST /api/v1/announcements/search - Search announcements with JSON body
                handleSearchAnnouncementsPost(request, response);
            } else if (pathInfo.equals("/bulk")) {
                // POST /api/v1/announcements/bulk - Bulk create announcements
                handleBulkCreateAnnouncements(request, response);
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
                } else if (parts.length == 2 && parts[0].equals("bulk") && parts[1].equals("activate")) {
                    // PUT /api/v1/announcements/bulk/activate - Bulk activate announcements
                    handleBulkActivateAnnouncements(request, response);
                } else if (parts.length == 2 && parts[0].equals("bulk") && parts[1].equals("deactivate")) {
                    // PUT /api/v1/announcements/bulk/deactivate - Bulk deactivate announcements
                    handleBulkDeactivateAnnouncements(request, response);
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
        try {
            List<AnnouncementDTO> announcements = announcementService.getAllAnnouncements().get(15, TimeUnit.SECONDS);
            String json = buildAnnouncementListResponse(announcements);
            sendSuccessResponse(response, json);
        } catch (Exception e) {
            logger.error("Error retrieving all announcements", e);
            sendErrorResponse(response, 500, "Internal server error: " + e.getMessage());
        }
    }
    
    /**
     * Handles GET /api/v1/announcements/active - Get active announcements
     */
    private void handleGetActiveAnnouncements(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            List<AnnouncementDTO> announcements = announcementService.getActiveAnnouncements().get(15, TimeUnit.SECONDS);
            String json = buildAnnouncementListResponse(announcements);
            sendSuccessResponse(response, json);
        } catch (Exception e) {
            logger.error("Error retrieving active announcements", e);
            sendErrorResponse(response, 500, "Failed to retrieve active announcements: " + e.getMessage());
        }
    }
    
    /**
     * Handles GET /api/v1/announcements/{id} - Get announcement by ID
     */
    private void handleGetAnnouncementById(String id, HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            CompletableFuture<Optional<AnnouncementDTO>> future = announcementService.getAnnouncement(id);
            Optional<AnnouncementDTO> optional = future.get(); // Wait for completion synchronously
            
            if (optional.isPresent()) {
                String json = buildAnnouncementResponse(optional.get());
                sendSuccessResponse(response, json);
            } else {
                sendErrorResponse(response, 404, "Announcement not found: " + id);
            }
        } catch (Exception e) {
            logger.error("Error retrieving announcement: " + id, e);
            sendErrorResponse(response, 500, "Failed to retrieve announcement: " + e.getMessage());
        }
    }
    
    /**
     * Handles GET /api/v1/announcements/count - Get total count
     */
    private void handleGetAnnouncementCount(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            long count = announcementService.getAnnouncementCount().get(15, TimeUnit.SECONDS);
            String json = String.format("{\"count\": %d}", count);
            sendSuccessResponse(response, json);
        } catch (Exception e) {
            logger.error("Error getting announcement count", e);
            sendErrorResponse(response, 500, "Failed to get announcement count: " + e.getMessage());
        }
    }
    
    /**
     * Handles GET /api/v1/announcements/count/active - Get active count
     */
    private void handleGetActiveAnnouncementCount(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            long count = announcementService.getActiveAnnouncementCount().get(15, TimeUnit.SECONDS);
            String json = String.format("{\"count\": %d}", count);
            sendSuccessResponse(response, json);
        } catch (Exception e) {
            logger.error("Error getting active announcement count", e);
            sendErrorResponse(response, 500, "Failed to get active announcement count: " + e.getMessage());
        }
    }
    
    /**
     * Handles GET /api/v1/announcements/type/{type} - Get announcements by type
     */
    private void handleGetAnnouncementsByType(String type, HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            List<AnnouncementDTO> announcements = announcementService.getAnnouncementsByType(type).get(15, TimeUnit.SECONDS);
            String json = buildAnnouncementListResponse(announcements);
            sendSuccessResponse(response, json);
        } catch (Exception e) {
            logger.error("Error retrieving announcements by type: " + type, e);
            sendErrorResponse(response, 500, "Failed to retrieve announcements by type: " + e.getMessage());
        }
    }
    
    /**
     * Handles search announcements
     */
    private void handleSearchAnnouncements(String query, HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            List<AnnouncementDTO> announcements = announcementService.searchAnnouncements(query).get(15, TimeUnit.SECONDS);
            String json = buildAnnouncementListResponse(announcements);
            sendSuccessResponse(response, json);
        } catch (Exception e) {
            logger.error("Error searching announcements: " + query, e);
            sendErrorResponse(response, 500, "Failed to search announcements: " + e.getMessage());
        }
    }
    
    /**
     * Handles POST /api/v1/announcements/search - Search announcements with JSON body
     */
    private void handleSearchAnnouncementsPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            // Read JSON from request body
            StringBuilder buffer = new StringBuilder();
            String line;
            while ((line = request.getReader().readLine()) != null) {
                buffer.append(line);
            }
            
            String jsonBody = buffer.toString();
            if (jsonBody.isEmpty()) {
                sendErrorResponse(response, 400, "Request body is empty");
                return;
            }
            
            // Parse JSON to extract query
            JsonObject json = JsonParser.parseString(jsonBody).getAsJsonObject();
            String query = json.has("query") ? json.get("query").getAsString() : "test";
            
            // Delegate to existing search logic
            handleSearchAnnouncements(query, request, response);
            
        } catch (Exception e) {
            logger.error("Error parsing JSON in search announcement request", e);
            sendErrorResponse(response, 400, "Invalid JSON format: " + e.getMessage());
        }
    }
    
    /**
     * Handles GET /api/v1/announcements/world/{world} - Get announcements for world
     */
    private void handleGetAnnouncementsForWorld(String world, HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            List<AnnouncementDTO> announcements = announcementService.getAnnouncementsForWorld(world).get(15, TimeUnit.SECONDS);
            String json = buildAnnouncementListResponse(announcements);
            sendSuccessResponse(response, json);
        } catch (Exception e) {
            logger.error("Error retrieving announcements for world: " + world, e);
            sendErrorResponse(response, 500, "Failed to retrieve announcements for world: " + e.getMessage());
        }
    }
    
    /**
     * Handles GET /api/v1/announcements/group/{group} - Get announcements for group
     */
    private void handleGetAnnouncementsForGroup(String group, HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            List<AnnouncementDTO> announcements = announcementService.getAnnouncementsForGroup(group).get(15, TimeUnit.SECONDS);
            String json = buildAnnouncementListResponse(announcements);
            sendSuccessResponse(response, json);
        } catch (Exception e) {
            logger.error("Error retrieving announcements for group: " + group, e);
            sendErrorResponse(response, 500, "Failed to retrieve announcements for group: " + e.getMessage());
        }
    }
    
    /**
     * Handles POST /api/v1/announcements - Create new announcement
     */
    private void handleCreateAnnouncement(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            // Read JSON from request body
            StringBuilder buffer = new StringBuilder();
            String line;
            while ((line = request.getReader().readLine()) != null) {
                buffer.append(line);
            }
            
            String jsonBody = buffer.toString();
            if (jsonBody.isEmpty()) {
                sendErrorResponse(response, 400, "Request body is empty");
                return;
            }
            
            // Parse JSON using modern approach
            JsonObject json = JsonParser.parseString(jsonBody).getAsJsonObject();
            
            // Map test data fields to expected DTO fields
            String title = json.has("title") ? json.get("title").getAsString() : "Test Announcement";
            String message = json.has("content") ? json.get("content").getAsString() : 
                           json.has("message") ? json.get("message").getAsString() : null;
            String type = json.has("type") ? json.get("type").getAsString() : null;
            boolean active = json.has("isActive") ? json.get("isActive").getAsBoolean() : 
                           json.has("active") ? json.get("active").getAsBoolean() : true;
            
            if (message == null || type == null) {
                sendErrorResponse(response, 400, "Missing required parameters: message/content and type");
                return;
            }
            
            AnnouncementDTO announcement = new AnnouncementDTO.Builder()
                .title(title)
                .message(message)
                .type(type)
                .active(active)
                .build();
            
            // Perform create synchronously for the HTTP request so the client reliably receives the
            // created resource (ID and 201 status). Blocking here is acceptable for short CRUD ops.
            try {
                AnnouncementDTO created = announcementService.createAnnouncement(announcement).join();
                response.setStatus(201); // Created
                String jsonResponse = buildAnnouncementResponse(created);
                sendSuccessResponse(response, jsonResponse);
            } catch (Exception ex) {
                logger.error("Failed to create announcement", ex);
                sendErrorResponse(response, 500, "Failed to create announcement: " + ex.getMessage());
            }
        } catch (Exception e) {
            logger.error("Error parsing JSON in create announcement request", e);
            sendErrorResponse(response, 400, "Invalid JSON format: " + e.getMessage());
        }
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
     * Handles PUT /api/v1/announcements/{id} - Update announcement
     */
    private void handleUpdateAnnouncement(String id, HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            // Parse JSON request body
            StringBuilder jsonBody = new StringBuilder();
            String line;
            java.io.BufferedReader reader = request.getReader();
            while ((line = reader.readLine()) != null) {
                jsonBody.append(line);
            }
            
            // Basic JSON parsing (a real implementation would use Jackson or similar)
            String json = jsonBody.toString();
            if (json == null || json.trim().isEmpty()) {
                sendErrorResponse(response, 400, "Request body is required");
                return;
            }
            
            // Create AnnouncementDTO from JSON - simplified parsing
            AnnouncementDTO announcement = parseAnnouncementFromJson(json, id);
            
            CompletableFuture<AnnouncementDTO> future = announcementService.updateAnnouncement(announcement);
            future.whenComplete((updatedAnnouncement, throwable) -> {
                try {
                    if (throwable != null) {
                        logger.error("Error updating announcement: " + id, throwable);
                        if (throwable.getCause() instanceof IllegalArgumentException) {
                            sendErrorResponse(response, 400, "Invalid announcement data: " + throwable.getMessage());
                        } else {
                            sendErrorResponse(response, 500, "Failed to update announcement: " + throwable.getMessage());
                        }
                    } else {
                        response.setStatus(200);
                        response.getWriter().write(buildAnnouncementResponse(updatedAnnouncement));
                    }
                } catch (IOException e) {
                    logger.error("Error writing update response", e);
                }
            });
            
        } catch (Exception e) {
            logger.error("Error handling update announcement request for ID: " + id, e);
            sendErrorResponse(response, 500, "Internal server error: " + e.getMessage());
        }
    }
    
    /**
     * Handles POST /api/v1/announcements/bulk-import - Bulk import announcements
     */
    private void handleBulkImportAnnouncements(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            // Parse JSON request body
            StringBuilder jsonBody = new StringBuilder();
            String line;
            java.io.BufferedReader reader = request.getReader();
            while ((line = reader.readLine()) != null) {
                jsonBody.append(line);
            }
            
            String json = jsonBody.toString();
            if (json == null || json.trim().isEmpty()) {
                sendErrorResponse(response, 400, "Request body is required");
                return;
            }
            
            // Parse JSON array to List<AnnouncementDTO>
            List<AnnouncementDTO> announcements = parseAnnouncementListFromJson(json);
            
            CompletableFuture<Integer> future = announcementService.bulkImportAnnouncements(announcements);
            future.whenComplete((importedCount, throwable) -> {
                try {
                    if (throwable != null) {
                        logger.error("Error bulk importing announcements", throwable);
                        if (throwable.getCause() instanceof IllegalArgumentException) {
                            sendErrorResponse(response, 400, "Invalid announcement data: " + throwable.getMessage());
                        } else {
                            sendErrorResponse(response, 500, "Failed to import announcements: " + throwable.getMessage());
                        }
                    } else {
                        response.setStatus(201);
                        String result = "{\"imported_count\":" + importedCount + ",\"message\":\"Successfully imported " + importedCount + " announcements\"}";
                        response.getWriter().write(result);
                    }
                } catch (IOException e) {
                    logger.error("Error writing bulk import response", e);
                }
            });
            
        } catch (Exception e) {
            logger.error("Error handling bulk import request", e);
            sendErrorResponse(response, 500, "Internal server error: " + e.getMessage());
        }
    }
    
    /**
     * Handles GET /api/v1/announcements/metrics - Get announcement system metrics
     */
    private void handleGetAnnouncementMetrics(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            CompletableFuture<java.util.Map<String, Object>> future = announcementService.getAnnouncementMetrics();
            future.whenComplete((metrics, throwable) -> {
                try {
                    if (throwable != null) {
                        logger.error("Error retrieving announcement metrics", throwable);
                        sendErrorResponse(response, 500, "Failed to retrieve metrics: " + throwable.getMessage());
                    } else {
                        response.setStatus(200);
                        response.getWriter().write(buildMetricsResponse(metrics));
                    }
                } catch (IOException e) {
                    logger.error("Error writing metrics response", e);
                }
            });
            
        } catch (Exception e) {
            logger.error("Error handling metrics request", e);
            sendErrorResponse(response, 500, "Internal server error: " + e.getMessage());
        }
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
        // Don't overwrite an already-set successful status (e.g. 201 Created).
        int status = response.getStatus();
        if (status < 200 || status >= 300) {
            response.setStatus(200);
        }
        PrintWriter writer = response.getWriter();
        writer.write(json);
        writer.flush();
    }
    
    /**
     * Sends an error response using the canonical ApiResponse envelope.
     */
    private void sendErrorResponse(HttpServletResponse response, int statusCode, String message) throws IOException {
        response.setStatus(statusCode);
        String code = switch (statusCode) {
            case 400 -> "BAD_REQUEST";
            case 401 -> "UNAUTHORIZED";
            case 404 -> "NOT_FOUND";
            case 500 -> "INTERNAL_ERROR";
            default -> "ERROR";
        };
        PrintWriter writer = response.getWriter();
        writer.write(gson.toJson(ApiResponse.error(code, message)));
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
    
    /**
     * Parses a single announcement from JSON string.
     * This is a simplified JSON parser - a real implementation would use Jackson or similar.
     */
    private AnnouncementDTO parseAnnouncementFromJson(String json, String id) {
        AnnouncementDTO announcement = new AnnouncementDTO();
        announcement.setId(id);
        
        // Simplified JSON parsing - extract key fields
        try {
            if (json.contains("\"title\":")) {
                String title = extractJsonValue(json, "title");
                announcement.setTitle(title);
            }
            
            if (json.contains("\"message\":")) {
                String message = extractJsonValue(json, "message");
                announcement.setMessage(message);
            }
            
            if (json.contains("\"type\":")) {
                String type = extractJsonValue(json, "type");
                announcement.setType(type);
            }
            
            if (json.contains("\"active\":")) {
                String activeStr = extractJsonValue(json, "active");
                boolean active = "true".equalsIgnoreCase(activeStr);
                announcement.setActive(active);
            }
            
            if (json.contains("\"interval_seconds\":")) {
                String intervalStr = extractJsonValue(json, "interval_seconds");
                try {
                    int interval = Integer.parseInt(intervalStr);
                    announcement.setIntervalSeconds(interval);
                } catch (NumberFormatException e) {
                    // Use default interval
                    announcement.setIntervalSeconds(300);
                }
            }
            
        } catch (Exception e) {
            logger.error("Error parsing announcement JSON", e);
            throw new IllegalArgumentException("Invalid JSON format: " + e.getMessage());
        }
        
        return announcement;
    }
    
    /**
     * Parses a list of announcements from JSON array string.
     * This is a simplified JSON parser - a real implementation would use Jackson or similar.
     */
    private List<AnnouncementDTO> parseAnnouncementListFromJson(String json) {
        List<AnnouncementDTO> announcements = new java.util.ArrayList<>();
        
        try {
            // Remove array brackets and split by object boundaries
            String cleaned = json.trim();
            if (!cleaned.startsWith("[") || !cleaned.endsWith("]")) {
                throw new IllegalArgumentException("JSON must be an array");
            }
            
            cleaned = cleaned.substring(1, cleaned.length() - 1); // Remove [ ]
            
            // Split by },{ pattern to separate objects
            String[] objects = cleaned.split("\\},\\s*\\{");
            
            for (int i = 0; i < objects.length; i++) {
                String obj = objects[i].trim();
                
                // Add back braces if they were removed during split
                if (!obj.startsWith("{")) obj = "{" + obj;
                if (!obj.endsWith("}")) obj = obj + "}";
                
                // Generate a temporary ID for parsing
                String tempId = "import_" + System.currentTimeMillis() + "_" + i;
                AnnouncementDTO announcement = parseAnnouncementFromJson(obj, tempId);
                announcement.setId(null); // Clear temp ID - let service generate real ID
                announcements.add(announcement);
            }
            
        } catch (Exception e) {
            logger.error("Error parsing announcement list JSON", e);
            throw new IllegalArgumentException("Invalid JSON array format: " + e.getMessage());
        }
        
        return announcements;
    }
    
    /**
     * Extracts a value from JSON string for a given key.
     * This is a simplified extraction - a real implementation would use Jackson or similar.
     */
    private String extractJsonValue(String json, String key) {
        String searchPattern = "\"" + key + "\":";
        int startIndex = json.indexOf(searchPattern);
        if (startIndex == -1) return "";
        
        startIndex += searchPattern.length();
        
        // Skip whitespace
        while (startIndex < json.length() && Character.isWhitespace(json.charAt(startIndex))) {
            startIndex++;
        }
        
        if (startIndex >= json.length()) return "";
        
        char firstChar = json.charAt(startIndex);
        String value;
        
        if (firstChar == '"') {
            // String value
            startIndex++; // Skip opening quote
            int endIndex = json.indexOf('"', startIndex);
            if (endIndex == -1) return "";
            value = json.substring(startIndex, endIndex);
        } else {
            // Non-string value (number, boolean)
            int endIndex = startIndex;
            while (endIndex < json.length()) {
                char ch = json.charAt(endIndex);
                if (ch == ',' || ch == '}' || Character.isWhitespace(ch)) {
                    break;
                }
                endIndex++;
            }
            value = json.substring(startIndex, endIndex).trim();
        }
        
        return value;
    }
    
    /**
     * Builds a JSON response for metrics data.
     */
    private String buildMetricsResponse(java.util.Map<String, Object> metrics) {
        StringBuilder json = new StringBuilder();
        json.append("{");
        
        boolean first = true;
        for (java.util.Map.Entry<String, Object> entry : metrics.entrySet()) {
            if (!first) json.append(",");
            first = false;
            
            String key = entry.getKey();
            Object value = entry.getValue();
            
            json.append("\"").append(escapeJson(key)).append("\":");
            
            if (value == null) {
                json.append("null");
            } else if (value instanceof String) {
                json.append("\"").append(escapeJson(value.toString())).append("\"");
            } else if (value instanceof Number || value instanceof Boolean) {
                json.append(value.toString());
            } else if (value instanceof java.util.Map) {
                json.append(buildNestedMapJson((java.util.Map<?, ?>) value));
            } else if (value instanceof java.util.List) {
                json.append(buildListJson((java.util.List<?>) value));
            } else {
                json.append("\"").append(escapeJson(value.toString())).append("\"");
            }
        }
        
        json.append("}");
        return json.toString();
    }
    
    /**
     * Builds JSON for nested map objects.
     */
    private String buildNestedMapJson(java.util.Map<?, ?> map) {
        StringBuilder json = new StringBuilder();
        json.append("{");
        
        boolean first = true;
        for (java.util.Map.Entry<?, ?> entry : map.entrySet()) {
            if (!first) json.append(",");
            first = false;
            
            String key = entry.getKey().toString();
            Object value = entry.getValue();
            
            json.append("\"").append(escapeJson(key)).append("\":");
            
            if (value == null) {
                json.append("null");
            } else if (value instanceof String) {
                json.append("\"").append(escapeJson(value.toString())).append("\"");
            } else if (value instanceof Number || value instanceof Boolean) {
                json.append(value.toString());
            } else {
                json.append("\"").append(escapeJson(value.toString())).append("\"");
            }
        }
        
        json.append("}");
        return json.toString();
    }
    
    /**
     * Builds JSON for list objects.
     */
    private String buildListJson(java.util.List<?> list) {
        StringBuilder json = new StringBuilder();
        json.append("[");
        
        boolean first = true;
        for (Object item : list) {
            if (!first) json.append(",");
            first = false;
            
            if (item == null) {
                json.append("null");
            } else if (item instanceof String) {
                json.append("\"").append(escapeJson(item.toString())).append("\"");
            } else if (item instanceof Number || item instanceof Boolean) {
                json.append(item.toString());
            } else {
                json.append("\"").append(escapeJson(item.toString())).append("\"");
            }
        }
        
        json.append("]");
        return json.toString();
    }
    
    /**
     * Handles POST /api/v1/announcements/bulk - Bulk create announcements
     */
    private void handleBulkCreateAnnouncements(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            StringBuilder jsonBody = new StringBuilder();
            String line;
            java.io.BufferedReader reader = request.getReader();
            while ((line = reader.readLine()) != null) {
                jsonBody.append(line);
            }
            
            String requestBody = jsonBody.toString();
            if (requestBody == null || requestBody.trim().isEmpty()) {
                sendErrorResponse(response, 400, "Request body is required");
                return;
            }
            
            // Accept either a raw JSON array or an object with an "announcements" array.
            com.google.gson.JsonElement root = JsonParser.parseString(requestBody);
            String arrayJson;
            if (root.isJsonArray()) {
                arrayJson = root.getAsJsonArray().toString();
            } else if (root.isJsonObject() && root.getAsJsonObject().has("announcements") && root.getAsJsonObject().get("announcements").isJsonArray()) {
                arrayJson = root.getAsJsonObject().get("announcements").getAsJsonArray().toString();
            } else {
                sendErrorResponse(response, 400, "Invalid request: provide a JSON array or an object with 'announcements' array");
                return;
            }

            // Parse and create announcements synchronously so client immediately receives result and IDs
            try {
                List<AnnouncementDTO> announcements = parseAnnouncementListFromJson(arrayJson);
                List<AnnouncementDTO> createdAnnouncements = announcementService.bulkCreateAnnouncements(announcements).join();
                
                // Build response with created announcements and their IDs
                StringBuilder responseJson = new StringBuilder();
                responseJson.append("{\"created_count\":").append(createdAnnouncements.size())
                           .append(",\"message\":\"Successfully created ").append(createdAnnouncements.size()).append(" announcements\"")
                           .append(",\"announcements\":[");
                
                for (int i = 0; i < createdAnnouncements.size(); i++) {
                    if (i > 0) responseJson.append(",");
                    responseJson.append(buildAnnouncementResponse(createdAnnouncements.get(i)));
                }
                
                responseJson.append("]}");
                
                response.setStatus(201);
                sendSuccessResponse(response, responseJson.toString());
            } catch (Exception e) {
                logger.error("Error bulk creating announcements", e);
                sendErrorResponse(response, 500, "Failed to create announcements: " + e.getMessage());
            }
            
        } catch (Exception e) {
            logger.error("Error handling bulk create request", e);
            sendErrorResponse(response, 500, "Internal server error: " + e.getMessage());
        }
    }
    
    /**
     * Handles PUT /api/v1/announcements/bulk/activate - Bulk activate announcements
     */
    private void handleBulkActivateAnnouncements(HttpServletRequest request, HttpServletResponse response) throws IOException {
        logger.info("Bulk activate handler called");
        try {
            StringBuilder jsonBody = new StringBuilder();
            String line;
            java.io.BufferedReader reader = request.getReader();
            while ((line = reader.readLine()) != null) {
                jsonBody.append(line);
            }
            
            String requestBody = jsonBody.toString();
            logger.info("Bulk activate request body: " + requestBody);
            
            if (requestBody == null || requestBody.trim().isEmpty()) {
                sendErrorResponse(response, 400, "Request body is required");
                return;
            }
            
            JsonObject jsonObject = JsonParser.parseString(requestBody).getAsJsonObject();
            
            if (!jsonObject.has("ids") || !jsonObject.get("ids").isJsonArray()) {
                sendErrorResponse(response, 400, "Invalid request: 'ids' array is required");
                return;
            }
            
            java.util.List<String> ids = new java.util.ArrayList<>();
            jsonObject.get("ids").getAsJsonArray().forEach(element -> {
                if (element.isJsonPrimitive()) {
                    ids.add(element.getAsString());
                }
            });
            
            if (ids.isEmpty()) {
                sendErrorResponse(response, 400, "No valid announcement IDs provided");
                return;
            }
            
            // DEBUG: Log the parsed IDs
            logger.info("Bulk activate request received with IDs: " + ids.toString());
            
            // Process each ID individually for activation with error handling
            List<CompletableFuture<BulkOperationResult>> futures = ids.stream()
                .map(id -> {
                    logger.info("Processing activation for ID: " + id);
                    return announcementService.activateAnnouncement(id)
                        .thenApply(v -> new BulkOperationResult(id, true, null))
                        .exceptionally(ex -> {
                            logger.warning("Failed to activate announcement " + id + ": " + ex.getMessage());
                            return new BulkOperationResult(id, false, ex.getMessage());
                        });
                })
                .collect(java.util.stream.Collectors.toList());
            
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).whenComplete((result, throwable) -> {
                try {
                    List<BulkOperationResult> results = futures.stream()
                        .map(CompletableFuture::join)
                        .collect(java.util.stream.Collectors.toList());
                    
                    long successful = results.stream().filter(r -> r.success).count();
                    long failed = results.stream().filter(r -> !r.success).count();
                    
                    response.setStatus(200);
                    
                    // Build detailed response
                    StringBuilder jsonResponse = new StringBuilder();
                    jsonResponse.append("{");
                    jsonResponse.append("\"message\":\"Bulk activation completed\",");
                    jsonResponse.append("\"total\":").append(ids.size()).append(",");
                    jsonResponse.append("\"successful\":").append(successful).append(",");
                    jsonResponse.append("\"failed\":").append(failed).append(",");
                    jsonResponse.append("\"results\":[");
                    
                    for (int i = 0; i < results.size(); i++) {
                        BulkOperationResult r = results.get(i);
                        jsonResponse.append("{");
                        jsonResponse.append("\"id\":\"").append(r.id).append("\",");
                        jsonResponse.append("\"success\":").append(r.success);
                        if (r.error != null) {
                            jsonResponse.append(",\"error\":\"").append(r.error.replace("\"", "\\\"")).append("\"");
                        }
                        jsonResponse.append("}");
                        if (i < results.size() - 1) jsonResponse.append(",");
                    }
                    
                    jsonResponse.append("]}");
                    response.getWriter().write(jsonResponse.toString());
                    
                } catch (IOException e) {
                    logger.error("Error writing bulk activation response", e);
                }
            });
            
        } catch (Exception e) {
            logger.error("Error handling bulk activation request", e);
            sendErrorResponse(response, 500, "Internal server error: " + e.getMessage());
        }
    }
    
    /**
     * Handles PUT /api/v1/announcements/bulk/deactivate - Bulk deactivate announcements
     */
    private void handleBulkDeactivateAnnouncements(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            StringBuilder jsonBody = new StringBuilder();
            String line;
            java.io.BufferedReader reader = request.getReader();
            while ((line = reader.readLine()) != null) {
                jsonBody.append(line);
            }
            
            String requestBody = jsonBody.toString();
            if (requestBody == null || requestBody.trim().isEmpty()) {
                sendErrorResponse(response, 400, "Request body is required");
                return;
            }
            
            JsonObject jsonObject = JsonParser.parseString(requestBody).getAsJsonObject();
            
            if (!jsonObject.has("ids") || !jsonObject.get("ids").isJsonArray()) {
                sendErrorResponse(response, 400, "Invalid request: 'ids' array is required");
                return;
            }
            
            java.util.List<String> ids = new java.util.ArrayList<>();
            jsonObject.get("ids").getAsJsonArray().forEach(element -> {
                if (element.isJsonPrimitive()) {
                    ids.add(element.getAsString());
                }
            });
            
            if (ids.isEmpty()) {
                sendErrorResponse(response, 400, "No valid announcement IDs provided");
                return;
            }
            
            // Process each ID individually for deactivation with error handling
            List<CompletableFuture<BulkOperationResult>> futures = ids.stream()
                .map(id -> 
                    announcementService.deactivateAnnouncement(id)
                        .thenApply(v -> new BulkOperationResult(id, true, null))
                        .exceptionally(ex -> {
                            logger.warning("Failed to deactivate announcement " + id + ": " + ex.getMessage());
                            return new BulkOperationResult(id, false, ex.getMessage());
                        })
                )
                .collect(java.util.stream.Collectors.toList());
            
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).whenComplete((result, throwable) -> {
                try {
                    List<BulkOperationResult> results = futures.stream()
                        .map(CompletableFuture::join)
                        .collect(java.util.stream.Collectors.toList());
                    
                    long successful = results.stream().filter(r -> r.success).count();
                    long failed = results.stream().filter(r -> !r.success).count();
                    
                    response.setStatus(200);
                    
                    // Build detailed response
                    StringBuilder jsonResponse = new StringBuilder();
                    jsonResponse.append("{");
                    jsonResponse.append("\"message\":\"Bulk deactivation completed\",");
                    jsonResponse.append("\"total\":").append(ids.size()).append(",");
                    jsonResponse.append("\"successful\":").append(successful).append(",");
                    jsonResponse.append("\"failed\":").append(failed).append(",");
                    jsonResponse.append("\"results\":[");
                    
                    for (int i = 0; i < results.size(); i++) {
                        BulkOperationResult r = results.get(i);
                        jsonResponse.append("{");
                        jsonResponse.append("\"id\":\"").append(r.id).append("\",");
                        jsonResponse.append("\"success\":").append(r.success);
                        if (r.error != null) {
                            jsonResponse.append(",\"error\":\"").append(r.error.replace("\"", "\\\"")).append("\"");
                        }
                        jsonResponse.append("}");
                        if (i < results.size() - 1) jsonResponse.append(",");
                    }
                    
                    jsonResponse.append("]}");
                    response.getWriter().write(jsonResponse.toString());
                    
                } catch (IOException e) {
                    logger.error("Error writing bulk deactivation response", e);
                }
            });
            
        } catch (Exception e) {
            logger.error("Error handling bulk deactivation request", e);
            sendErrorResponse(response, 500, "Internal server error: " + e.getMessage());
        }
    }
    
    /**
     * Result class for tracking individual bulk operation outcomes
     */
    private static class BulkOperationResult {
        public final String id;
        public final boolean success;
        public final String error;
        
        public BulkOperationResult(String id, boolean success, String error) {
            this.id = id;
            this.success = success;
            this.error = error;
        }
    }
}
