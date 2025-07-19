package org.fourz.rvnkcore.api.exception;

/**
 * Runtime exception thrown when database operations fail.
 * 
 * This exception is used to wrap SQLException and other database-related
 * errors in a more domain-specific exception type. It extends RuntimeException
 * to avoid requiring explicit handling in async CompletableFuture operations.
 * 
 * @since 1.0.0
 */
public class DatabaseException extends RuntimeException {
    
    /**
     * Constructs a new DatabaseException with the specified detail message.
     * 
     * @param message The detail message
     */
    public DatabaseException(String message) {
        super(message);
    }
    
    /**
     * Constructs a new DatabaseException with the specified detail message and cause.
     * 
     * @param message The detail message
     * @param cause The cause of this exception
     */
    public DatabaseException(String message, Throwable cause) {
        super(message, cause);
    }
    
    /**
     * Constructs a new DatabaseException with the specified cause.
     * 
     * @param cause The cause of this exception
     */
    public DatabaseException(Throwable cause) {
        super(cause);
    }
}
