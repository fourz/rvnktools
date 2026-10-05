package org.fourz.rvnkcore.api.model.response;

import java.util.List;

/**
 * API error details included in a failed {@link ApiResponse}.
 *
 * @param code    Machine-readable error code (e.g. {@code "NOT_FOUND"}, {@code "INVALID_REQUEST"})
 * @param message Human-readable error description
 * @param details Optional list of additional context (field validation errors, etc.)
 * @param fieldErrors Structured field-level validation failures ({@code {field, message}}).
 *                    {@code null} when there are none, so Gson omits the key and every
 *                    pre-1.5.96 error envelope keeps its exact shape. Since 1.5.96.
 *
 * @since 1.4.0
 */
public record ApiError(
    String code,
    String message,
    List<String> details,
    List<FieldError> fieldErrors
) {
    public ApiError {
        details = details == null ? List.of() : List.copyOf(details);
        fieldErrors = fieldErrors == null || fieldErrors.isEmpty() ? null : List.copyOf(fieldErrors);
    }

    /**
     * Pre-1.5.96 constructor, kept so plugins compiled against an older core still link.
     */
    public ApiError(String code, String message, List<String> details) {
        this(code, message, details, null);
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
            case "RATE_LIMITED"     -> 429;
            case "TIMEOUT"          -> 504;
            case "INTERNAL_ERROR"   -> 500;
            default                 -> 400;
        };
    }
}
