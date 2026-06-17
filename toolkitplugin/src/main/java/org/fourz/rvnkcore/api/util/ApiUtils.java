package org.fourz.rvnkcore.api.util;

import com.google.gson.Gson;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.fourz.rvnkcore.api.model.response.ApiResponse;

import java.io.BufferedReader;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;

/**
 * Shared HTTP utility helpers for RVNKCore API servlets.
 *
 * <p>Provides stateless, static methods for common request-handling operations so that
 * plugin-specific servlets (LoreApiServlet, WorldApiServlet, ShopApiServlet, etc.) don't
 * duplicate this logic.</p>
 */
public final class ApiUtils {

    private ApiUtils() {}

    /**
     * Returns the raw TCP remote address of the connection.
     * XFF and X-Real-IP headers are intentionally ignored — they are attacker-controlled
     * and would allow IP whitelist bypass if trusted unconditionally (#1020).
     *
     * @param req The incoming HTTP request
     * @return Client IP address from the TCP connection
     */
    public static String getClientIP(HttpServletRequest req) {
        return req.getRemoteAddr();
    }

    /**
     * Compares two strings in constant time to prevent timing side-channel attacks (#1019).
     * Safe to use for API key comparison.
     *
     * @param a First string
     * @param b Second string
     * @return true if equal
     */
    public static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) return false;
        return MessageDigest.isEqual(a.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                                     b.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    /**
     * Reads the full request body into a string.
     *
     * @param req The incoming HTTP request
     * @return Request body as a string (may be empty)
     * @throws IOException if reading fails
     */
    public static String readRequestBody(HttpServletRequest req) throws IOException {
        StringBuilder body = new StringBuilder();
        try (BufferedReader reader = req.getReader()) {
            String line;
            while ((line = reader.readLine()) != null) {
                body.append(line);
            }
        }
        return body.toString();
    }

    /**
     * Parses a query parameter as an integer, returning a default on parse failure or absence.
     *
     * @param req          The incoming HTTP request
     * @param name         Query parameter name
     * @param defaultValue Value to return if the parameter is absent or non-numeric
     * @return Parsed integer value
     */
    public static int getIntParam(HttpServletRequest req, String name, int defaultValue) {
        return parseIntOrDefault(req.getParameter(name), defaultValue);
    }

    /**
     * Parses a query parameter as a boolean. Accepts {@code "true"} or {@code "1"} as true.
     *
     * @param req          The incoming HTTP request
     * @param name         Query parameter name
     * @param defaultValue Value to return if the parameter is absent
     * @return Parsed boolean value
     */
    public static boolean getBoolParam(HttpServletRequest req, String name, boolean defaultValue) {
        String value = req.getParameter(name);
        if (value == null) return defaultValue;
        return "true".equalsIgnoreCase(value) || "1".equals(value);
    }

    /**
     * Parses an arbitrary string as an integer, returning a default on parse failure or null.
     *
     * @param value        String to parse
     * @param defaultValue Value to return on failure
     * @return Parsed integer value
     */
    public static int parseIntOrDefault(String value, int defaultValue) {
        if (value == null || value.isEmpty()) return defaultValue;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * Extracts all query parameters from the request as a single-value map.
     * When a parameter has multiple values, only the first is kept.
     *
     * @param req The incoming HTTP request
     * @return Map of parameter name to first value
     */
    public static Map<String, String> extractQueryParams(HttpServletRequest req) {
        Map<String, String> params = new HashMap<>();
        req.getParameterMap().forEach((key, values) -> {
            if (values != null && values.length > 0) {
                params.put(key, values[0]);
            }
        });
        return params;
    }

    /**
     * Sends a successful JSON response wrapped in the canonical {@link ApiResponse} envelope.
     *
     * @param resp HTTP response
     * @param gson JSON serializer
     * @param data Payload to wrap in {@code ApiResponse.success(data)}
     */
    public static void sendSuccess(HttpServletResponse resp, Gson gson, Object data) {
        sendJson(resp, gson, 200, ApiResponse.success(data));
    }

    /**
     * Sends an error JSON response wrapped in the canonical {@link ApiResponse} envelope.
     *
     * @param resp    HTTP response
     * @param gson    JSON serializer
     * @param status  HTTP status code
     * @param code    Machine-readable error code (e.g. {@code "NOT_FOUND"})
     * @param message Human-readable error description
     */
    public static void sendError(HttpServletResponse resp, Gson gson, int status, String code, String message) {
        sendJson(resp, gson, status, ApiResponse.error(code, message));
    }

    /**
     * Sends an error JSON response, deriving the machine-readable code from the HTTP status.
     * Convenience overload — callers that do not need a custom code string should use this.
     *
     * @param resp    HTTP response
     * @param gson    JSON serializer
     * @param status  HTTP status code
     * @param message Human-readable error description
     */
    public static void sendError(HttpServletResponse resp, Gson gson, int status, String message) {
        String code = switch (status) {
            case 400 -> "BAD_REQUEST";
            case 401 -> "UNAUTHORIZED";
            case 403 -> "FORBIDDEN";
            case 404 -> "NOT_FOUND";
            case 503 -> "SERVICE_UNAVAILABLE";
            case 500 -> "INTERNAL_ERROR";
            default -> "ERROR";
        };
        sendError(resp, gson, status, code, message);
    }

    /**
     * Writes an arbitrary object as JSON to the response with the given HTTP status.
     *
     * @param resp   HTTP response
     * @param gson   JSON serializer
     * @param status HTTP status code
     * @param data   Object to serialize
     */
    public static void sendJson(HttpServletResponse resp, Gson gson, int status, Object data) {
        try {
            resp.setStatus(status);
            resp.setContentType("application/json");
            resp.setCharacterEncoding("UTF-8");
            resp.getWriter().write(gson.toJson(data));
        } catch (IOException e) {
            // Cannot recover — response stream is broken
        }
    }
}
