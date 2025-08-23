# RVNKCore PlayerWorld Implementation - Copilot Instructions

**Project Context**: 08-rvnkcore-playerworld-implementation  
**Phase**: Implementation Phase  
**Integration**: RVNKCore Architecture Patterns

## Project-Specific Guidelines

When working on this PlayerWorld implementation project, follow these specific patterns and requirements in addition to the general RVNK ecosystem guidelines:

### Database Schema Patterns

**Table Naming and Structure:**
- Use `rvnk_player_world_data` table with composite primary key `(player_id, world_name)`
- Maintain separation between global player data (`rvnk_players`) and world-specific data
- Always include proper foreign key constraints with cascade delete
- Create strategic indexes for query performance from day one

**Schema Evolution:**
- Enhanced `rvnk_players` table removes location fields (moved to world-specific table)
- Add `current_world` field to track player's active world
- Preserve `total_playtime_seconds` as aggregate across all worlds
- Use `metadata` TEXT field for extensible world-specific data

### Service Architecture Patterns

**PlayerWorldService Implementation:**
- Implement comprehensive world-specific player tracking
- Use rate limiting (30-second intervals) for location updates
- Provide both global player data access and world-specific operations
- Cache frequently accessed world data for performance

**Method Naming Conventions:**
```java
// World-specific operations
CompletableFuture<Optional<PlayerWorldDataDTO>> getLastKnownLocation(UUID playerId, String worldName);
CompletableFuture<Void> updatePlayerLocation(UUID playerId, String worldName, double x, double y, double z, float yaw, float pitch, String biome);
CompletableFuture<Void> recordWorldChange(UUID playerId, String fromWorld, String toWorld, ...);

// Analytics and reporting
CompletableFuture<List<PlayerWorldDataDTO>> getPlayerWorldHistory(UUID playerId);
CompletableFuture<List<String>> getVisitedWorlds(UUID playerId);
CompletableFuture<Long> getWorldPlaytime(UUID playerId, String worldName);
```

### Repository Patterns

**PlayerWorldDataRepository:**
- Extend repository patterns for composite key operations
- Use `findByPlayerAndWorld(UUID, String)` as primary lookup method
- Implement batch operations for efficiency
- Create specialized methods for analytics queries

**Query Optimization:**
- Always use prepared statements with proper parameter binding
- Leverage composite indexes for `(player_id, world_name)` and `(player_id, last_visit)`
- Implement pagination for large result sets
- Use connection pooling through RVNKCore's ConnectionProvider

### Event Integration Patterns

**Bukkit Event Handlers:**
```java
@EventHandler
public void onPlayerJoin(PlayerJoinEvent event) {
    // Update global player data (current_world, last_seen)
    // Record world visit with location tracking
    // Rate-limited to prevent spam
}

@EventHandler(priority = EventPriority.MONITOR)
public void onPlayerMove(PlayerMoveEvent event) {
    // Rate-limited location updates (30-second intervals)
    // Only update if location significantly changed
}

@EventHandler
public void onWorldChange(PlayerChangedWorldEvent event) {
    // Record world transition with from/to tracking
    // Update visit counts and location data immediately
}
```

### DTO and Model Patterns

**PlayerWorldDataDTO Structure:**
- Include all location data (x, y, z, yaw, pitch, biome)
- Track visit statistics (firstVisit, lastVisit, visitCount)
- Include playtime and death count per world
- Use Map<String, Object> metadata for extensible data

**Builder Pattern Usage:**
```java
PlayerWorldDataDTO worldData = new PlayerWorldDataDTO()
    .setPlayerId(playerId)
    .setWorldName(worldName)
    .setLastLocation(x, y, z, yaw, pitch)
    .recordVisit();
```

### REST API Patterns

**Endpoint Design:**
- `/api/players/{uuid}/worlds` - All world data for player
- `/api/players/{uuid}/worlds/{world}` - Specific world data
- `/api/worlds/{world}/players` - All players who visited world
- `/api/worlds/{world}/statistics` - World analytics

**Response Model Consistency:**
```java
public class PlayerWorldDataResponse {
    private String playerId;
    private String worldName;
    private LocationData lastLocation;
    private VisitStatistics visitStats;
    private PlaytimeData playtime;
    private Map<String, Object> metadata;
}
```

### Performance Optimization Patterns

**Rate Limiting Implementation:**
- Use in-memory tracking for last update timestamps
- Configurable rate limiting intervals (default: 30 seconds)
- Immediate updates for world changes and critical events
- Batch updates for efficiency during high activity

**Caching Strategy:**
- Cache frequently accessed world data in memory
- Use configurable cache expiration (default: 30 minutes)
- Implement cache invalidation on data updates
- Monitor cache hit rates and adjust sizing

### Error Handling Patterns

**World-Specific Error Handling:**
- Validate world existence before operations
- Handle deleted worlds gracefully with data preservation
- Provide meaningful error messages for API consumers
- Implement fallback behavior for missing world data

**Database Error Recovery:**
- Handle connection failures with retry mechanisms
- Implement transaction rollback for failed operations
- Provide detailed error logging for debugging
- Maintain data consistency during failures

### Testing Patterns

**Repository Testing:**
```java
@Test
public void testFindByPlayerAndWorld() {
    // Given: Player and world data exists
    // When: Querying for specific player/world combination
    // Then: Correct data returned with all fields populated
}

@Test
public void testCompositeKeyOperations() {
    // Test all CRUD operations with composite keys
    // Validate foreign key constraints
    // Ensure cascade delete behavior
}
```

**Service Testing:**
```java
@Test
public void testRateLimitedLocationUpdate() {
    // Given: Recent location update exists
    // When: Attempting update within rate limit window
    // Then: Update is skipped but no error thrown
}
```

### Migration and Compatibility

**Legacy Data Migration:**
- Migrate existing location data from `rvnk_players` to `rvnk_player_world_data`
- Preserve all existing playtime as world-specific data
- Update service interfaces to delegate location operations to PlayerWorldService
- Maintain backward compatibility during transition period

**Compatibility Layers:**
```java
// Maintain existing PlayerService methods during migration
@Deprecated
public CompletableFuture<Void> updatePlayerLocation(UUID playerId, double x, double y, double z) {
    // Delegate to PlayerWorldService with current world
    return playerWorldService.updatePlayerLocation(playerId, getCurrentWorld(playerId), x, y, z, 0, 0, null);
}
```

### Integration with Existing Systems

**ServiceRegistry Integration:**
- Register PlayerWorldService in ServiceRegistry during RVNKCore initialization
- Ensure proper dependency injection for repositories and connection providers
- Maintain service lifecycle management with proper cleanup

**Configuration Management:**
- Support configurable rate limiting intervals
- Allow cache size and expiration tuning
- Enable/disable world tracking features
- Provide performance tuning options

This project represents a foundational enhancement to RVNKCore that enables advanced world management features while maintaining high performance and backward compatibility.
