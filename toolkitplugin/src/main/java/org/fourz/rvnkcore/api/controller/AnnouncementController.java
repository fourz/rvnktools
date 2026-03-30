package org.fourz.rvnkcore.api.controller;

import org.fourz.rvnkcore.api.model.AnnouncementDTO;
import org.fourz.rvnkcore.api.model.AnnouncementTypeDTO;
import org.fourz.rvnkcore.api.model.PlayerPreferencesDTO;
import org.fourz.rvnkcore.api.model.QuietHoursConfig;
import org.fourz.rvnkcore.api.service.AnnouncementService;
import org.fourz.rvnkcore.api.service.PlayerPreferencesService;
import org.fourz.rvnkcore.api.util.ApiUtils;
import org.fourz.rvnkcore.util.log.LogManager;
import org.fourz.rvnkcore.RVNKCore;
import org.fourz.rvnkcore.service.registry.ServiceRegistry;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * REST API controller for announcement management.
 *
 * Provides comprehensive REST endpoints for announcement CRUD operations,
 * search functionality, user preferences, permission checks, and
 * administrative management.
 *
 * @since 1.0.0
 */
public class AnnouncementController extends HttpServlet {

    private final AnnouncementService announcementService;
    private final LogManager logger;
    private final Gson gson;

    private static final String PLUGIN_ID = "announcements";
    private static final Pattern USER_PREFS_PATTERN = Pattern.compile("^/user/([0-9a-fA-F-]{36})/preferences$");
    private static final Pattern PERMISSIONS_PATTERN = Pattern.compile("^/permissions/([0-9a-fA-F-]{36})$");

    // Permission check cache: UUID -> { permissions map, expiry timestamp }
    private static final long PERMISSION_CACHE_TTL_MS = 300_000; // 5 min
    private final ConcurrentHashMap<UUID, CachedPermissions> permissionCache = new ConcurrentHashMap<>();

    private static class CachedPermissions {
        final Map<String, Boolean> permissions;
        final long expiresAt;
        CachedPermissions(Map<String, Boolean> permissions) {
            this.permissions = permissions;
            this.expiresAt = System.currentTimeMillis() + PERMISSION_CACHE_TTL_MS;
        }
        boolean isExpired() { return System.currentTimeMillis() > expiresAt; }
    }

    public AnnouncementController(AnnouncementService announcementService, LogManager logger, Gson gson) {
        this.announcementService = announcementService;
        this.logger = logger;
        this.gson = gson;
    }

    /**
     * Lazily resolves PlayerPreferencesService from ServiceRegistry.
     */
    private PlayerPreferencesService getPreferencesService() {
        RVNKCore core = RVNKCore.getInstance();
        if (core != null) {
            ServiceRegistry registry = core.getServiceRegistry();
            if (registry != null && registry.hasService(PlayerPreferencesService.class)) {
                return registry.getService(PlayerPreferencesService.class);
            }
        }
        return null;
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String pathInfo = request.getPathInfo();
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        try {
            if (pathInfo == null || pathInfo.equals("/")) {
                handleGetAllAnnouncements(request, response);
            } else if (pathInfo.equals("/active")) {
                handleGetActiveAnnouncements(request, response);
            } else if (pathInfo.equals("/count")) {
                handleGetAnnouncementCount(request, response);
            } else if (pathInfo.equals("/count/active")) {
                handleGetActiveAnnouncementCount(request, response);
            } else if (pathInfo.startsWith("/type/")) {
                String type = pathInfo.substring(6);
                handleGetAnnouncementsByType(type, request, response);
            } else if (pathInfo.startsWith("/world/")) {
                String world = pathInfo.substring(7);
                handleGetAnnouncementsForWorld(world, request, response);
            } else if (pathInfo.startsWith("/group/")) {
                String group = pathInfo.substring(7);
                handleGetAnnouncementsForGroup(group, request, response);
            } else if (pathInfo.equals("/search")) {
                String query = request.getParameter("q");
                if (query != null) {
                    handleSearchAnnouncements(query, request, response);
                } else {
                    sendError(response, 400, "Missing required parameter: q");
                }
            } else if (pathInfo.equals("/types")) {
                handleGetAnnouncementTypes(request, response);
            } else if (pathInfo.equals("/metrics")) {
                handleGetAnnouncementMetrics(request, response);
            } else {
                // Check user preferences and permissions patterns
                Matcher prefsMatcher = USER_PREFS_PATTERN.matcher(pathInfo);
                Matcher permsMatcher = PERMISSIONS_PATTERN.matcher(pathInfo);
                if (prefsMatcher.matches()) {
                    handleGetUserPreferences(prefsMatcher.group(1), request, response);
                } else if (permsMatcher.matches()) {
                    handleGetPermissions(permsMatcher.group(1), request, response);
                } else if (pathInfo.startsWith("/")) {
                    String id = pathInfo.substring(1);
                    handleGetAnnouncementById(id, request, response);
                } else {
                    sendError(response, 404, "Endpoint not found");
                }
            }
        } catch (Exception e) {
            logger.error("Error handling GET request: " + pathInfo, e);
            sendError(response, 500, "Internal server error: " + e.getMessage());
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String pathInfo = request.getPathInfo();
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        try {
            if (pathInfo == null || pathInfo.equals("/")) {
                handleCreateAnnouncement(request, response);
            } else if (pathInfo.equals("/search")) {
                handleSearchAnnouncementsPost(request, response);
            } else if (pathInfo.equals("/bulk")) {
                handleBulkCreateAnnouncements(request, response);
            } else if (pathInfo.equals("/bulk-import")) {
                handleBulkImportAnnouncements(request, response);
            } else {
                sendError(response, 404, "Endpoint not found");
            }
        } catch (Exception e) {
            logger.error("Error handling POST request: " + pathInfo, e);
            sendError(response, 500, "Internal server error: " + e.getMessage());
        }
    }

    @Override
    protected void doPut(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String pathInfo = request.getPathInfo();
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        try {
            if (pathInfo != null && pathInfo.startsWith("/")) {
                // Check user preferences pattern first
                Matcher prefsMatcher = USER_PREFS_PATTERN.matcher(pathInfo);
                if (prefsMatcher.matches()) {
                    handlePutUserPreferences(prefsMatcher.group(1), request, response);
                    return;
                }

                String[] parts = pathInfo.substring(1).split("/");
                if (parts.length == 1) {
                    handleUpdateAnnouncement(parts[0], request, response);
                } else if (parts.length == 2 && parts[0].equals("bulk") && parts[1].equals("activate")) {
                    handleBulkActivateAnnouncements(request, response);
                } else if (parts.length == 2 && parts[0].equals("bulk") && parts[1].equals("deactivate")) {
                    handleBulkDeactivateAnnouncements(request, response);
                } else if (parts.length == 2 && parts[1].equals("activate")) {
                    handleActivateAnnouncement(parts[0], request, response);
                } else if (parts.length == 2 && parts[1].equals("deactivate")) {
                    handleDeactivateAnnouncement(parts[0], request, response);
                } else {
                    sendError(response, 404, "Endpoint not found");
                }
            } else {
                sendError(response, 404, "Endpoint not found");
            }
        } catch (Exception e) {
            logger.error("Error handling PUT request: " + pathInfo, e);
            sendError(response, 500, "Internal server error: " + e.getMessage());
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
                handleDeleteAnnouncement(id, request, response);
            } else {
                sendError(response, 404, "Endpoint not found");
            }
        } catch (Exception e) {
            logger.error("Error handling DELETE request: " + pathInfo, e);
            sendError(response, 500, "Internal server error: " + e.getMessage());
        }
    }

    // ====== User Preferences handlers (#539) ======

    private void handleGetUserPreferences(String uuidStr, HttpServletRequest request, HttpServletResponse response) throws IOException {
        PlayerPreferencesService prefsService = getPreferencesService();
        if (prefsService == null) {
            sendError(response, 503, "Preferences service unavailable");
            return;
        }

        try {
            UUID uuid = UUID.fromString(uuidStr);
            PlayerPreferencesDTO prefs = prefsService.getPreferences(uuid, PLUGIN_ID).get(15, TimeUnit.SECONDS);
            Set<String> mutedTypes = prefsService.getDisabledTypes(uuid, PLUGIN_ID).get(15, TimeUnit.SECONDS);

            // Build response in the issue-specified format
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("enabled", prefs.isMasterEnabled());
            data.put("filter", getFilterFromMetadata(prefs));
            data.put("mutedTypes", mutedTypes);

            ApiUtils.sendSuccess(response, gson, data);
        } catch (IllegalArgumentException e) {
            sendError(response, 400, "Invalid UUID: " + uuidStr);
        } catch (Exception e) {
            logger.error("Error retrieving user preferences: " + uuidStr, e);
            sendError(response, 500, "Failed to retrieve preferences: " + e.getMessage());
        }
    }

    private void handlePutUserPreferences(String uuidStr, HttpServletRequest request, HttpServletResponse response) throws IOException {
        PlayerPreferencesService prefsService = getPreferencesService();
        if (prefsService == null) {
            sendError(response, 503, "Preferences service unavailable");
            return;
        }

        try {
            UUID uuid = UUID.fromString(uuidStr);

            String body = ApiUtils.readRequestBody(request);
            if (body.isEmpty()) {
                sendError(response, 400, "Request body is empty");
                return;
            }

            JsonObject json = JsonParser.parseString(body).getAsJsonObject();

            // Get or create preferences
            PlayerPreferencesDTO prefs = prefsService.getPreferences(uuid, PLUGIN_ID).get(15, TimeUnit.SECONDS);

            // Update enabled
            if (json.has("enabled")) {
                prefs.setMasterEnabled(json.get("enabled").getAsBoolean());
            }

            // Update filter via metadata
            if (json.has("filter")) {
                String filter = json.get("filter").getAsString();
                Map<String, String> metadata = prefs.getMetadata();
                if (metadata == null) {
                    metadata = new HashMap<>();
                }
                metadata.put("filter", filter);
                prefs.setMetadata(metadata);
            }

            // Save core preferences
            prefsService.savePreferences(prefs).get(15, TimeUnit.SECONDS);

            // Update muted types
            if (json.has("mutedTypes") && json.get("mutedTypes").isJsonArray()) {
                JsonArray mutedArray = json.get("mutedTypes").getAsJsonArray();
                // First, get current disabled types and re-enable them
                Set<String> currentMuted = prefsService.getDisabledTypes(uuid, PLUGIN_ID).get(15, TimeUnit.SECONDS);
                for (String type : currentMuted) {
                    prefsService.setNotificationEnabled(uuid, PLUGIN_ID, type, true).get(15, TimeUnit.SECONDS);
                }
                // Then disable the requested types
                for (JsonElement el : mutedArray) {
                    String type = el.getAsString();
                    prefsService.setNotificationEnabled(uuid, PLUGIN_ID, type, false).get(15, TimeUnit.SECONDS);
                }
            }

            // Return the updated state
            Set<String> mutedTypes = prefsService.getDisabledTypes(uuid, PLUGIN_ID).get(15, TimeUnit.SECONDS);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("enabled", prefs.isMasterEnabled());
            data.put("filter", getFilterFromMetadata(prefs));
            data.put("mutedTypes", mutedTypes);

            ApiUtils.sendSuccess(response, gson, data);
        } catch (IllegalArgumentException e) {
            sendError(response, 400, "Invalid UUID or request data: " + e.getMessage());
        } catch (Exception e) {
            logger.error("Error saving user preferences: " + uuidStr, e);
            sendError(response, 500, "Failed to save preferences: " + e.getMessage());
        }
    }

    private String getFilterFromMetadata(PlayerPreferencesDTO prefs) {
        if (prefs.getMetadata() != null && prefs.getMetadata().containsKey("filter")) {
            return prefs.getMetadata().get("filter");
        }
        return "all";
    }

    // ====== Permission Check handler (#540) ======

    private void handleGetPermissions(String uuidStr, HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            UUID uuid = UUID.fromString(uuidStr);

            // Check cache first
            CachedPermissions cached = permissionCache.get(uuid);
            if (cached != null && !cached.isExpired()) {
                ApiUtils.sendSuccess(response, gson, cached.permissions);
                return;
            }

            // Resolve permissions via LuckPerms
            Map<String, Boolean> permissions = resolvePermissions(uuid);

            // Cache the result
            permissionCache.put(uuid, new CachedPermissions(permissions));

            // Evict expired entries periodically (lazy cleanup)
            if (permissionCache.size() > 500) {
                permissionCache.entrySet().removeIf(e -> e.getValue().isExpired());
            }

            ApiUtils.sendSuccess(response, gson, permissions);
        } catch (IllegalArgumentException e) {
            sendError(response, 400, "Invalid UUID: " + uuidStr);
        } catch (Exception e) {
            logger.error("Error checking permissions: " + uuidStr, e);
            sendError(response, 500, "Failed to check permissions: " + e.getMessage());
        }
    }

    private static final List<String> ANNOUNCEMENT_PERMISSION_NODES = List.of(
            "rvnk.announcements.create",
            "rvnk.announcements.edit",
            "rvnk.announcements.edit.own",
            "rvnk.announcements.delete",
            "rvnk.announcements.pin"
    );

    private static final Map<String, String> PERM_NODE_TO_KEY = Map.of(
            "rvnk.announcements.create", "canCreate",
            "rvnk.announcements.edit", "canEdit",
            "rvnk.announcements.edit.own", "canEditOwn",
            "rvnk.announcements.delete", "canDelete",
            "rvnk.announcements.pin", "canPin"
    );

    /**
     * Resolves announcement permissions for a UUID via LuckPerms.
     * Uses {@code QueryOptions.nonContextual()} so inherited group permissions
     * resolve correctly for offline players.
     * Falls back to all-false if LuckPerms is unavailable.
     */
    private Map<String, Boolean> resolvePermissions(UUID uuid) {
        Map<String, Boolean> perms = new LinkedHashMap<>();
        perms.put("canCreate", false);
        perms.put("canEdit", false);
        perms.put("canEditOwn", false);
        perms.put("canDelete", false);
        perms.put("canPin", false);

        try {
            Map<String, Boolean> resolved = org.fourz.rvnktools.permission.LuckPermsGroupResolver
                    .checkPermissionsAsync(uuid, ANNOUNCEMENT_PERMISSION_NODES).join();
            for (Map.Entry<String, Boolean> entry : resolved.entrySet()) {
                String key = PERM_NODE_TO_KEY.get(entry.getKey());
                if (key != null) {
                    perms.put(key, entry.getValue());
                }
            }
        } catch (Exception e) {
            logger.warning("LuckPerms unavailable for permission check: " + e.getMessage());
        }

        return perms;
    }

    // ====== GET handlers ======

    private void handleGetAllAnnouncements(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            List<AnnouncementDTO> announcements = announcementService.getAllAnnouncements().get(15, TimeUnit.SECONDS);
            // Filter out payment-depleted announcements from REST response
            announcements = filterPaymentDepleted(announcements);
            ApiUtils.sendSuccess(response, gson, announcements);
        } catch (Exception e) {
            logger.error("Error retrieving all announcements", e);
            sendError(response, 500, "Internal server error: " + e.getMessage());
        }
    }

    private void handleGetActiveAnnouncements(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            List<AnnouncementDTO> announcements = announcementService.getActiveAnnouncements().get(15, TimeUnit.SECONDS);
            ApiUtils.sendSuccess(response, gson, announcements);
        } catch (Exception e) {
            logger.error("Error retrieving active announcements", e);
            sendError(response, 500, "Failed to retrieve active announcements: " + e.getMessage());
        }
    }

    private void handleGetAnnouncementById(String id, HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            Optional<AnnouncementDTO> optional = announcementService.getAnnouncement(id).get(15, TimeUnit.SECONDS);
            if (optional.isPresent()) {
                ApiUtils.sendSuccess(response, gson, optional.get());
            } else {
                sendError(response, 404, "Announcement not found: " + id);
            }
        } catch (Exception e) {
            logger.error("Error retrieving announcement: " + id, e);
            sendError(response, 500, "Failed to retrieve announcement: " + e.getMessage());
        }
    }

    private void handleGetAnnouncementCount(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            long count = announcementService.getAnnouncementCount().get(15, TimeUnit.SECONDS);
            ApiUtils.sendSuccess(response, gson, Map.of("count", count));
        } catch (Exception e) {
            logger.error("Error getting announcement count", e);
            sendError(response, 500, "Failed to get announcement count: " + e.getMessage());
        }
    }

    private void handleGetActiveAnnouncementCount(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            long count = announcementService.getActiveAnnouncementCount().get(15, TimeUnit.SECONDS);
            ApiUtils.sendSuccess(response, gson, Map.of("count", count));
        } catch (Exception e) {
            logger.error("Error getting active announcement count", e);
            sendError(response, 500, "Failed to get active announcement count: " + e.getMessage());
        }
    }

    private void handleGetAnnouncementsByType(String type, HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            List<AnnouncementDTO> announcements = announcementService.getAnnouncementsByType(type).get(15, TimeUnit.SECONDS);
            ApiUtils.sendSuccess(response, gson, announcements);
        } catch (Exception e) {
            logger.error("Error retrieving announcements by type: " + type, e);
            sendError(response, 500, "Failed to retrieve announcements by type: " + e.getMessage());
        }
    }

    private void handleSearchAnnouncements(String query, HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            List<AnnouncementDTO> announcements = announcementService.searchAnnouncements(query).get(15, TimeUnit.SECONDS);
            ApiUtils.sendSuccess(response, gson, announcements);
        } catch (Exception e) {
            logger.error("Error searching announcements: " + query, e);
            sendError(response, 500, "Failed to search announcements: " + e.getMessage());
        }
    }

    private void handleSearchAnnouncementsPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            String body = ApiUtils.readRequestBody(request);
            if (body.isEmpty()) {
                sendError(response, 400, "Request body is empty");
                return;
            }
            JsonObject json = JsonParser.parseString(body).getAsJsonObject();
            if (!json.has("query") || json.get("query").getAsString().trim().isEmpty()) {
                sendError(response, 400, "Missing required field: query");
                return;
            }
            String query = json.get("query").getAsString();
            handleSearchAnnouncements(query, request, response);
        } catch (Exception e) {
            logger.error("Error parsing JSON in search announcement request", e);
            sendError(response, 400, "Invalid JSON format: " + e.getMessage());
        }
    }

    private void handleGetAnnouncementsForWorld(String world, HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            List<AnnouncementDTO> announcements = announcementService.getAnnouncementsForWorld(world).get(15, TimeUnit.SECONDS);
            ApiUtils.sendSuccess(response, gson, announcements);
        } catch (Exception e) {
            logger.error("Error retrieving announcements for world: " + world, e);
            sendError(response, 500, "Failed to retrieve announcements for world: " + e.getMessage());
        }
    }

    private void handleGetAnnouncementsForGroup(String group, HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            List<AnnouncementDTO> announcements = announcementService.getAnnouncementsForGroup(group).get(15, TimeUnit.SECONDS);
            ApiUtils.sendSuccess(response, gson, announcements);
        } catch (Exception e) {
            logger.error("Error retrieving announcements for group: " + group, e);
            sendError(response, 500, "Failed to retrieve announcements for group: " + e.getMessage());
        }
    }

    private void handleGetAnnouncementTypes(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            List<AnnouncementTypeDTO> types = announcementService.getAnnouncementTypes().get(15, TimeUnit.SECONDS);
            ApiUtils.sendSuccess(response, gson, types);
        } catch (Exception e) {
            logger.error("Error retrieving announcement types", e);
            sendError(response, 500, "Failed to retrieve announcement types: " + e.getMessage());
        }
    }

    private void handleGetAnnouncementMetrics(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            Map<String, Object> metrics = announcementService.getAnnouncementMetrics().get(15, TimeUnit.SECONDS);
            ApiUtils.sendSuccess(response, gson, metrics);
        } catch (Exception e) {
            logger.error("Error retrieving announcement metrics", e);
            sendError(response, 500, "Failed to retrieve metrics: " + e.getMessage());
        }
    }

    // ====== POST handlers ======

    private void handleCreateAnnouncement(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            String body = ApiUtils.readRequestBody(request);
            if (body.isEmpty()) {
                sendError(response, 400, "Request body is empty");
                return;
            }

            JsonObject json = JsonParser.parseString(body).getAsJsonObject();

            String title = json.has("title") ? json.get("title").getAsString() : null;
            String message = json.has("content") ? json.get("content").getAsString() :
                           json.has("message") ? json.get("message").getAsString() : null;
            String type = json.has("type") ? json.get("type").getAsString() : null;
            boolean active = json.has("isActive") ? json.get("isActive").getAsBoolean() :
                           json.has("active") ? json.get("active").getAsBoolean() : true;

            if (title == null || title.trim().isEmpty() || message == null || type == null) {
                sendError(response, 400, "Missing required parameters: title, message/content, and type");
                return;
            }

            String ownerUuid = json.has("ownerUuid") ? json.get("ownerUuid").getAsString() : null;

            AnnouncementDTO announcement = new AnnouncementDTO.Builder()
                .title(title)
                .message(message)
                .type(type)
                .active(active)
                .ownerUuid(ownerUuid)
                .build();

            AnnouncementDTO created = announcementService.createAnnouncement(announcement).join();
            ApiUtils.sendJson(response, gson, 201,
                org.fourz.rvnkcore.api.model.response.ApiResponse.success(created));
        } catch (Exception e) {
            logger.error("Error creating announcement", e);
            sendError(response, 500, "Failed to create announcement: " + e.getMessage());
        }
    }

    private void handleBulkCreateAnnouncements(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            String body = ApiUtils.readRequestBody(request);
            if (body.isEmpty()) {
                sendError(response, 400, "Request body is required");
                return;
            }

            JsonElement root = JsonParser.parseString(body);
            JsonArray array;
            if (root.isJsonArray()) {
                array = root.getAsJsonArray();
            } else if (root.isJsonObject() && root.getAsJsonObject().has("announcements")
                       && root.getAsJsonObject().get("announcements").isJsonArray()) {
                array = root.getAsJsonObject().get("announcements").getAsJsonArray();
            } else {
                sendError(response, 400, "Invalid request: provide a JSON array or an object with 'announcements' array");
                return;
            }

            List<AnnouncementDTO> announcements = parseAnnouncementList(array);
            List<AnnouncementDTO> created = announcementService.bulkCreateAnnouncements(announcements).join();

            ApiUtils.sendJson(response, gson, 201,
                org.fourz.rvnkcore.api.model.response.ApiResponse.success(
                    Map.of("createdCount", created.size(),
                           "message", "Successfully created " + created.size() + " announcements",
                           "announcements", created)));
        } catch (Exception e) {
            logger.error("Error bulk creating announcements", e);
            sendError(response, 500, "Failed to create announcements: " + e.getMessage());
        }
    }

    private void handleBulkImportAnnouncements(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            String body = ApiUtils.readRequestBody(request);
            if (body.isEmpty()) {
                sendError(response, 400, "Request body is required");
                return;
            }

            JsonArray array = JsonParser.parseString(body).getAsJsonArray();
            List<AnnouncementDTO> announcements = parseAnnouncementList(array);

            Integer importedCount = announcementService.bulkImportAnnouncements(announcements).get(15, TimeUnit.SECONDS);
            ApiUtils.sendJson(response, gson, 201,
                org.fourz.rvnkcore.api.model.response.ApiResponse.success(
                    Map.of("importedCount", importedCount,
                           "message", "Successfully imported " + importedCount + " announcements")));
        } catch (Exception e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            logger.error("Error bulk importing announcements", e);
            if (cause instanceof IllegalArgumentException) {
                sendError(response, 400, "Invalid announcement data: " + cause.getMessage());
            } else {
                sendError(response, 500, "Failed to import announcements: " + cause.getMessage());
            }
        }
    }

    // ====== PUT handlers ======

    private void handleUpdateAnnouncement(String id, HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            String body = ApiUtils.readRequestBody(request);
            if (body.isEmpty()) {
                sendError(response, 400, "Request body is required");
                return;
            }

            // Check X-User-UUID header for ownership-based updates
            String callerUuid = request.getHeader("X-User-UUID");
            if (callerUuid != null && !callerUuid.isEmpty()) {
                UUID uuid = UUID.fromString(callerUuid);
                Map<String, Boolean> perms = resolvePermissions(uuid);
                boolean canEdit = Boolean.TRUE.equals(perms.get("canEdit"));
                boolean canEditOwn = Boolean.TRUE.equals(perms.get("canEditOwn"));

                if (!canEdit && !canEditOwn) {
                    sendError(response, 403, "No permission to edit announcements");
                    return;
                }

                if (!canEdit && canEditOwn) {
                    // Verify ownership
                    Optional<AnnouncementDTO> existing = announcementService.getAnnouncement(id).get(15, TimeUnit.SECONDS);
                    if (existing.isEmpty()) {
                        sendError(response, 404, "Announcement not found: " + id);
                        return;
                    }
                    if (!callerUuid.equals(existing.get().getOwnerUuid())) {
                        sendError(response, 403, "You can only edit your own announcements");
                        return;
                    }
                    // Owner can only update message — parse body and restrict fields
                    JsonObject json = JsonParser.parseString(body).getAsJsonObject();
                    AnnouncementDTO update = new AnnouncementDTO(id);
                    if (json.has("message")) {
                        update.setMessage(json.get("message").getAsString());
                    } else if (json.has("content")) {
                        update.setMessage(json.get("content").getAsString());
                    }
                    // Ignore title/type/active changes for canEditOwn callers
                    AnnouncementDTO updated = announcementService.updateAnnouncement(update).get(15, TimeUnit.SECONDS);
                    ApiUtils.sendSuccess(response, gson, updated);
                    return;
                }
            }

            // Global canEdit or no X-User-UUID header — full update
            AnnouncementDTO announcement = parseAnnouncementFromJson(body, id);
            AnnouncementDTO updated = announcementService.updateAnnouncement(announcement).get(15, TimeUnit.SECONDS);
            ApiUtils.sendSuccess(response, gson, updated);
        } catch (IllegalArgumentException e) {
            sendError(response, 400, "Invalid UUID: " + e.getMessage());
        } catch (Exception e) {
            logger.error("Error handling update announcement request for ID: " + id, e);
            sendError(response, 500, "Internal server error: " + e.getMessage());
        }
    }

    private void handleActivateAnnouncement(String id, HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            // Ownership check for canEditOwn callers
            if (!checkTogglePermission(id, request, response)) return;

            announcementService.activateAnnouncement(id).get(15, TimeUnit.SECONDS);
            ApiUtils.sendSuccess(response, gson, Map.of("message", "Announcement " + id + " activated"));
        } catch (Exception e) {
            logger.error("Error activating announcement: " + id, e);
            sendError(response, 500, "Failed to activate announcement: " + e.getMessage());
        }
    }

    private void handleDeactivateAnnouncement(String id, HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            // Ownership check for canEditOwn callers
            if (!checkTogglePermission(id, request, response)) return;

            announcementService.deactivateAnnouncement(id).get(15, TimeUnit.SECONDS);
            ApiUtils.sendSuccess(response, gson, Map.of("message", "Announcement " + id + " deactivated"));
        } catch (Exception e) {
            logger.error("Error deactivating announcement: " + id, e);
            sendError(response, 500, "Failed to deactivate announcement: " + e.getMessage());
        }
    }

    /**
     * Checks toggle (activate/deactivate) permission. If X-User-UUID is present and caller
     * has only canEditOwn (not global canEdit), verifies ownership.
     *
     * @return true if the operation should proceed, false if a response has already been sent
     */
    private boolean checkTogglePermission(String id, HttpServletRequest request, HttpServletResponse response) throws IOException {
        String callerUuid = request.getHeader("X-User-UUID");
        if (callerUuid == null || callerUuid.isEmpty()) return true; // Legacy behavior

        try {
            UUID uuid = UUID.fromString(callerUuid);
            Map<String, Boolean> perms = resolvePermissions(uuid);
            boolean canEdit = Boolean.TRUE.equals(perms.get("canEdit"));
            if (canEdit) return true; // Global edit permission — allow all

            boolean canEditOwn = Boolean.TRUE.equals(perms.get("canEditOwn"));
            if (!canEditOwn) {
                sendError(response, 403, "No permission to toggle announcements");
                return false;
            }

            // Verify ownership
            Optional<AnnouncementDTO> existing = announcementService.getAnnouncement(id).get(15, TimeUnit.SECONDS);
            if (existing.isEmpty()) {
                sendError(response, 404, "Announcement not found: " + id);
                return false;
            }
            if (!callerUuid.equals(existing.get().getOwnerUuid())) {
                sendError(response, 403, "You can only toggle your own announcements");
                return false;
            }
            return true;
        } catch (IllegalArgumentException e) {
            sendError(response, 400, "Invalid UUID: " + callerUuid);
            return false;
        } catch (Exception e) {
            logger.error("Error checking toggle permission for: " + id, e);
            sendError(response, 500, "Permission check failed: " + e.getMessage());
            return false;
        }
    }

    private void handleBulkActivateAnnouncements(HttpServletRequest request, HttpServletResponse response) throws IOException {
        handleBulkStateChange(request, response, true);
    }

    private void handleBulkDeactivateAnnouncements(HttpServletRequest request, HttpServletResponse response) throws IOException {
        handleBulkStateChange(request, response, false);
    }

    private void handleBulkStateChange(HttpServletRequest request, HttpServletResponse response, boolean activate) throws IOException {
        String action = activate ? "activation" : "deactivation";
        try {
            String body = ApiUtils.readRequestBody(request);
            if (body.isEmpty()) {
                sendError(response, 400, "Request body is required");
                return;
            }

            JsonObject jsonObject = JsonParser.parseString(body).getAsJsonObject();
            if (!jsonObject.has("ids") || !jsonObject.get("ids").isJsonArray()) {
                sendError(response, 400, "Invalid request: 'ids' array is required");
                return;
            }

            List<String> ids = new ArrayList<>();
            jsonObject.get("ids").getAsJsonArray().forEach(element -> {
                if (element.isJsonPrimitive()) {
                    ids.add(element.getAsString());
                }
            });

            if (ids.isEmpty()) {
                sendError(response, 400, "No valid announcement IDs provided");
                return;
            }

            List<CompletableFuture<Map<String, Object>>> futures = ids.stream()
                .map(id -> {
                    CompletableFuture<Void> op = activate
                        ? announcementService.activateAnnouncement(id)
                        : announcementService.deactivateAnnouncement(id);
                    return op
                        .thenApply(v -> Map.<String, Object>of("id", id, "success", true))
                        .exceptionally(ex -> {
                            logger.warning("Failed to " + (activate ? "activate" : "deactivate") + " announcement " + id + ": " + ex.getMessage());
                            return Map.of("id", id, "success", false, "error", ex.getMessage());
                        });
                })
                .collect(Collectors.toList());

            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get(30, TimeUnit.SECONDS);

            List<Map<String, Object>> results = futures.stream()
                .map(CompletableFuture::join)
                .collect(Collectors.toList());

            long successful = results.stream().filter(r -> (boolean) r.get("success")).count();
            long failed = results.stream().filter(r -> !(boolean) r.get("success")).count();

            ApiUtils.sendSuccess(response, gson, Map.of(
                "message", "Bulk " + action + " completed",
                "total", ids.size(),
                "successful", successful,
                "failed", failed,
                "results", results));
        } catch (Exception e) {
            logger.error("Error handling bulk " + action + " request", e);
            sendError(response, 500, "Internal server error: " + e.getMessage());
        }
    }

    // ====== DELETE handler ======

    private void handleDeleteAnnouncement(String id, HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            // If caller identifies via X-User-UUID, they must have global canDelete
            String callerUuid = request.getHeader("X-User-UUID");
            if (callerUuid != null && !callerUuid.isEmpty()) {
                UUID uuid = UUID.fromString(callerUuid);
                Map<String, Boolean> perms = resolvePermissions(uuid);
                if (!Boolean.TRUE.equals(perms.get("canDelete"))) {
                    sendError(response, 403, "Only administrators can delete announcements");
                    return;
                }
            }

            announcementService.deleteAnnouncement(id).get(15, TimeUnit.SECONDS);
            response.setStatus(204);
        } catch (IllegalArgumentException e) {
            sendError(response, 400, "Invalid UUID: " + e.getMessage());
        } catch (Exception e) {
            logger.error("Error deleting announcement: " + id, e);
            sendError(response, 500, "Failed to delete announcement: " + e.getMessage());
        }
    }

    // ====== Parsing helpers ======

    private AnnouncementDTO parseAnnouncementFromJson(String json, String id) {
        JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
        AnnouncementDTO announcement = new AnnouncementDTO();
        announcement.setId(id);

        if (obj.has("title")) announcement.setTitle(obj.get("title").getAsString());
        if (obj.has("content")) announcement.setMessage(obj.get("content").getAsString());
        else if (obj.has("message")) announcement.setMessage(obj.get("message").getAsString());
        if (obj.has("type")) announcement.setType(obj.get("type").getAsString());
        if (obj.has("active")) announcement.setActive(obj.get("active").getAsBoolean());
        else if (obj.has("isActive")) announcement.setActive(obj.get("isActive").getAsBoolean());
        if (obj.has("intervalSeconds")) announcement.setIntervalSeconds(obj.get("intervalSeconds").getAsInt());

        return announcement;
    }

    private List<AnnouncementDTO> parseAnnouncementList(JsonArray array) {
        List<AnnouncementDTO> announcements = new ArrayList<>();
        for (int i = 0; i < array.size(); i++) {
            AnnouncementDTO dto = parseAnnouncementFromJson(array.get(i).toString(), null);
            dto.setId(null);
            announcements.add(dto);
        }
        return announcements;
    }

    // ====== Filtering helpers ======

    /**
     * Filters out announcements that were deactivated due to payment depletion.
     * These are hidden from the WebUI (distinct from admin-deactivated announcements).
     */
    private List<AnnouncementDTO> filterPaymentDepleted(List<AnnouncementDTO> announcements) {
        return announcements.stream()
            .filter(ann -> {
                Map<String, Object> metadata = ann.getMetadata();
                if (metadata == null) return true;
                Object reason = metadata.get("deactivation_reason");
                return reason == null || !"payment_depleted".equals(reason.toString());
            })
            .collect(Collectors.toList());
    }

    // ====== Response helpers ======

    private void sendError(HttpServletResponse response, int status, String message) {
        String code = switch (status) {
            case 400 -> "BAD_REQUEST";
            case 401 -> "UNAUTHORIZED";
            case 404 -> "NOT_FOUND";
            case 503 -> "SERVICE_UNAVAILABLE";
            case 500 -> "INTERNAL_ERROR";
            default -> "ERROR";
        };
        ApiUtils.sendError(response, gson, status, code, message);
    }
}
