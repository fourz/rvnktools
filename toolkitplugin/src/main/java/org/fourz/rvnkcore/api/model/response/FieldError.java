package org.fourz.rvnkcore.api.model.response;

/**
 * One field-level validation failure, carried in {@link ApiError#fieldErrors()}.
 *
 * <p>JSON shape: {@code {"field": "settings.islandRadius", "message": "must be between 16 and 512"}}.
 * Nested fields use dot paths so a form can attach the message to the right control.</p>
 *
 * @param field   Dot path of the offending request field (e.g. {@code "name"}, {@code "settings.seaLevel"})
 * @param message Human-readable reason
 *
 * @since 1.5.96
 */
public record FieldError(String field, String message) {

    /** Renders as {@code field: message}, the form used in {@link ApiError#details()}. */
    public String asDetail() {
        return field + ": " + message;
    }
}
