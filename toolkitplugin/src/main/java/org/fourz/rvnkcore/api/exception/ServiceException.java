package org.fourz.rvnkcore.api.exception;

/**
 * Exception thrown when service-related operations fail.
 * 
 * This exception is used for service registration, lookup, initialization,
 * and other service lifecycle operations. Extends RuntimeException to work
 * seamlessly with CompletableFuture async operations.
 */
public class ServiceException extends RuntimeException {
    
    /**
     * Creates a new ServiceException with the specified message.
     * 
     * @param message The detail message explaining the exception
     */
    public ServiceException(String message) {
        super(message);
    }
    
    /**
     * Creates a new ServiceException with the specified message and cause.
     * 
     * @param message The detail message explaining the exception
     * @param cause The underlying cause of this exception
     */
    public ServiceException(String message, Throwable cause) {
        super(message, cause);
    }
    
    /**
     * Creates a new ServiceException with the specified cause.
     * 
     * @param cause The underlying cause of this exception
     */
    public ServiceException(Throwable cause) {
        super(cause);
    }
}
