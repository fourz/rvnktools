package org.fourz.rvnkcore.api.model.response;

import java.util.List;

/**
 * API error details included in a failed {@link ApiResponse}.
 *
 * @param code    Machine-readable error code (e.g. {@code "NOT_FOUND"}, {@code "INVALID_REQUEST"})
 * @param message Human-readable error description
 * @param details Optional list of additional context (field validation errors, etc.)
 *
 * @since 1.4.0
 */
public record ApiError(
    String code,
    String message,
    List<String> details
) {
    public ApiError {
        details = details == null ? List.of() : List.copyOf(details);
    }

    /**
     * Maps the error code to a suggested HTTP status code.
     *
     * <p>Servlets use this to derive the correct HTTP response status from the
     * canonical error code so callers don't have to track it separately.</p>
     *
     * @return HTTP status integer (e.g. 404, 400, 401)
     */
    public int suggestedHttpStatus() {
        if (code == null) return 400;
        return switch (code) {
            case "NOT_FOUND"        -> 404;
            case "UNAUTHORIZED"     -> 401;
            case "FORBIDDEN"        -> 403;
            case "CONFLICT"         -> 409;
            case "TIMEOUT"          -> 504;
            case "INTERNAL_ERROR"   -> 500;
            default                 -> 400;
        };
    }
}
