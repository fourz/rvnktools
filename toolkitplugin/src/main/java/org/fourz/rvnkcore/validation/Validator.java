package org.fourz.rvnkcore.validation;

import org.fourz.rvnkcore.api.exception.ValidationException;

import java.util.UUID;
import java.util.function.Predicate;

/**
 * Fluent validation builder for input validation.
 *
 * Provides a chainable API for validating objects and collecting errors.
 *
 * <p>Example usage:
 * <pre>{@code
 * ValidationResult result = Validator.create()
 *     .field("username", username)
 *         .notBlank("Username is required")
 *         .matches(Constraints.USERNAME, "Invalid username format")
 *     .field("email", email)
 *         .notBlank("Email is required")
 *         .satisfies(Constraints.EMAIL, "Invalid email format")
 *     .field("age", age)
 *         .notNull("Age is required")
 *         .satisfies(Constraints.between(0, 150), "Age must be between 0 and 150")
 *     .validate();
 *
 * result.throwIfInvalid();
 * }</pre>
 */
public class Validator {

    private final ValidationResult result;
    private String currentField;
    private Object currentValue;

    private Validator() {
        this.result = new ValidationResult();
    }

    /**
     * Creates a new Validator instance.
     *
     * @return A new Validator
     */
    public static Validator create() {
        return new Validator();
    }

    /**
     * Starts validation for a new field.
     *
     * @param fieldName The name of the field being validated
     * @param value The value to validate
     * @param <T> The type of value
     * @return This Validator for chaining
     */
    public <T> Validator field(String fieldName, T value) {
        this.currentField = fieldName;
        this.currentValue = value;
        return this;
    }

    /**
     * Validates that the current field is not null.
     *
     * @param message Error message if null
     * @return This Validator for chaining
     */
    public Validator notNull(String message) {
        if (currentValue == null) {
            result.addError(currentField, message);
        }
        return this;
    }

    /**
     * Validates that the current string field is not null or empty.
     *
     * @param message Error message if empty
     * @return This Validator for chaining
     */
    public Validator notEmpty(String message) {
        if (currentValue == null || (currentValue instanceof String s && s.isEmpty())) {
            result.addError(currentField, message);
        }
        return this;
    }

    /**
     * Validates that the current string field is not null, empty, or blank.
     *
     * @param message Error message if blank
     * @return This Validator for chaining
     */
    public Validator notBlank(String message) {
        if (currentValue == null || (currentValue instanceof String s && s.isBlank())) {
            result.addError(currentField, message);
        }
        return this;
    }

    /**
     * Validates that the current value satisfies a predicate.
     *
     * @param predicate The validation predicate
     * @param message Error message if predicate fails
     * @param <T> The type of value
     * @return This Validator for chaining
     */
    @SuppressWarnings("unchecked")
    public <T> Validator satisfies(Predicate<T> predicate, String message) {
        if (currentValue != null && !predicate.test((T) currentValue)) {
            result.addError(currentField, message);
        }
        return this;
    }

    /**
     * Validates that the current string matches a regex pattern.
     *
     * @param pattern The regex pattern
     * @param message Error message if pattern doesn't match
     * @return This Validator for chaining
     */
    public Validator matches(String pattern, String message) {
        if (currentValue instanceof String s && !s.matches(pattern)) {
            result.addError(currentField, message);
        }
        return this;
    }

    /**
     * Validates string length is within range.
     *
     * @param min Minimum length (inclusive)
     * @param max Maximum length (inclusive)
     * @param message Error message if out of range
     * @return This Validator for chaining
     */
    public Validator lengthBetween(int min, int max, String message) {
        if (currentValue instanceof String s && (s.length() < min || s.length() > max)) {
            result.addError(currentField, message);
        }
        return this;
    }

    /**
     * Validates number is within range.
     *
     * @param min Minimum value (inclusive)
     * @param max Maximum value (inclusive)
     * @param message Error message if out of range
     * @return This Validator for chaining
     */
    public Validator between(Number min, Number max, String message) {
        if (currentValue instanceof Number n) {
            double val = n.doubleValue();
            if (val < min.doubleValue() || val > max.doubleValue()) {
                result.addError(currentField, message);
            }
        }
        return this;
    }

    /**
     * Validates that the current value is a valid UUID string.
     *
     * @param message Error message if not a valid UUID
     * @return This Validator for chaining
     */
    public Validator uuid(String message) {
        if (currentValue instanceof String s) {
            try {
                UUID.fromString(s);
            } catch (IllegalArgumentException e) {
                result.addError(currentField, message);
            }
        } else if (currentValue != null && !(currentValue instanceof UUID)) {
            result.addError(currentField, message);
        }
        return this;
    }

    /**
     * Validates with a custom condition.
     *
     * @param condition The condition that must be true
     * @param message Error message if condition is false
     * @return This Validator for chaining
     */
    public Validator when(boolean condition, String message) {
        if (!condition) {
            result.addError(currentField, message);
        }
        return this;
    }

    /**
     * Completes validation and returns the result.
     *
     * @return The ValidationResult containing any errors
     */
    public ValidationResult validate() {
        return result;
    }

    /**
     * Completes validation and throws if there are errors.
     *
     * @throws ValidationException if validation failed
     */
    public void validateOrThrow() throws ValidationException {
        result.throwIfInvalid();
    }

    // ========== Static Convenience Methods ==========

    /**
     * Validates that a value is not null.
     *
     * @param fieldName The field name for error messages
     * @param value The value to check
     * @param <T> The type of value
     * @throws ValidationException if value is null
     */
    public static <T> void requireNotNull(String fieldName, T value) throws ValidationException {
        if (value == null) {
            throw new ValidationException(fieldName, "is required");
        }
    }

    /**
     * Validates that a string is not blank.
     *
     * @param fieldName The field name for error messages
     * @param value The string to check
     * @throws ValidationException if string is null or blank
     */
    public static void requireNotBlank(String fieldName, String value) throws ValidationException {
        if (value == null || value.isBlank()) {
            throw new ValidationException(fieldName, "is required");
        }
    }

    /**
     * Validates and parses a UUID string.
     *
     * @param fieldName The field name for error messages
     * @param uuidString The string to parse
     * @return The parsed UUID
     * @throws ValidationException if string is not a valid UUID
     */
    public static UUID requireUuid(String fieldName, String uuidString) throws ValidationException {
        if (uuidString == null || uuidString.isBlank()) {
            throw new ValidationException(fieldName, "is required");
        }
        try {
            return UUID.fromString(uuidString);
        } catch (IllegalArgumentException e) {
            throw new ValidationException(fieldName, "must be a valid UUID");
        }
    }

    /**
     * Validates and parses an integer string.
     *
     * @param fieldName The field name for error messages
     * @param intString The string to parse
     * @return The parsed integer
     * @throws ValidationException if string is not a valid integer
     */
    public static int requireInt(String fieldName, String intString) throws ValidationException {
        if (intString == null || intString.isBlank()) {
            throw new ValidationException(fieldName, "is required");
        }
        try {
            return Integer.parseInt(intString.trim());
        } catch (NumberFormatException e) {
            throw new ValidationException(fieldName, "must be a valid integer");
        }
    }

    /**
     * Validates that an integer is within range.
     *
     * @param fieldName The field name for error messages
     * @param value The value to check
     * @param min Minimum allowed value
     * @param max Maximum allowed value
     * @return The validated value
     * @throws ValidationException if value is out of range
     */
    public static int requireInRange(String fieldName, int value, int min, int max)
            throws ValidationException {
        if (value < min || value > max) {
            throw new ValidationException(fieldName,
                String.format("must be between %d and %d", min, max));
        }
        return value;
    }
}
