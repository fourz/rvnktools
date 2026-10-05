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
     * Creates a 400 validation-failure response carrying structured field errors (since 1.5.96).
     *
     * <p>JSON: {@code error.code = "VALIDATION_FAILED"}, {@code error.fieldErrors = [{field, message}]},
     * and {@code error.details} repeats each one as {@code "field: message"} so a client that only
     * reads {@code details} still sees the reasons.</p>
     *
     * @param fieldErrors One entry per failing field; must not be empty
     */
    public static <T> ApiResponse<T> validationError(List<FieldError> fieldErrors) {
        List<String> details = fieldErrors.stream().map(FieldError::asDetail).toList();
        String message = fieldErrors.size() == 1
            ? "Validation failed: " + details.get(0)
            : "Validation failed for " + fieldErrors.size() + " fields";
        return new ApiResponse<>(false, null,
            new ApiError(VALIDATION_FAILED, message, details, fieldErrors), ApiMeta.create());
    }

    /** Error code used by {@link #validationError(List)}. Maps to HTTP 400. */
    public static final String VALIDATION_FAILED = "VALIDATION_FAILED";

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
