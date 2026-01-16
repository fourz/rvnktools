package org.fourz.rvnkcore.api.exception;

/**
 * Exception thrown when authorization fails.
 *
 * This exception indicates that the client lacks the necessary permissions
 * to access the requested resource or perform the requested action.
 */
public class AuthorizationException extends ApiException {

    private static final int STATUS_UNAUTHORIZED = 401;
    private static final int STATUS_FORBIDDEN = 403;

    private final String requiredPermission;
    private final String resource;

    /**
     * Creates a new AuthorizationException for authentication failure (401).
     *
     * @param message The detail message
     */
    public AuthorizationException(String message) {
        super(STATUS_UNAUTHORIZED, "UNAUTHORIZED", message);
        this.requiredPermission = null;
        this.resource = null;
    }

    /**
     * Creates a new AuthorizationException for permission denial (403).
     *
     * @param resource The resource being accessed
     * @param requiredPermission The permission that was required
     */
    public AuthorizationException(String resource, String requiredPermission) {
        super(STATUS_FORBIDDEN, "FORBIDDEN",
              String.format("Access denied to '%s'. Required permission: %s", resource, requiredPermission));
        this.resource = resource;
        this.requiredPermission = requiredPermission;
    }

    /**
     * Creates a new AuthorizationException with cause.
     *
     * @param message The detail message
     * @param cause The underlying cause
     */
    public AuthorizationException(String message, Throwable cause) {
        super(STATUS_UNAUTHORIZED, message, cause);
        this.requiredPermission = null;
        this.resource = null;
    }

    /**
     * Gets the permission that was required but missing.
     *
     * @return The required permission, or null if not applicable
     */
    public String getRequiredPermission() {
        return requiredPermission;
    }

    /**
     * Gets the resource that access was denied to.
     *
     * @return The resource identifier, or null if not applicable
     */
    public String getResource() {
        return resource;
    }

    /**
     * Creates an unauthorized (401) exception for missing authentication.
     *
     * @return A new AuthorizationException with status 401
     */
    public static AuthorizationException unauthorized() {
        return new AuthorizationException("Authentication required");
    }

    /**
     * Creates an unauthorized (401) exception for invalid token.
     *
     * @return A new AuthorizationException with status 401
     */
    public static AuthorizationException invalidToken() {
        return new AuthorizationException("Invalid or expired authentication token");
    }

    /**
     * Creates a forbidden (403) exception for insufficient permissions.
     *
     * @param resource The resource being accessed
     * @param requiredPermission The permission that was required
     * @return A new AuthorizationException with status 403
     */
    public static AuthorizationException forbidden(String resource, String requiredPermission) {
        return new AuthorizationException(resource, requiredPermission);
    }
}
