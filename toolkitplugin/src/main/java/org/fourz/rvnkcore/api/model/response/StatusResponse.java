package org.fourz.rvnkcore.api.model.response;

/**
 * Generic status response for REST API operations.
 * Used for operations that don't return specific data.
 */
public class StatusResponse {
    private boolean success;
    private String message;
    private int statusCode;

    // Default constructor for JSON deserialization
    public StatusResponse() {}

    public StatusResponse(boolean success, String message) {
        this.success = success;
        this.message = message;
        this.statusCode = success ? 200 : 400;
    }

    public StatusResponse(boolean success, String message, int statusCode) {
        this.success = success;
        this.message = message;
        this.statusCode = statusCode;
    }

    // Static factory methods
    public static StatusResponse success(String message) {
        return new StatusResponse(true, message);
    }

    public static StatusResponse error(String message) {
        return new StatusResponse(false, message);
    }

    public static StatusResponse error(String message, int statusCode) {
        return new StatusResponse(false, message, statusCode);
    }

    // Getters
    public boolean isSuccess() { return success; }
    public String getMessage() { return message; }
    public int getStatusCode() { return statusCode; }

    // Setters for JSON deserialization
    public void setSuccess(boolean success) { this.success = success; }
    public void setMessage(String message) { this.message = message; }
    public void setStatusCode(int statusCode) { this.statusCode = statusCode; }
}
