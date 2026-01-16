package org.fourz.rvnkcore.api.exception;

/**
 * Exception thrown when repository operations fail.
 *
 * This exception is used for data access layer failures including CRUD operations,
 * query execution, and transaction management. Extends RuntimeException to work
 * seamlessly with CompletableFuture async operations.
 *
 * Note: For low-level database errors, see {@link DatabaseException}.
 * RepositoryException is for higher-level repository pattern failures.
 */
public class RepositoryException extends RuntimeException {

    private final String entityType;
    private final String operation;

    /**
     * Creates a new RepositoryException with the specified message.
     *
     * @param message The detail message explaining the repository failure
     */
    public RepositoryException(String message) {
        super(message);
        this.entityType = null;
        this.operation = null;
    }

    /**
     * Creates a new RepositoryException for a specific entity and operation.
     *
     * @param entityType The type of entity involved
     * @param operation The operation that failed (e.g., "save", "find", "delete")
     * @param message The detail message
     */
    public RepositoryException(String entityType, String operation, String message) {
        super(String.format("%s operation failed for %s: %s", operation, entityType, message));
        this.entityType = entityType;
        this.operation = operation;
    }

    /**
     * Creates a new RepositoryException with the specified message and cause.
     *
     * @param message The detail message explaining the repository failure
     * @param cause The underlying cause of this exception
     */
    public RepositoryException(String message, Throwable cause) {
        super(message, cause);
        this.entityType = null;
        this.operation = null;
    }

    /**
     * Creates a new RepositoryException for a specific entity and operation with cause.
     *
     * @param entityType The type of entity involved
     * @param operation The operation that failed
     * @param cause The underlying cause
     */
    public RepositoryException(String entityType, String operation, Throwable cause) {
        super(String.format("%s operation failed for %s", operation, entityType), cause);
        this.entityType = entityType;
        this.operation = operation;
    }

    /**
     * Creates a new RepositoryException with the specified cause.
     *
     * @param cause The underlying cause of this exception
     */
    public RepositoryException(Throwable cause) {
        super(cause);
        this.entityType = null;
        this.operation = null;
    }

    /**
     * Gets the entity type involved in the failed operation.
     *
     * @return The entity type, or null if not specified
     */
    public String getEntityType() {
        return entityType;
    }

    /**
     * Gets the operation that failed.
     *
     * @return The operation name, or null if not specified
     */
    public String getOperation() {
        return operation;
    }
}
