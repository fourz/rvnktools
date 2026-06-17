package org.fourz.rvnkcore.api.model.response;

import java.time.Instant;
import org.fourz.rvnkcore.ApiVersion;

/**
 * Metadata attached to every {@link ApiResponse}.
 * Includes an ISO-8601 timestamp, API version, and optional pagination fields.
 *
 * @param timestamp  ISO-8601 UTC timestamp of when the response was generated
 * @param version    API version string (from {@link ApiVersion#API_VERSION})
 * @param page       Current page number (paginated responses only)
 * @param limit      Page size (paginated responses only)
 * @param totalItems Total matching items across all pages (paginated responses only)
 * @param totalPages Total number of pages (paginated responses only)
 *
 * @since 1.4.0
 */
public record ApiMeta(
    String timestamp,
    String version,
    Integer page,
    Integer limit,
    Integer totalItems,
    Integer totalPages
) {
    /**
     * Creates basic (non-paginated) metadata with the current timestamp.
     */
    public static ApiMeta create() {
        return new ApiMeta(Instant.now().toString(), ApiVersion.API_VERSION, null, null, null, null);
    }

    /**
     * Creates paginated metadata with the current timestamp.
     *
     * @param page       Current page (1-based)
     * @param limit      Items per page
     * @param totalItems Total items across all pages
     */
    public static ApiMeta createPaginated(int page, int limit, int totalItems) {
        int totalPages = limit > 0 ? (int) Math.ceil((double) totalItems / limit) : 0;
        return new ApiMeta(Instant.now().toString(), ApiVersion.API_VERSION, page, limit, totalItems, totalPages);
    }
}
