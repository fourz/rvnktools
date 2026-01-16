package org.fourz.rvnkcore.validation;

import java.util.Collection;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Common validation constraints for use with the Validator.
 *
 * Provides reusable predicates for common validation scenarios.
 */
public final class Constraints {

    private Constraints() {
        // Utility class - prevent instantiation
    }

    // ========== String Constraints ==========

    /**
     * Checks that a string is not null or empty.
     */
    public static final Predicate<String> NOT_EMPTY = s -> s != null && !s.isEmpty();

    /**
     * Checks that a string is not null or blank (whitespace-only).
     */
    public static final Predicate<String> NOT_BLANK = s -> s != null && !s.isBlank();

    /**
     * Creates a constraint for minimum string length.
     *
     * @param min Minimum length (inclusive)
     * @return Predicate that checks minimum length
     */
    public static Predicate<String> minLength(int min) {
        return s -> s != null && s.length() >= min;
    }

    /**
     * Creates a constraint for maximum string length.
     *
     * @param max Maximum length (inclusive)
     * @return Predicate that checks maximum length
     */
    public static Predicate<String> maxLength(int max) {
        return s -> s == null || s.length() <= max;
    }

    /**
     * Creates a constraint for string length range.
     *
     * @param min Minimum length (inclusive)
     * @param max Maximum length (inclusive)
     * @return Predicate that checks length range
     */
    public static Predicate<String> lengthBetween(int min, int max) {
        return s -> s != null && s.length() >= min && s.length() <= max;
    }

    /**
     * Creates a constraint for regex pattern matching.
     *
     * @param pattern The regex pattern
     * @return Predicate that checks pattern match
     */
    public static Predicate<String> matches(String pattern) {
        Pattern p = Pattern.compile(pattern);
        return s -> s != null && p.matcher(s).matches();
    }

    /**
     * Creates a constraint for regex pattern matching.
     *
     * @param pattern The compiled regex pattern
     * @return Predicate that checks pattern match
     */
    public static Predicate<String> matches(Pattern pattern) {
        return s -> s != null && pattern.matcher(s).matches();
    }

    // ========== Common Format Patterns ==========

    private static final Pattern EMAIL_PATTERN =
        Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$");

    private static final Pattern ALPHANUMERIC_PATTERN =
        Pattern.compile("^[A-Za-z0-9]+$");

    private static final Pattern USERNAME_PATTERN =
        Pattern.compile("^[A-Za-z0-9_]{3,16}$");

    private static final Pattern MINECRAFT_USERNAME_PATTERN =
        Pattern.compile("^[A-Za-z0-9_]{3,16}$");

    /**
     * Checks that a string is a valid email format.
     */
    public static final Predicate<String> EMAIL = matches(EMAIL_PATTERN);

    /**
     * Checks that a string contains only alphanumeric characters.
     */
    public static final Predicate<String> ALPHANUMERIC = matches(ALPHANUMERIC_PATTERN);

    /**
     * Checks that a string is a valid username (3-16 alphanumeric + underscore).
     */
    public static final Predicate<String> USERNAME = matches(USERNAME_PATTERN);

    /**
     * Checks that a string is a valid Minecraft username.
     */
    public static final Predicate<String> MINECRAFT_USERNAME = matches(MINECRAFT_USERNAME_PATTERN);

    /**
     * Checks that a string is a valid UUID format.
     */
    public static final Predicate<String> UUID_FORMAT = s -> {
        if (s == null) return false;
        try {
            UUID.fromString(s);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    };

    // ========== Number Constraints ==========

    /**
     * Creates a constraint for minimum value.
     *
     * @param min Minimum value (inclusive)
     * @return Predicate that checks minimum
     */
    public static Predicate<Number> min(Number min) {
        return n -> n != null && n.doubleValue() >= min.doubleValue();
    }

    /**
     * Creates a constraint for maximum value.
     *
     * @param max Maximum value (inclusive)
     * @return Predicate that checks maximum
     */
    public static Predicate<Number> max(Number max) {
        return n -> n != null && n.doubleValue() <= max.doubleValue();
    }

    /**
     * Creates a constraint for value range.
     *
     * @param min Minimum value (inclusive)
     * @param max Maximum value (inclusive)
     * @return Predicate that checks range
     */
    public static Predicate<Number> between(Number min, Number max) {
        return n -> n != null &&
                   n.doubleValue() >= min.doubleValue() &&
                   n.doubleValue() <= max.doubleValue();
    }

    /**
     * Checks that a number is positive (greater than zero).
     */
    public static final Predicate<Number> POSITIVE = n -> n != null && n.doubleValue() > 0;

    /**
     * Checks that a number is non-negative (zero or positive).
     */
    public static final Predicate<Number> NON_NEGATIVE = n -> n != null && n.doubleValue() >= 0;

    // ========== Collection Constraints ==========

    /**
     * Checks that a collection is not null or empty.
     */
    public static final Predicate<Collection<?>> NOT_EMPTY_COLLECTION =
        c -> c != null && !c.isEmpty();

    /**
     * Creates a constraint for minimum collection size.
     *
     * @param min Minimum size (inclusive)
     * @return Predicate that checks minimum size
     */
    public static Predicate<Collection<?>> minSize(int min) {
        return c -> c != null && c.size() >= min;
    }

    /**
     * Creates a constraint for maximum collection size.
     *
     * @param max Maximum size (inclusive)
     * @return Predicate that checks maximum size
     */
    public static Predicate<Collection<?>> maxSize(int max) {
        return c -> c == null || c.size() <= max;
    }

    // ========== Object Constraints ==========

    /**
     * Checks that an object is not null.
     */
    public static final Predicate<Object> NOT_NULL = o -> o != null;

    /**
     * Creates a constraint that checks if an object is one of the allowed values.
     *
     * @param allowedValues The allowed values
     * @param <T> The type of value
     * @return Predicate that checks membership
     */
    @SafeVarargs
    public static <T> Predicate<T> oneOf(T... allowedValues) {
        return value -> {
            for (T allowed : allowedValues) {
                if (allowed.equals(value)) return true;
            }
            return false;
        };
    }
}
