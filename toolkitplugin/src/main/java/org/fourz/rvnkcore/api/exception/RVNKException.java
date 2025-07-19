package org.fourz.rvnkcore.api.exception;

/**
 * Base exception for all RVNKCore-related exceptions.
 * 
 * This exception serves as the root of the RVNKCore exception hierarchy,
 * allowing for unified exception handling across the framework.
 */
public class RVNKException extends Exception {
    
    /**
     * Creates a new RVNKException with the specified message.
     * 
     * @param message The detail message explaining the exception
     */
    public RVNKException(String message) {
        super(message);
    }
    
    /**
     * Creates a new RVNKException with the specified message and cause.
     * 
     * @param message The detail message explaining the exception
     * @param cause The underlying cause of this exception
     */
    public RVNKException(String message, Throwable cause) {
        super(message, cause);
    }
    
    /**
     * Creates a new RVNKException with the specified cause.
     * 
     * @param cause The underlying cause of this exception
     */
    public RVNKException(Throwable cause) {
        super(cause);
    }
}
