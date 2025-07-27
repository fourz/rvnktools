package org.fourz.rvnkcore.database.query;

/**
 * Interface for building SQL queries in a database-agnostic way.
 * 
 * This interface provides a fluent API for constructing SQL queries
 * without needing to know the specific SQL dialect of the underlying
 * database. Implementations handle dialect-specific syntax differences.
 */
public interface QueryBuilder {
    
    // SELECT operations
    
    /**
     * Starts a SELECT query with the specified columns.
     * 
     * @param columns The columns to select, or "*" for all columns
     * @return This QueryBuilder instance for method chaining
     */
    QueryBuilder select(String... columns);
    
    /**
     * Specifies the table to select from.
     * 
     * @param table The table name
     * @return This QueryBuilder instance for method chaining
     */
    QueryBuilder from(String table);
    
    /**
     * Adds a WHERE clause with parameters.
     * 
     * @param condition The WHERE condition with ? placeholders for parameters
     * @param params The parameters to substitute into the condition
     * @return This QueryBuilder instance for method chaining
     */
    QueryBuilder where(String condition, Object... params);
    
    /**
     * Adds an ORDER BY clause.
     * 
     * @param column The column to order by
     * @param ascending true for ASC, false for DESC
     * @return This QueryBuilder instance for method chaining
     */
    QueryBuilder orderBy(String column, boolean ascending);
    
    /**
     * Adds a LIMIT clause.
     * 
     * @param limit The maximum number of rows to return
     * @return This QueryBuilder instance for method chaining
     */
    QueryBuilder limit(int limit);
    
    /**
     * Adds a simple JOIN clause.
     * 
     * @param table The table to join
     * @param condition The join condition
     * @return This QueryBuilder instance for method chaining
     */
    QueryBuilder join(String table, String condition);
    
    // INSERT operations
    
    /**
     * Starts an INSERT query for the specified table.
     * 
     * @param table The table to insert into
     * @return This QueryBuilder instance for method chaining
     */
    QueryBuilder insert(String table);
    
    /**
     * Specifies the columns for an INSERT operation.
     * 
     * @param columns The column names
     * @return This QueryBuilder instance for method chaining
     */
    QueryBuilder columns(String... columns);
    
    /**
     * Specifies the values for an INSERT operation.
     * 
     * @param values The values to insert
     * @return This QueryBuilder instance for method chaining
     */
    QueryBuilder values(Object... values);
    
    // UPDATE operations
    
    /**
     * Starts an UPDATE query for the specified table.
     * 
     * @param table The table to update
     * @return This QueryBuilder instance for method chaining
     */
    QueryBuilder update(String table);
    
    /**
     * Sets a column value for an UPDATE operation.
     * 
     * @param column The column name
     * @param value The new value
     * @return This QueryBuilder instance for method chaining
     */
    QueryBuilder set(String column, Object value);
    
    // DELETE operations
    
    /**
     * Starts a DELETE query.
     * Must be followed by from() and typically where().
     * 
     * @return This QueryBuilder instance for method chaining
     */
    QueryBuilder delete();
    
    // Query execution
    
    /**
     * Builds the final SQL query string.
     * 
     * @return The constructed SQL query
     */
    String build();
    
    /**
     * Gets the parameters for the query in the correct order.
     * 
     * @return Array of parameters to be used with the query
     */
    Object[] getParameters();
}
