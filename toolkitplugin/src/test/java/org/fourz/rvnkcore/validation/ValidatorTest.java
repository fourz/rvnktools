package org.fourz.rvnkcore.validation;

import org.fourz.rvnkcore.api.exception.ValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the Validator class.
 */
class ValidatorTest {

    @Test
    @DisplayName("Validator chain validates multiple fields")
    void validatorChainValidatesMultipleFields() {
        ValidationResult result = Validator.create()
            .field("username", "testuser")
                .notBlank("Username is required")
                .lengthBetween(3, 16, "Username must be 3-16 characters")
            .field("age", 25)
                .notNull("Age is required")
                .between(0, 150, "Age must be between 0 and 150")
            .validate();

        assertTrue(result.isValid());
        assertEquals(0, result.getErrorCount());
    }

    @Test
    @DisplayName("Validator chain collects errors for invalid fields")
    void validatorChainCollectsErrors() {
        ValidationResult result = Validator.create()
            .field("username", "")
                .notBlank("Username is required")
            .field("age", -5)
                .between(0, 150, "Age must be between 0 and 150")
            .validate();

        assertFalse(result.isValid());
        assertEquals(2, result.getErrorCount());
    }

    @Test
    @DisplayName("requireNotNull throws for null value")
    void requireNotNullThrowsForNull() {
        assertThrows(ValidationException.class, () ->
            Validator.requireNotNull("field", null));
    }

    @Test
    @DisplayName("requireNotNull passes for non-null value")
    void requireNotNullPassesForNonNull() {
        assertDoesNotThrow(() ->
            Validator.requireNotNull("field", "value"));
    }

    @Test
    @DisplayName("requireNotBlank throws for blank string")
    void requireNotBlankThrowsForBlank() {
        assertThrows(ValidationException.class, () ->
            Validator.requireNotBlank("field", "   "));
    }

    @Test
    @DisplayName("requireNotBlank throws for null string")
    void requireNotBlankThrowsForNull() {
        assertThrows(ValidationException.class, () ->
            Validator.requireNotBlank("field", null));
    }

    @Test
    @DisplayName("requireNotBlank passes for non-blank string")
    void requireNotBlankPassesForNonBlank() {
        assertDoesNotThrow(() ->
            Validator.requireNotBlank("field", "value"));
    }

    @Test
    @DisplayName("requireUuid parses valid UUID")
    void requireUuidParsesValidUuid() throws ValidationException {
        String uuidStr = "550e8400-e29b-41d4-a716-446655440000";
        UUID result = Validator.requireUuid("id", uuidStr);
        assertEquals(UUID.fromString(uuidStr), result);
    }

    @Test
    @DisplayName("requireUuid throws for invalid UUID")
    void requireUuidThrowsForInvalid() {
        assertThrows(ValidationException.class, () ->
            Validator.requireUuid("id", "not-a-uuid"));
    }

    @Test
    @DisplayName("requireInt parses valid integer")
    void requireIntParsesValidInt() throws ValidationException {
        int result = Validator.requireInt("count", "42");
        assertEquals(42, result);
    }

    @Test
    @DisplayName("requireInt throws for non-integer")
    void requireIntThrowsForNonInt() {
        assertThrows(ValidationException.class, () ->
            Validator.requireInt("count", "abc"));
    }

    @Test
    @DisplayName("requireInRange passes for value in range")
    void requireInRangePassesForInRange() throws ValidationException {
        int result = Validator.requireInRange("score", 50, 0, 100);
        assertEquals(50, result);
    }

    @Test
    @DisplayName("requireInRange throws for value below minimum")
    void requireInRangeThrowsForBelowMin() {
        assertThrows(ValidationException.class, () ->
            Validator.requireInRange("score", -10, 0, 100));
    }

    @Test
    @DisplayName("requireInRange throws for value above maximum")
    void requireInRangeThrowsForAboveMax() {
        assertThrows(ValidationException.class, () ->
            Validator.requireInRange("score", 150, 0, 100));
    }
}
