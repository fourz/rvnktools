package org.fourz.rvnkcore.api.util;

import jakarta.servlet.http.HttpServletRequest;

import java.io.BufferedReader;
import java.io.IOException;

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
     * Resolves the real client IP from standard proxy headers before falling back to
     * the raw remote address.
     *
     * @param req The incoming HTTP request
     * @return Best-guess client IP address string
     */
    public static String getClientIP(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isEmpty()) {
            return xff.split(",")[0].trim();
        }
        String xri = req.getHeader("X-Real-IP");
        if (xri != null && !xri.isEmpty()) {
            return xri;
        }
        return req.getRemoteAddr();
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
}
