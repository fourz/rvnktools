package org.fourz.rvnkcore.database.repository;

import org.fourz.rvnkcore.api.exception.DatabaseException;
import org.fourz.rvnkcore.database.connection.ConnectionProvider;
import org.fourz.rvnkcore.database.query.QueryBuilder;
import org.fourz.rvnkcore.database.query.BasicSQLQueryBuilder;
import org.fourz.rvnkcore.util.log.LogManager;
import org.bukkit.plugin.Plugin;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Abstract base repository providing common database operations for all entity types.
 * 
 * This class implements the Repository pattern with async operations using CompletableFuture
 * to prevent blocking the main thread. All database operations include proper error handling
 * and resource cleanup.
 * 
 * @param <T> The entity type
 * @param <ID> The primary key type
 * @since 1.0.0
 */
public abstract class BaseRepository<T, ID> {
    
    protected final ConnectionProvider connectionProvider;
    protected final QueryBuilder queryBuilder;
    protected final LogManager logger;
    protected final String tableName;
    protected final Class<T> entityType;
    
    /**
     * Constructor for BaseRepository.
     * 
     * @param connectionProvider The database connection provider
     * @param queryBuilder The query builder for database operations
     * @param tableName The name of the database table
     * @param entityType The class of the entity type
     * @param plugin The plugin instance for logging
     */
    protected BaseRepository(ConnectionProvider connectionProvider, 
                           QueryBuilder queryBuilder, 
                           String tableName,
                           Class<T> entityType,
                           Plugin plugin) {
        this.connectionProvider = connectionProvider;
        this.queryBuilder = queryBuilder;
        this.tableName = tableName;
        this.entityType = entityType;
        this.logger = LogManager.getInstance(plugin, getClass());
    }
    
    /**
     * Finds an entity by its primary key.
     * 
     * @param id The primary key value
     * @return CompletableFuture containing the entity if found
     */
    public CompletableFuture<Optional<T>> findById(ID id) {
        return CompletableFuture.supplyAsync(() -> {
            // Create a new QueryBuilder instance for thread safety
            QueryBuilder builder = createQueryBuilder();
            String query = builder.select("*")
                .from(tableName)
                .where(getPrimaryKeyColumn() + " = ?")
                .build();
                
            try (Connection conn = connectionProvider.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(query)) {
                
                setPrimaryKeyParameter(stmt, 1, id);
                
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(mapResultSet(rs));
                    }
                    return Optional.empty();
                }
            } catch (SQLException e) {
                logger.error("Failed to find entity by ID: " + id, e);
                throw new DatabaseException("Entity retrieval failed", e);
            }
        });
    }
    
    /**
     * Finds all entities in the table.
     * 
     * @return CompletableFuture containing list of all entities
     */
    public CompletableFuture<List<T>> findAll() {
        return CompletableFuture.supplyAsync(() -> {
            // Create a new QueryBuilder instance for thread safety
            QueryBuilder builder = createQueryBuilder();
            String query = builder.select("*")
                .from(tableName)
                .build();
                
            logger.debug("Executing findAll query: " + query + " on table: " + tableName);
                
            try (Connection conn = connectionProvider.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(query);
                 ResultSet rs = stmt.executeQuery()) {
                
                List<T> results = new ArrayList<>();
                int rowCount = 0;
                while (rs.next()) {
                    rowCount++;
                    try {
                        T entity = mapResultSet(rs);
                        results.add(entity);
                        logger.debug("Mapped row " + rowCount + " for table " + tableName);
                    } catch (SQLException mapEx) {
                        logger.error("Failed to map result set row " + rowCount + " for table: " + tableName, mapEx);
                        throw mapEx;
                    }
                }
                logger.info("FindAll query completed for table " + tableName + " - found " + rowCount + " rows, returning " + results.size() + " entities");
                return results;
            } catch (SQLException e) {
                logger.error("Failed to find all entities from table: " + tableName, e);
                throw new DatabaseException("Entity list retrieval failed", e);
            }
        });
    }
    
    /**
     * Finds a page of entities using LIMIT/OFFSET.
     *
     * @param limit  maximum number of entities to return (clamped to 1–1000)
     * @param offset number of entities to skip
     * @return CompletableFuture containing the page of entities
     */
    public CompletableFuture<List<T>> findPage(int limit, int offset) {
        int safeLimit = Math.max(1, Math.min(limit, 1000));
        int safeOffset = Math.max(0, offset);
        return CompletableFuture.supplyAsync(() -> {
            QueryBuilder builder = createQueryBuilder();
            String query = builder.select("*")
                .from(tableName)
                .build() + " LIMIT " + safeLimit + " OFFSET " + safeOffset;

            try (Connection conn = connectionProvider.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(query);
                 ResultSet rs = stmt.executeQuery()) {

                List<T> results = new ArrayList<>();
                while (rs.next()) {
                    results.add(mapResultSet(rs));
                }
                return results;
            } catch (SQLException e) {
                logger.error("Failed to find page (limit=" + safeLimit + ", offset=" + safeOffset + ") from " + tableName, e);
                throw new DatabaseException("Page query failed", e);
            }
        });
    }

    /**
     * Saves an entity to the database.
     * 
     * @param entity The entity to save
     * @return CompletableFuture containing the saved entity
     */
    public CompletableFuture<T> save(T entity) {
        return CompletableFuture.supplyAsync(() -> {
            ID id = getId(entity);
            
            if (id != null && existsById(id).join()) {
                return update(entity).join();
            } else {
                return insert(entity).join();
            }
        });
    }
    
    /**
     * Inserts a new entity into the database.
     * 
     * @param entity The entity to insert
     * @return CompletableFuture containing the inserted entity
     */
    protected CompletableFuture<T> insert(T entity) {
        return CompletableFuture.supplyAsync(() -> {
            String query = buildInsertQuery();
            
            try (Connection conn = connectionProvider.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(query)) {
                
                setInsertParameters(stmt, entity);
                
                int rowsAffected = stmt.executeUpdate();
                if (rowsAffected == 0) {
                    throw new DatabaseException("Insert failed, no rows affected");
                }
                
                logger.info("Successfully inserted " + getEntityTypeName() + " into " + tableName);
                return entity;
            } catch (SQLException e) {
                logger.error("Failed to insert entity into " + tableName, e);
                throw new DatabaseException("Entity insertion failed", e);
            }
        });
    }
    
    /**
     * Updates an existing entity in the database.
     * 
     * @param entity The entity to update
     * @return CompletableFuture containing the updated entity
     */
    protected CompletableFuture<T> update(T entity) {
        return CompletableFuture.supplyAsync(() -> {
            String query = buildUpdateQuery();
            
            try (Connection conn = connectionProvider.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(query)) {
                
                setUpdateParameters(stmt, entity);
                
                int rowsAffected = stmt.executeUpdate();
                if (rowsAffected == 0) {
                    throw new DatabaseException("Update failed, no rows affected");
                }
                
                // Move database update logging to debug level to reduce verbosity
                logger.debug("Updated " + getEntityTypeName() + " in " + tableName);
                return entity;
            } catch (SQLException e) {
                logger.error("Failed to update entity in " + tableName, e);
                throw new DatabaseException("Entity update failed", e);
            }
        });
    }
    
    /**
     * Deletes an entity by its primary key.
     * 
     * @param id The primary key value
     * @return CompletableFuture that completes when deletion is finished
     */
    public CompletableFuture<Void> deleteById(ID id) {
        return CompletableFuture.runAsync(() -> {
            // Create a new QueryBuilder instance for thread safety
            QueryBuilder builder = createQueryBuilder();
            String query = builder.delete()
                .from(tableName)
                .where(getPrimaryKeyColumn() + " = ?")
                .build();
                
            try (Connection conn = connectionProvider.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(query)) {
                
                setPrimaryKeyParameter(stmt, 1, id);
                
                int rowsAffected = stmt.executeUpdate();
                if (rowsAffected == 0) {
                    logger.warning("Delete operation affected 0 rows for ID: " + id);
                } else {
                    logger.info("Successfully deleted entity with ID: " + id + " from " + tableName);
                }
            } catch (SQLException e) {
                logger.error("Failed to delete entity by ID: " + id, e);
                throw new DatabaseException("Entity deletion failed", e);
            }
        });
    }
    
    /**
     * Checks if an entity exists by its primary key.
     * 
     * @param id The primary key value
     * @return CompletableFuture containing true if the entity exists
     */
    /**
     * Gets the name of the entity type being managed by this repository.
     * Used for logging and error messages.
     * 
     * @return The simple name of the entity class
     */
    protected String getEntityTypeName() {
        return entityType.getSimpleName();
    }

    public CompletableFuture<Boolean> existsById(ID id) {
        return CompletableFuture.supplyAsync(() -> {
            // Create a new QueryBuilder instance for thread safety
            QueryBuilder builder = createQueryBuilder();
            String query = builder.select("COUNT(*)")
                .from(tableName)
                .where(getPrimaryKeyColumn() + " = ?")
                .build();
                
            try (Connection conn = connectionProvider.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(query)) {
                
                setPrimaryKeyParameter(stmt, 1, id);
                
                try (ResultSet rs = stmt.executeQuery()) {
                    return rs.next() && rs.getInt(1) > 0;
                }
            } catch (SQLException e) {
                logger.error("Failed to check existence for ID: " + id, e);
                throw new DatabaseException("Existence check failed", e);
            }
        });
    }
    
    /**
     * Gets the count of all entities in the table.
     * 
     * @return CompletableFuture containing the total count
     */
    public CompletableFuture<Long> count() {
        return CompletableFuture.supplyAsync(() -> {
            // Create a new QueryBuilder instance for thread safety
            QueryBuilder builder = createQueryBuilder();
            String query = builder.select("COUNT(*)")
                .from(tableName)
                .build();
                
            try (Connection conn = connectionProvider.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(query);
                 ResultSet rs = stmt.executeQuery()) {
                
                return rs.next() ? rs.getLong(1) : 0L;
            } catch (SQLException e) {
                logger.error("Failed to count entities in " + tableName, e);
                throw new DatabaseException("Count operation failed", e);
            }
        });
    }
    
    // Abstract methods that must be implemented by subclasses
    
    /**
     * Maps a ResultSet row to an entity object.
     * 
     * @param rs The ResultSet positioned at the current row
     * @return The mapped entity
     * @throws SQLException if mapping fails
     */
    protected abstract T mapResultSet(ResultSet rs) throws SQLException;
    
    /**
     * Gets the primary key column name for this entity.
     * 
     * @return The primary key column name
     */
    protected abstract String getPrimaryKeyColumn();
    
    /**
     * Extracts the primary key value from an entity.
     * 
     * @param entity The entity
     * @return The primary key value
     */
    protected abstract ID getId(T entity);
    
    /**
     * Sets the primary key parameter in a PreparedStatement.
     * 
     * @param stmt The PreparedStatement
     * @param parameterIndex The parameter index
     * @param id The primary key value
     * @throws SQLException if setting the parameter fails
     */
    protected abstract void setPrimaryKeyParameter(PreparedStatement stmt, int parameterIndex, ID id) throws SQLException;
    
    /**
     * Builds the INSERT query for this entity type.
     * 
     * @return The INSERT query string
     */
    protected abstract String buildInsertQuery();
    
    /**
     * Builds the UPDATE query for this entity type.
     * 
     * @return The UPDATE query string
     */
    protected abstract String buildUpdateQuery();
    
    /**
     * Sets the parameters for an INSERT statement.
     * 
     * @param stmt The PreparedStatement
     * @param entity The entity to insert
     * @throws SQLException if setting parameters fails
     */
    protected abstract void setInsertParameters(PreparedStatement stmt, T entity) throws SQLException;
    
    /**
     * Sets the parameters for an UPDATE statement.
     * 
     * @param stmt The PreparedStatement
     * @param entity The entity to update
     * @throws SQLException if setting parameters fails
     */
    protected abstract void setUpdateParameters(PreparedStatement stmt, T entity) throws SQLException;
    
    /**
     * Creates a new QueryBuilder instance for thread-safe query construction.
     *
     * @return A new QueryBuilder instance
     */
    protected QueryBuilder createQueryBuilder() {
        return BasicSQLQueryBuilder.create();
    }

    // ========== Batch Operations ==========

    /**
     * Saves multiple entities in a single batch operation.
     *
     * All entities are saved within a single transaction for atomicity.
     * If any save fails, all changes are rolled back.
     *
     * @param entities The entities to save
     * @return CompletableFuture containing the saved entities
     */
    public CompletableFuture<List<T>> saveAll(List<T> entities) {
        if (entities == null || entities.isEmpty()) {
            return CompletableFuture.completedFuture(List.of());
        }

        return CompletableFuture.supplyAsync(() -> {
            List<T> results = new ArrayList<>(entities.size());

            try (Connection conn = connectionProvider.getConnection()) {
                boolean originalAutoCommit = conn.getAutoCommit();
                conn.setAutoCommit(false);

                try {
                    for (T entity : entities) {
                        ID id = getId(entity);
                        if (id != null && existsByIdSync(conn, id)) {
                            updateSync(conn, entity);
                        } else {
                            insertSync(conn, entity);
                        }
                        results.add(entity);
                    }
                    conn.commit();
                    logger.info("Batch saved " + results.size() + " entities to " + tableName);
                    return results;
                } catch (SQLException e) {
                    conn.rollback();
                    throw e;
                } finally {
                    conn.setAutoCommit(originalAutoCommit);
                }
            } catch (SQLException e) {
                logger.error("Failed to batch save entities to " + tableName, e);
                throw new DatabaseException("Batch save failed", e);
            }
        });
    }

    /**
     * Deletes multiple entities by their IDs in a single batch operation.
     *
     * All deletions occur within a single transaction for atomicity.
     *
     * @param ids The IDs of entities to delete
     * @return CompletableFuture containing the number of deleted entities
     */
    public CompletableFuture<Integer> deleteAllById(List<ID> ids) {
        if (ids == null || ids.isEmpty()) {
            return CompletableFuture.completedFuture(0);
        }

        return CompletableFuture.supplyAsync(() -> {
            QueryBuilder builder = createQueryBuilder();
            String query = builder.delete()
                .from(tableName)
                .where(getPrimaryKeyColumn() + " = ?")
                .build();

            try (Connection conn = connectionProvider.getConnection()) {
                boolean originalAutoCommit = conn.getAutoCommit();
                conn.setAutoCommit(false);

                try (PreparedStatement stmt = conn.prepareStatement(query)) {
                    int totalDeleted = 0;

                    for (ID id : ids) {
                        setPrimaryKeyParameter(stmt, 1, id);
                        totalDeleted += stmt.executeUpdate();
                        stmt.clearParameters();
                    }

                    conn.commit();
                    logger.info("Batch deleted " + totalDeleted + " entities from " + tableName);
                    return totalDeleted;
                } catch (SQLException e) {
                    conn.rollback();
                    throw e;
                } finally {
                    conn.setAutoCommit(originalAutoCommit);
                }
            } catch (SQLException e) {
                logger.error("Failed to batch delete entities from " + tableName, e);
                throw new DatabaseException("Batch delete failed", e);
            }
        });
    }

    /**
     * Inserts multiple entities using JDBC batch operations.
     *
     * More efficient than saveAll for pure inserts when you know entities are new.
     *
     * @param entities The entities to insert
     * @return CompletableFuture containing the number of inserted entities
     */
    public CompletableFuture<Integer> insertAll(List<T> entities) {
        if (entities == null || entities.isEmpty()) {
            return CompletableFuture.completedFuture(0);
        }

        return CompletableFuture.supplyAsync(() -> {
            String query = buildInsertQuery();

            try (Connection conn = connectionProvider.getConnection()) {
                boolean originalAutoCommit = conn.getAutoCommit();
                conn.setAutoCommit(false);

                try (PreparedStatement stmt = conn.prepareStatement(query)) {
                    for (T entity : entities) {
                        setInsertParameters(stmt, entity);
                        stmt.addBatch();
                    }

                    int[] results = stmt.executeBatch();
                    conn.commit();

                    int totalInserted = 0;
                    for (int result : results) {
                        if (result > 0) totalInserted++;
                    }

                    logger.info("Batch inserted " + totalInserted + " entities to " + tableName);
                    return totalInserted;
                } catch (SQLException e) {
                    conn.rollback();
                    throw e;
                } finally {
                    conn.setAutoCommit(originalAutoCommit);
                }
            } catch (SQLException e) {
                logger.error("Failed to batch insert entities to " + tableName, e);
                throw new DatabaseException("Batch insert failed", e);
            }
        });
    }

    // Helper methods for synchronous operations within transactions

    private boolean existsByIdSync(Connection conn, ID id) throws SQLException {
        QueryBuilder builder = createQueryBuilder();
        String query = builder.select("COUNT(*)")
            .from(tableName)
            .where(getPrimaryKeyColumn() + " = ?")
            .build();

        try (PreparedStatement stmt = conn.prepareStatement(query)) {
            setPrimaryKeyParameter(stmt, 1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

    private void insertSync(Connection conn, T entity) throws SQLException {
        String query = buildInsertQuery();
        try (PreparedStatement stmt = conn.prepareStatement(query)) {
            setInsertParameters(stmt, entity);
            stmt.executeUpdate();
        }
    }

    private void updateSync(Connection conn, T entity) throws SQLException {
        String query = buildUpdateQuery();
        try (PreparedStatement stmt = conn.prepareStatement(query)) {
            setUpdateParameters(stmt, entity);
            stmt.executeUpdate();
        }
    }
}
