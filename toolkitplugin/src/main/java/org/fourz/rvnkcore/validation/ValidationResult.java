package org.fourz.rvnkcore.validation;

import org.fourz.rvnkcore.api.exception.ValidationException;

import java.util.*;

/**
 * Container for validation results.
 *
 * Collects validation errors for multiple fields and provides methods
 * to check validity and throw exceptions when validation fails.
 */
public class ValidationResult {

    private final Map<String, List<String>> errors;

    /**
     * Creates a new empty ValidationResult.
     */
    public ValidationResult() {
        this.errors = new LinkedHashMap<>();
    }

    /**
     * Adds an error for a specific field.
     *
     * @param field The field name
     * @param message The error message
     * @return This ValidationResult for chaining
     */
    public ValidationResult addError(String field, String message) {
        errors.computeIfAbsent(field, k -> new ArrayList<>()).add(message);
        return this;
    }

    /**
     * Adds a global error not associated with a specific field.
     *
     * @param message The error message
     * @return This ValidationResult for chaining
     */
    public ValidationResult addGlobalError(String message) {
        return addError("_global", message);
    }

    /**
     * Merges another ValidationResult into this one.
     *
     * @param other The other ValidationResult to merge
     * @return This ValidationResult for chaining
     */
    public ValidationResult merge(ValidationResult other) {
        other.errors.forEach((field, messages) ->
            messages.forEach(msg -> addError(field, msg)));
        return this;
    }

    /**
     * Checks if the validation passed (no errors).
     *
     * @return true if there are no validation errors
     */
    public boolean isValid() {
        return errors.isEmpty();
    }

    /**
     * Checks if the validation failed (has errors).
     *
     * @return true if there are validation errors
     */
    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    /**
     * Gets all errors for a specific field.
     *
     * @param field The field name
     * @return List of error messages, or empty list if no errors
     */
    public List<String> getErrors(String field) {
        return errors.getOrDefault(field, Collections.emptyList());
    }

    /**
     * Gets all field errors as a map.
     *
     * @return Unmodifiable map of field names to error messages
     */
    public Map<String, List<String>> getAllErrors() {
        return Collections.unmodifiableMap(errors);
    }

    /**
     * Gets all error messages as a flat list.
     *
     * @return List of all error messages
     */
    public List<String> getAllErrorMessages() {
        List<String> messages = new ArrayList<>();
        errors.forEach((field, fieldMessages) -> {
            String prefix = "_global".equals(field) ? "" : field + ": ";
            fieldMessages.forEach(msg -> messages.add(prefix + msg));
        });
        return messages;
    }

    /**
     * Gets the total number of errors.
     *
     * @return Total error count
     */
    public int getErrorCount() {
        return errors.values().stream().mapToInt(List::size).sum();
    }

    /**
     * Throws a ValidationException if there are any errors.
     *
     * @throws ValidationException if validation failed
     */
    public void throwIfInvalid() throws ValidationException {
        if (hasErrors()) {
            throw new ValidationException(getAllErrorMessages());
        }
    }

    /**
     * Returns the result if valid, otherwise throws ValidationException.
     *
     * @param <T> The type of value to return
     * @param value The value to return if valid
     * @return The value if validation passed
     * @throws ValidationException if validation failed
     */
    public <T> T getOrThrow(T value) throws ValidationException {
        throwIfInvalid();
        return value;
    }

    /**
     * Creates a successful (empty) validation result.
     *
     * @return A new valid ValidationResult
     */
    public static ValidationResult ok() {
        return new ValidationResult();
    }

    /**
     * Creates a validation result with a single error.
     *
     * @param field The field name
     * @param message The error message
     * @return A new ValidationResult with one error
     */
    public static ValidationResult error(String field, String message) {
        return new ValidationResult().addError(field, message);
    }

    /**
     * Creates a validation result with a global error.
     *
     * @param message The error message
     * @return A new ValidationResult with one global error
     */
    public static ValidationResult globalError(String message) {
        return new ValidationResult().addGlobalError(message);
    }

    @Override
    public String toString() {
        if (isValid()) {
            return "ValidationResult{valid=true}";
        }
        return String.format("ValidationResult{valid=false, errors=%d: %s}",
                            getErrorCount(), getAllErrorMessages());
    }
}
