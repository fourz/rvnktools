package org.fourz.rvnkcore.database.query;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Basic SQL QueryBuilder implementation.
 * 
 * Provides a fluent API for building SQL queries in a database-agnostic way.
 * This implementation supports standard SQL syntax compatible with both
 * SQLite and MySQL databases.
 * 
 * @since 1.0.0
 */
public class BasicSQLQueryBuilder implements QueryBuilder {
    
    private StringBuilder query;
    private List<Object> parameters;
    private QueryType currentType;
    
    /**
     * Enumeration of query types for internal state tracking.
     */
    private enum QueryType {
        SELECT, INSERT, UPDATE, DELETE
    }
    
    /**
     * Constructor for BasicSQLQueryBuilder.
     */
    public BasicSQLQueryBuilder() {
        this.query = new StringBuilder();
        this.parameters = new ArrayList<>();
        this.currentType = null;
    }
    
    @Override
    public QueryBuilder select(String... columns) {
        reset();
        currentType = QueryType.SELECT;
        query.append("SELECT ");
        
        if (columns.length == 0 || (columns.length == 1 && "*".equals(columns[0]))) {
            query.append("*");
        } else {
            query.append(String.join(", ", columns));
        }
        
        return this;
    }
    
    @Override
    public QueryBuilder from(String table) {
        if (currentType != QueryType.SELECT && currentType != QueryType.DELETE) {
            throw new IllegalStateException("FROM clause can only be used with SELECT or DELETE queries");
        }
        query.append(" FROM ").append(table);
        return this;
    }
    
    @Override
    public QueryBuilder where(String condition, Object... params) {
        query.append(" WHERE ").append(condition);
        if (params.length > 0) {
            parameters.addAll(Arrays.asList(params));
        }
        return this;
    }
    
    @Override
    public QueryBuilder orderBy(String column, boolean ascending) {
        query.append(" ORDER BY ").append(column);
        query.append(ascending ? " ASC" : " DESC");
        return this;
    }
    
    @Override
    public QueryBuilder limit(int limit) {
        query.append(" LIMIT ").append(limit);
        return this;
    }
    
    @Override
    public QueryBuilder join(String table, String condition) {
        query.append(" JOIN ").append(table).append(" ON ").append(condition);
        return this;
    }
    
    @Override
    public QueryBuilder insert(String table) {
        reset();
        currentType = QueryType.INSERT;
        query.append("INSERT INTO ").append(table);
        return this;
    }
    
    @Override
    public QueryBuilder columns(String... columns) {
        if (currentType != QueryType.INSERT) {
            throw new IllegalStateException("COLUMNS clause can only be used with INSERT queries");
        }
        query.append(" (").append(String.join(", ", columns)).append(")");
        return this;
    }
    
    @Override
    public QueryBuilder values(Object... values) {
        if (currentType != QueryType.INSERT) {
            throw new IllegalStateException("VALUES clause can only be used with INSERT queries");
        }
        query.append(" VALUES (");
        
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                query.append(", ");
            }
            if ("?".equals(values[i])) {
                query.append("?");
            } else {
                query.append("?");
                parameters.add(values[i]);
            }
        }
        
        query.append(")");
        return this;
    }
    
    @Override
    public QueryBuilder update(String table) {
        reset();
        currentType = QueryType.UPDATE;
        query.append("UPDATE ").append(table);
        return this;
    }
    
    @Override
    public QueryBuilder set(String column, Object value) {
        if (currentType != QueryType.UPDATE) {
            throw new IllegalStateException("SET clause can only be used with UPDATE queries");
        }
        
        if (query.indexOf(" SET ") == -1) {
            query.append(" SET ");
        } else {
            query.append(", ");
        }
        
        query.append(column).append(" = ");
        
        if ("?".equals(value)) {
            query.append("?");
        } else {
            query.append("?");
            parameters.add(value);
        }
        
        return this;
    }
    
    @Override
    public QueryBuilder delete() {
        reset();
        currentType = QueryType.DELETE;
        query.append("DELETE");
        return this;
    }
    
    @Override
    public String build() {
        if (query.length() == 0) {
            throw new IllegalStateException("No query has been constructed");
        }
        return query.toString();
    }
    
    @Override
    public Object[] getParameters() {
        return parameters.toArray();
    }
    
    /**
     * Resets the builder state for constructing a new query.
     */
    private void reset() {
        query.setLength(0);
        parameters.clear();
        currentType = null;
    }
    
    /**
     * Creates a new BasicSQLQueryBuilder instance.
     * 
     * @return A new query builder instance
     */
    public static BasicSQLQueryBuilder create() {
        return new BasicSQLQueryBuilder();
    }
    
    @Override
    public String toString() {
        return "BasicSQLQueryBuilder{" +
                "query='" + query + '\'' +
                ", parameters=" + parameters +
                ", currentType=" + currentType +
                '}';
    }
}
