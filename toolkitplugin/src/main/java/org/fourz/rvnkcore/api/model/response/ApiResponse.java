package org.fourz.rvnkcore.api.model.response;

import java.util.List;

/**
 * Canonical REST API response envelope used across all RVNK plugins.
 *
 * <p>JSON shape:</p>
 * <pre>
 * {
 *   "success": true,
 *   "data": { ... },
 *   "error": null,
 *   "meta": { "timestamp": "2026-03-01T00:00:00Z", "version": "1.1" }
 * }
 * </pre>
 *
 * <p>Usage:</p>
 * <pre>
 * // Successful response
 * ApiResponse.success(myData);
 *
 * // Successful paginated response
 * ApiResponse.success(myList, page, limit, totalItems);
 *
 * // Error response with code
 * ApiResponse.error("NOT_FOUND", "World not found: " + name);
 *
 * // Error response (generic code)
 * ApiResponse.error("World load failed");
 * </pre>
 *
 * @param <T> The payload type
 *
 * @since 1.4.0
 */
public record ApiResponse<T>(
    boolean success,
    T data,
    ApiError error,
    ApiMeta meta
) {
    /**
     * Creates a successful response.
     *
     * @param data Response payload
     */
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, null, ApiMeta.create());
    }

    /**
     * Creates a successful paginated response.
     *
     * @param data       Response payload (current page)
     * @param page       Current page number (1-based)
     * @param limit      Items per page
     * @param totalItems Total matching items across all pages
     */
    public static <T> ApiResponse<T> success(T data, int page, int limit, int totalItems) {
        return new ApiResponse<>(true, data, null, ApiMeta.createPaginated(page, limit, totalItems));
    }

    /**
     * Creates an error response with a machine-readable code.
     *
     * @param code    Machine-readable error code (e.g. {@code "NOT_FOUND"})
     * @param message Human-readable error description
     */
    public static <T> ApiResponse<T> error(String code, String message) {
        return new ApiResponse<>(false, null, new ApiError(code, message, null), ApiMeta.create());
    }

    /**
     * Creates an error response with a code and validation details.
     *
     * @param code    Machine-readable error code
     * @param message Human-readable error description
     * @param details Additional context (e.g. field validation messages)
     */
    public static <T> ApiResponse<T> error(String code, String message, List<String> details) {
        return new ApiResponse<>(false, null, new ApiError(code, message, details), ApiMeta.create());
    }

    /**
     * Creates a generic error response. Uses {@code "ERROR"} as the error code.
     * Prefer {@link #error(String, String)} when a specific code is available.
     *
     * @param message Human-readable error description
     */
    public static <T> ApiResponse<T> error(String message) {
        return error("ERROR", message);
    }
}
