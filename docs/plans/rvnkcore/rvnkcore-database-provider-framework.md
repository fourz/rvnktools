# RVNKCore Database Provider Framework

*Last Updated: July 23, 2025*

A streamlined database abstraction framework for RVNKCore providing clean separation of concerns, asynchronous operations, and database dialect independence without unnecessary complexity.

## Simplified Architecture Overview

```
┌─────────────────────────┐
│      RVNKCore API       │
│                         │
│  Service Interfaces     │
└────────────┬────────────┘
             │
             ▼
┌─────────────────────────┐
│    Service Layer        │
│                         │
│  Service Implementations│
└────────────┬────────────┘
             │
             ▼
┌─────────────────────────┐     ┌─────────────────────────┐
│   Repository Layer      │────▶│    Database Layer       │
│                         │     │                         │
│  Base Repository        │     │  ConnectionProvider     │
│  Entity Repositories    │     │  QueryBuilder           │
└─────────────────────────┘     │  QueryExecutor          │
             │                  └─────────────────────────┘
             │                               │
             ▼                               ▼
┌─────────────────────────┐     ┌─────────────────────────┐
│      DTO Layer          │     │   Database Drivers      │
│                         │     │                         │
│  Data Transfer Objects  │     │  MySQL Implementation   │
└─────────────────────────┘     │  SQLite Implementation  │
                                └─────────────────────────┘
```

## Core Components

### 1. ConnectionProvider Interface

Simplified connection management without unnecessary abstraction:

```java
public interface ConnectionProvider extends AutoCloseable {
    /**
     * Gets a database connection.
     * For SQLite: returns the single persistent connection
     * For MySQL: gets a connection from the pool
     */
    Connection getConnection() throws SQLException;
    
    /**
     * Validates the connection is still alive.
     */
    boolean isValid();
    
    /**
     * Closes all connections and cleans up resources.
     */
    @Override
    void close();
}
```

### 2. QueryBuilder Interface

Simple query building without over-engineering:

```java
public interface QueryBuilder {
    // Basic query operations
    QueryBuilder select(String... columns);
    QueryBuilder from(String table);
    QueryBuilder where(String condition, Object... params);
    QueryBuilder orderBy(String column, boolean ascending);
    QueryBuilder limit(int limit);
    
    // Simple joins
    QueryBuilder join(String table, String condition);
    
    // Insert operations
    QueryBuilder insert(String table);
    QueryBuilder columns(String... columns);
    QueryBuilder values(Object... values);
    
    // Update operations
    QueryBuilder update(String table);
    QueryBuilder set(String column, Object value);
    
    // Delete operations
    QueryBuilder delete();
    
    // Build the query
    String build();
    Object[] getParameters();
}
```

### 3. QueryExecutor Interface

Focused on essential operations only:

```java
public interface QueryExecutor {
    /**
     * Execute a SELECT query returning a single result.
     */
    <T> CompletableFuture<Optional<T>> executeQuery(
        QueryBuilder query, Class<T> dtoClass);
    
    /**
     * Execute a SELECT query returning multiple results.
     */
    <T> CompletableFuture<List<T>> executeQueryList(
        QueryBuilder query, Class<T> dtoClass);
    
    /**
     * Execute an INSERT and return generated ID if applicable.
     */
    CompletableFuture<Integer> executeInsert(QueryBuilder query);
    
    /**
     * Execute an UPDATE/DELETE and return affected rows.
     */
    CompletableFuture<Integer> executeUpdate(QueryBuilder query);
}
```

### 4. BaseRepository Pattern

Simplified repository base class without excessive abstraction:

```java
public abstract class BaseRepository<T, ID> {
    protected final ConnectionProvider connectionProvider;
    protected final QueryBuilder queryBuilder;
    protected final QueryExecutor queryExecutor;
    protected final LogManager logger;
    protected final Class<T> entityClass;
    
    protected BaseRepository(ConnectionProvider provider, 
                           QueryBuilder builder,
                           QueryExecutor executor,
                           LogManager logger,
                           Class<T> entityClass) {
        this.connectionProvider = provider;
        this.queryBuilder = builder;
        this.queryExecutor = executor;
        this.logger = logger;
        this.entityClass = entityClass;
    }
    
    /**
     * Find entity by ID.
     */
    public CompletableFuture<Optional<T>> findById(ID id) {
        return queryExecutor.executeQuery(
            queryBuilder.select("*")
                       .from(getTableName())
                       .where(getIdColumn() + " = ?", id),
            entityClass
        );
    }
    
    /**
     * Save entity (insert or update based on ID presence).
     */
    public abstract CompletableFuture<T> save(T entity);
    
    /**
     * Delete entity by ID.
     */
    public CompletableFuture<Boolean> deleteById(ID id) {
        return queryExecutor.executeUpdate(
            queryBuilder.delete()
                       .from(getTableName())
                       .where(getIdColumn() + " = ?", id)
        ).thenApply(rows -> rows > 0);
    }
    
    protected abstract String getTableName();
    protected abstract String getIdColumn();
}
```

### 5. Simple DTO Pattern

Data Transfer Objects without unnecessary complexity:

```java
public class PlayerDTO {
    private UUID id;
    private String username;
    private Timestamp firstJoin;
    private Timestamp lastSeen;
    private long playtimeSeconds;
    private boolean banned;
    
    // Simple getters and setters
    // No complex mapping logic - keep it simple
}
```

## Implementation Example

### 1. Connection Provider Implementation

```java
public class SQLiteConnectionProvider implements ConnectionProvider {
    private final String databasePath;
    private final LogManager logger;
    private Connection connection;
    
    public SQLiteConnectionProvider(String databasePath, LogManager logger) {
        this.databasePath = databasePath;
        this.logger = logger;
    }
    
    @Override
    public synchronized Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            connection = DriverManager.getConnection("jdbc:sqlite:" + databasePath);
            // Enable WAL mode for better concurrency
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("PRAGMA journal_mode=WAL");
            }
        }
        return connection;
    }
    
    @Override
    public boolean isValid() {
        try {
            return connection != null && !connection.isClosed() && connection.isValid(1);
        } catch (SQLException e) {
            return false;
        }
    }
    
    @Override
    public void close() {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException e) {
                logger.error("Failed to close SQLite connection", e);
            }
        }
    }
}

public class MySQLConnectionProvider implements ConnectionProvider {
    private final HikariDataSource dataSource;
    
    public MySQLConnectionProvider(String host, int port, String database,
                                 String username, String password, int poolSize) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(String.format("jdbc:mysql://%s:%d/%s", host, port, database));
        config.setUsername(username);
        config.setPassword(password);
        config.setMaximumPoolSize(poolSize);
        config.setMinimumIdle(Math.min(2, poolSize));
        config.setConnectionTimeout(30000);
        config.setIdleTimeout(600000);
        config.setMaxLifetime(1800000);
        
        this.dataSource = new HikariDataSource(config);
    }
    
    @Override
    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }
    
    @Override
    public boolean isValid() {
        return dataSource != null && !dataSource.isClosed();
    }
    
    @Override
    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }
}
```

### 2. Repository Implementation

```java
public class PlayerRepository extends BaseRepository<PlayerDTO, UUID> {
    
    public PlayerRepository(ConnectionProvider provider, 
                          QueryBuilder builder,
                          QueryExecutor executor,
                          LogManager logger) {
        super(provider, builder, executor, logger, PlayerDTO.class);
    }
    
    @Override
    protected String getTableName() {
        return "players";
    }
    
    @Override
    protected String getIdColumn() {
        return "id";
    }
    
    @Override
    public CompletableFuture<PlayerDTO> save(PlayerDTO player) {
        if (player.getId() == null) {
            player.setId(UUID.randomUUID());
        }
        
        // Check if player exists
        return findById(player.getId()).thenCompose(existing -> {
            if (existing.isPresent()) {
                // Update existing player
                return queryExecutor.executeUpdate(
                    queryBuilder.update(getTableName())
                        .set("username", player.getUsername())
                        .set("last_seen", player.getLastSeen())
                        .set("playtime_seconds", player.getPlaytimeSeconds())
                        .set("is_banned", player.isBanned())
                        .where("id = ?", player.getId())
                ).thenApply(rows -> player);
            } else {
                // Insert new player
                return queryExecutor.executeInsert(
                    queryBuilder.insert(getTableName())
                        .columns("id", "username", "first_join", "last_seen", 
                               "playtime_seconds", "is_banned")
                        .values(player.getId(), player.getUsername(), 
                               player.getFirstJoin(), player.getLastSeen(),
                               player.getPlaytimeSeconds(), player.isBanned())
                ).thenApply(rows -> player);
            }
        });
    }
    
    /**
     * Find player by username.
     */
    public CompletableFuture<Optional<PlayerDTO>> findByUsername(String username) {
        return queryExecutor.executeQuery(
            queryBuilder.select("*")
                       .from(getTableName())
                       .where("username = ?", username),
            PlayerDTO.class
        );
    }
    
    /**
     * Get all online players (last seen within 5 minutes).
     */
    public CompletableFuture<List<PlayerDTO>> getOnlinePlayers() {
        Timestamp fiveMinutesAgo = new Timestamp(
            System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(5));
        
        return queryExecutor.executeQueryList(
            queryBuilder.select("*")
                       .from(getTableName())
                       .where("last_seen > ?", fiveMinutesAgo)
                       .orderBy("username", true),
            PlayerDTO.class
        );
    }
}
```

### 3. Service Implementation

```java
public class PlayerService implements IPlayerService {
    private final PlayerRepository repository;
    private final LogManager logger;
    
    public PlayerService(PlayerRepository repository, LogManager logger) {
        this.repository = repository;
        this.logger = logger;
    }
    
    @Override
    public CompletableFuture<PlayerDTO> getPlayer(UUID id) {
        return repository.findById(id)
            .thenApply(opt -> opt.orElse(null));
    }
    
    @Override
    public CompletableFuture<PlayerDTO> updatePlayer(PlayerDTO player) {
        Objects.requireNonNull(player, "Player cannot be null");
        Objects.requireNonNull(player.getId(), "Player ID cannot be null");
        
        return repository.save(player)
            .exceptionally(ex -> {
                logger.error("Failed to update player: " + player.getId(), ex);
                throw new ServiceException("Failed to update player", ex);
            });
    }
    
    @Override
    public CompletableFuture<List<PlayerDTO>> getOnlinePlayers() {
        return repository.getOnlinePlayers()
            .exceptionally(ex -> {
                logger.error("Failed to get online players", ex);
                return Collections.emptyList();
            });
    }
}
```

## Schema Management

Simple schema creation without complex migration systems:

```java
public class SchemaManager {
    private final ConnectionProvider connectionProvider;
    private final LogManager logger;
    private final boolean isMySql;
    
    public CompletableFuture<Boolean> createSchema() {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = connectionProvider.getConnection()) {
                createTables(conn);
                createIndexes(conn);
                return true;
            } catch (SQLException e) {
                logger.error("Failed to create schema", e);
                return false;
            }
        });
    }
    
    private void createTables(Connection conn) throws SQLException {
        // API Version table
        executeStatement(conn, 
            "CREATE TABLE IF NOT EXISTS api_version (" +
            "id INTEGER PRIMARY KEY " + getAutoIncrement() + "," +
            "component VARCHAR(50) NOT NULL," +
            "version VARCHAR(20) NOT NULL," +
            "installed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP," +
            "UNIQUE (component, version)" +
            ")");
        
        // Players table
        executeStatement(conn,
            "CREATE TABLE IF NOT EXISTS players (" +
            "id VARCHAR(36) PRIMARY KEY," +
            "username VARCHAR(16) NOT NULL," +
            "first_join TIMESTAMP NOT NULL," +
            "last_seen TIMESTAMP NOT NULL," +
            "playtime_seconds BIGINT DEFAULT 0," +
            "is_banned BOOLEAN DEFAULT FALSE," +
            "UNIQUE (username)" +
            ")");
        
        // Additional tables...
    }
    
    private String getAutoIncrement() {
        return isMySql ? "AUTO_INCREMENT" : "AUTOINCREMENT";
    }
}
```

## Configuration

Simple configuration structure:

```yaml
database:
  type: sqlite  # or mysql
  
  sqlite:
    file: rvnkcore.db
    
  mysql:
    host: localhost
    port: 3306
    database: rvnkcore
    username: minecraft
    password: secure_password
    pool-size: 10
    
  table-prefix: rvnk_
  auto-create-schema: true
```

## Key Simplifications

### 1. Removed Unnecessary Complexity

- **No Service Layer abstractions**: Services interact directly with repositories
- **No complex DTO mapping**: Simple getter/setter objects
- **No transaction wrapper classes**: Use standard JDBC transactions when needed
- **No health monitoring service**: Simple connection validation in providers
- **No backup service**: Leave to external tools or server admins
- **No query service layer**: Repositories handle all queries directly
- **No schema migration system**: Simple create-if-not-exists approach

### 2. Focused on Essential Features

- Connection management (pooling for MySQL, single connection for SQLite)
- Query building abstraction for dialect differences
- Asynchronous operations to prevent blocking
- Simple repository pattern for data access
- Basic error handling and logging

### 3. Direct Integration Points

Services can directly use repositories without additional layers:

```java
public class AnnouncementService implements IAnnouncementService {
    private final AnnouncementRepository repository;
    
    @Override
    public CompletableFuture<AnnouncementDTO> createAnnouncement(AnnouncementDTO announcement) {
        // Direct repository usage - no additional abstraction needed
        return repository.save(announcement);
    }
}
```

## Best Practices

1. **Keep it simple**: Don't add abstraction until it's needed
2. **Use CompletableFuture**: For all database operations
3. **Handle errors gracefully**: Log and return sensible defaults
4. **Close resources properly**: Use try-with-resources
5. **Validate inputs**: In service layer before database operations
6. **Cache strategically**: Only where performance requires it
7. **Test thoroughly**: Focus on integration tests over unit tests

## Conclusion

This simplified framework provides all the essential database functionality needed for RVNKCore without unnecessary complexity. It maintains clean separation of concerns while being straightforward to understand, implement, and maintain. The framework can be extended as needed but starts with only the essential components required for a functional database layer.
