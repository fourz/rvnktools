package org.fourz.rvnkcore.api.exception;

/**
 * Exception thrown when REST API operations fail.
 *
 * This exception is the base class for all API-related exceptions including
 * authentication failures, rate limiting, and request processing errors.
 * Extends RuntimeException to work seamlessly with Jetty handlers.
 */
public class ApiException extends RuntimeException {

    private final int statusCode;
    private final String errorCode;

    /**
     * Creates a new ApiException with the specified message and HTTP status code.
     *
     * @param statusCode The HTTP status code to return
     * @param message The detail message explaining the API failure
     */
    public ApiException(int statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
        this.errorCode = null;
    }

    /**
     * Creates a new ApiException with status code, error code, and message.
     *
     * @param statusCode The HTTP status code to return
     * @param errorCode A machine-readable error code (e.g., "RATE_LIMIT_EXCEEDED")
     * @param message The detail message explaining the API failure
     */
    public ApiException(int statusCode, String errorCode, String message) {
        super(message);
        this.statusCode = statusCode;
        this.errorCode = errorCode;
    }

    /**
     * Creates a new ApiException with status code, message, and cause.
     *
     * @param statusCode The HTTP status code to return
     * @param message The detail message explaining the API failure
     * @param cause The underlying cause of this exception
     */
    public ApiException(int statusCode, String message, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
        this.errorCode = null;
    }

    /**
     * Gets the HTTP status code for this exception.
     *
     * @return The HTTP status code (e.g., 400, 401, 429, 500)
     */
    public int getStatusCode() {
        return statusCode;
    }

    /**
     * Gets the machine-readable error code.
     *
     * @return The error code, or null if not specified
     */
    public String getErrorCode() {
        return errorCode;
    }

    /**
     * Creates a Bad Request (400) exception.
     *
     * @param message The detail message
     * @return A new ApiException with status 400
     */
    public static ApiException badRequest(String message) {
        return new ApiException(400, "BAD_REQUEST", message);
    }

    /**
     * Creates a Not Found (404) exception.
     *
     * @param message The detail message
     * @return A new ApiException with status 404
     */
    public static ApiException notFound(String message) {
        return new ApiException(404, "NOT_FOUND", message);
    }

    /**
     * Creates an Internal Server Error (500) exception.
     *
     * @param message The detail message
     * @return A new ApiException with status 500
     */
    public static ApiException internalError(String message) {
        return new ApiException(500, "INTERNAL_ERROR", message);
    }

    /**
     * Creates an Internal Server Error (500) exception with cause.
     *
     * @param message The detail message
     * @param cause The underlying cause
     * @return A new ApiException with status 500
     */
    public static ApiException internalError(String message, Throwable cause) {
        return new ApiException(500, message, cause);
    }
}
