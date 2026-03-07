package org.fourz.rvnkcore.api.exception;

import java.util.Collections;
import java.util.List;

/**
 * Exception thrown when input validation fails.
 *
 * This exception is used for parameter validation, data integrity checks,
 * and other input validation operations. Extends RuntimeException to work
 * seamlessly with CompletableFuture async operations.
 */
public class ValidationException extends RuntimeException {

    private final List<String> violations;
    private final String field;

    /**
     * Creates a new ValidationException with the specified message.
     *
     * @param message The detail message explaining the validation failure
     */
    public ValidationException(String message) {
        super(message);
        this.violations = Collections.emptyList();
        this.field = null;
    }

    /**
     * Creates a new ValidationException for a specific field.
     *
     * @param field The field that failed validation
     * @param message The detail message explaining the validation failure
     */
    public ValidationException(String field, String message) {
        super(String.format("Validation failed for '%s': %s", field, message));
        this.field = field;
        this.violations = Collections.singletonList(message);
    }

    /**
     * Creates a new ValidationException with multiple violations.
     *
     * @param violations List of validation violation messages
     */
    public ValidationException(List<String> violations) {
        super("Validation failed: " + String.join("; ", violations));
        this.violations = Collections.unmodifiableList(violations);
        this.field = null;
    }

    /**
     * Creates a new ValidationException with the specified message and cause.
     *
     * @param message The detail message explaining the validation failure
     * @param cause The underlying cause of this exception
     */
    public ValidationException(String message, Throwable cause) {
        super(message, cause);
        this.violations = Collections.emptyList();
        this.field = null;
    }

    /**
     * Gets the list of validation violations.
     *
     * @return Unmodifiable list of violation messages
     */
    public List<String> getViolations() {
        return violations;
    }

    /**
     * Gets the field that failed validation.
     *
     * @return The field name, or null if not field-specific
     */
    public String getField() {
        return field;
    }

    /**
     * Checks if this exception has multiple violations.
     *
     * @return true if there are multiple violations
     */
    public boolean hasMultipleViolations() {
        return violations.size() > 1;
    }
}
