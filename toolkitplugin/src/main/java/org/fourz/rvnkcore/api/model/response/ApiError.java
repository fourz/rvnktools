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
}
