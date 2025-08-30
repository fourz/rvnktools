# RVNKCore Player & PlayerWorld Testing Checklist

**Date**: August 9, 2025  
**Status**: Testing Framework - Ready for Implementation  
**Target**: Comprehensive validation of player service components

---

## Test Environment Setup

### Prerequisites

- [x] Development server with RVNKTools installed
- [x] SQLite database access
- [x] Multiverse-Core plugin for world management
- [x] Test worlds: `world`, `world_nether`, `world_the_end`, `test_world`
- [/] Multiple test player accounts

### Test Data Preparation

- [x] Clean database state for baseline testing
- [x] Seed data for existing player scenarios
- [x] Multiple world configurations
- [ ] Permission group test cases

---

## Core Player Service Testing

### PlayerService Interface Tests

#### Basic CRUD Operations

- [x] **Test: Create New Player**
  - Input: New player UUID, name "TestPlayer1"
  - Expected: Player record created with default values
  - Validate: First join timestamp, initial playtime = 0

- [ ] **Test: Retrieve Player by UUID**
  - Input: Existing player UUID
  - Expected: Complete player data returned
  - Validate: All fields populated correctly

- [ ] **Test: Retrieve Player by Name**
  - Input: Current player name
  - Expected: Player data returned
  - Validate: Case sensitivity, exact match behavior

- [ ] **Test: Update Player Location**
  - Input: Player UUID, world name, coordinates
  - Expected: Location updated successfully
  - Validate: World name stored correctly, timestamp updated

- [ ] **Test: Update Player Name**
  - Input: Player UUID, new name
  - Expected: Name updated, history maintained
  - Validate: Previous names preserved, current name updated

- [ ] **Test: Update Player Groups**
  - Input: Player UUID, primary group, group list
  - Expected: Permission groups updated
  - Validate: Primary group set, all groups stored

#### Error Handling Tests

- [ ] **Test: Invalid Player UUID**
  - Input: Non-existent UUID
  - Expected: Empty Optional returned
  - Validate: No exceptions thrown, graceful handling

- [ ] **Test: Null Input Parameters**
  - Input: Null UUID or names
  - Expected: IllegalArgumentException or appropriate error
  - Validate: Error messages descriptive

- [ ] **Test: Database Connection Failure**
  - Setup: Simulate database unavailability
  - Expected: ServiceException with appropriate message
  - Validate: Connection recovery when database returns

### Async Operation Tests

- [ ] **Test: Concurrent Player Updates**
  - Setup: Multiple threads updating same player
  - Expected: All operations complete without corruption
  - Validate: Data consistency, no race conditions

- [ ] **Test: CompletableFuture Chain Handling**
  - Setup: Chain multiple operations (get → update → save)
  - Expected: All operations execute in order
  - Validate: Exception propagation through chain

---

## PlayerWorld Service Testing

### Location Tracking Tests

#### Basic Location Operations

- [ ] **Test: First World Visit**
  - Input: Player visits new world for first time
  - Expected: New PlayerWorldData record created
  - Validate: Visit count = 1, initial timestamps set

- [ ] **Test: Return World Visit**
  - Input: Player returns to previously visited world
  - Expected: Visit count incremented, timestamps updated
  - Validate: Last location preserved, playtime accumulated

- [ ] **Test: Get Last Known Location**
  - Input: Player UUID, world name
  - Expected: Most recent location data returned
  - Validate: Coordinates, yaw, pitch accuracy

- [ ] **Test: Location Rate Limiting**
  - Input: Multiple location updates within 30 seconds
  - Expected: Only first update persisted to database
  - Validate: Rate limiting prevents excessive DB writes

#### Session Tracking Tests

- [ ] **Test: Player Join Session**
  - Input: Player joins server, enters world
  - Expected: Session tracking started
  - Validate: Join timestamp recorded, session active

- [ ] **Test: Player Quit Session**
  - Input: Player quits server
  - Expected: Session ended, playtime calculated
  - Validate: Duration calculation accurate, total playtime updated

- [ ] **Test: World Change Tracking**
  - Input: Player teleports between worlds
  - Expected: World change event recorded
  - Validate: From/to worlds logged, transition timestamp

#### Multi-World Scenarios

- [ ] **Test: Multiple World Visits**
  - Input: Player visits 5 different worlds
  - Expected: Separate tracking for each world
  - Validate: Independent visit counts, unique locations

- [ ] **Test: World History Retrieval**
  - Input: Request all player world data
  - Expected: List of all visited worlds returned
  - Validate: Sorted by last visit, complete data sets

- [ ] **Test: Previous World Detection**
  - Input: Player in world A, check previous world
  - Expected: Most recent non-current world returned
  - Validate: Correct world identification, proper filtering

---

## WorldSwap Command Integration Testing

### Command Execution Tests

#### First-Time Visit Scenarios

- [ ] **Test: First Visit to Existing World**
  - Command: `/worldswap test_world`
  - Expected: Teleport to world spawn
  - Validate: Location = spawn coordinates, welcome message

- [ ] **Test: First Visit Invalid World**
  - Command: `/worldswap nonexistent_world`
  - Expected: Error message, no teleportation
  - Validate: "World does not exist" message

#### Return Visit Scenarios

- [ ] **Test: Return to Previously Visited World**
  - Setup: Player has location data in target world
  - Command: `/worldswap target_world`
  - Expected: Teleport to last known location
  - Validate: Exact coordinates restored, visit count message

- [ ] **Test: Multiple World Swaps**
  - Commands: `/worldswap world_nether`, `/worldswap world`, `/worldswap world_nether`
  - Expected: Each teleport to appropriate location
  - Validate: Location persistence across multiple swaps

#### Permission and Validation Tests

- [ ] **Test: Same World Check**
  - Setup: Player in world "survival"
  - Command: `/worldswap survival`
  - Expected: "Already in world" message
  - Validate: No teleportation occurs

- [ ] **Test: Multiverse Integration**
  - Setup: World with Multiverse permissions
  - Command: `/worldswap restricted_world`
  - Expected: Permission check via Multiverse
  - Validate: Access granted/denied appropriately

### Command Framework Integration

- [ ] **Test: Legacy Command Path**
  - Command: `/worldswap target_world`
  - Expected: Deprecation warning + functional teleport
  - Validate: Warning shown to operators only

- [ ] **Test: New Command Path**
  - Command: `/teleport worldswap target_world`
  - Expected: Clean execution without warnings
  - Validate: Same functionality as legacy path

- [ ] **Test: Tab Completion**
  - Input: `/worldswap <TAB>`
  - Expected: Available world names suggested
  - Validate: Only accessible worlds shown

---

## Database Integration Testing

### SQLite Specific Tests

- [ ] **Test: Schema Auto-Creation**
  - Setup: Fresh database file
  - Expected: All required tables created automatically
  - Validate: Correct column types, indexes, constraints

- [ ] **Test: Connection Pooling**
  - Setup: Multiple concurrent database operations
  - Expected: Connections managed efficiently
  - Validate: No connection leaks, proper cleanup

- [ ] **Test: Transaction Handling**
  - Setup: Multi-step operation (player + world data update)
  - Expected: Atomic operation completion
  - Validate: All-or-nothing persistence, rollback on failure

### Data Integrity Tests

- [ ] **Test: Foreign Key Constraints**
  - Setup: PlayerWorldData references non-existent player
  - Expected: Constraint violation handled gracefully
  - Validate: Error messages clear, data consistency maintained

- [ ] **Test: Timestamp Accuracy**
  - Setup: Multiple timestamp-dependent operations
  - Expected: Accurate timestamp storage and retrieval
  - Validate: Timezone handling, precision maintenance

---

## REST API Testing

### Player Endpoint Tests

- [ ] **Test: GET /api/v1/players**
  - Expected: Paginated list of all players
  - Validate: JSON format, pagination parameters, complete data

- [ ] **Test: GET /api/v1/players/{uuid}**
  - Input: Valid player UUID
  - Expected: Complete player data response
  - Validate: All fields present, proper JSON structure

- [ ] **Test: GET /api/v1/player/name/{name}**
  - Input: Player name
  - Expected: Player data for matching name
  - Validate: Case sensitivity handling, exact match

- [ ] **Test: GET /api/v1/player/name/{name}/history**
  - Input: Player name
  - Expected: Player name history data
  - Validate: Complete history, current name identification

- [ ] **Test: PUT /api/v1/players/{uuid}/location**
  - Input: Location update JSON
  - Expected: Location updated successfully
  - Validate: Async processing, response confirmation

- [ ] **Test: PUT /api/v1/players/{uuid}/groups**
  - Input: Group update JSON
  - Expected: Permission groups updated
  - Validate: Primary group handling, group list processing

### Authentication & Security Tests

- [ ] **Test: API Key Authentication**
  - Setup: Request without valid API key
  - Expected: 401 Unauthorized response
  - Validate: Proper HTTP status codes, error messages

- [ ] **Test: Rate Limiting**
  - Setup: Rapid succession of API requests
  - Expected: Rate limiting enforced
  - Validate: 429 Too Many Requests after limit

### Error Response Tests

- [ ] **Test: Invalid UUID Format**
  - Input: Malformed UUID in URL
  - Expected: 400 Bad Request with descriptive error
  - Validate: JSON error response format

- [ ] **Test: Player Not Found**
  - Input: Valid but non-existent UUID
  - Expected: 404 Not Found response
  - Validate: Appropriate HTTP status, clear error message

---

## Performance Testing

### Database Performance

- [ ] **Test: Location Update Rate Limiting**
  - Setup: Rapid location updates for same player
  - Expected: Only one update per 30-second window persisted
  - Validate: Rate limiting effective, memory usage stable

- [ ] **Test: Concurrent Player Operations**
  - Setup: 50 simultaneous player data operations
  - Expected: All operations complete within reasonable time
  - Validate: No deadlocks, consistent response times

- [ ] **Test: Large Dataset Queries**
  - Setup: Database with 1000+ player records
  - Expected: Query performance remains acceptable
  - Validate: Response times under 500ms for typical operations

### Memory Usage Tests

- [ ] **Test: Session Data Cleanup**
  - Setup: Multiple player sessions started and ended
  - Expected: Session data properly cleaned up
  - Validate: No memory leaks in session tracking

- [ ] **Test: Rate Limiting Cache Management**
  - Setup: High-frequency location updates
  - Expected: Rate limiting cache bounded
  - Validate: Cache size doesn't grow indefinitely

---

## Integration Testing

### Cross-Service Integration

- [ ] **Test: PlayerService + PlayerWorldService**
  - Setup: Player data update affecting world-specific data
  - Expected: Both services updated consistently
  - Validate: Data synchronization, no orphaned records

- [ ] **Test: Command + Service Integration**
  - Setup: WorldSwap command execution
  - Expected: Services called appropriately, data persisted
  - Validate: End-to-end functionality, error propagation

### Plugin Integration

- [ ] **Test: Multiverse-Core Integration**
  - Setup: Multiverse worlds with specific configurations
  - Expected: Proper world validation and permission checks
  - Validate: Spawn location retrieval, access control

- [ ] **Test: LuckPerms Integration**
  - Setup: Player with LuckPerms group assignments
  - Expected: Group data synchronized with RVNKCore
  - Validate: Group updates reflected in both systems

---

## Test Execution Strategy

### Automated Testing

1. **Unit Tests**: Individual method and class testing
2. **Integration Tests**: Service layer integration validation
3. **End-to-End Tests**: Complete command workflow testing
4. **Performance Tests**: Load and stress testing scenarios

### Manual Testing

1. **User Experience**: Command usability and message clarity
2. **Error Scenarios**: Edge case and failure condition handling
3. **Admin Features**: Operator commands and diagnostic information

### Continuous Validation

1. **Pre-commit Testing**: Core functionality validation
2. **Build Pipeline**: Automated test execution
3. **Deployment Testing**: Production environment validation

---

## Success Criteria

### Functional Requirements

- [ ] All CRUD operations work correctly for Player and PlayerWorld data
- [ ] WorldSwap command provides expected teleportation behavior
- [ ] Rate limiting prevents database overload
- [ ] Session tracking accurately calculates playtime
- [ ] REST API provides complete data access

### Non-Functional Requirements

- [ ] Database operations complete within 500ms under normal load
- [ ] Memory usage remains stable during extended operation
- [ ] Error handling provides meaningful feedback to users
- [ ] API responses follow consistent JSON format
- [ ] Documentation accurately reflects implemented behavior

### Reliability Requirements

- [ ] System handles player disconnections gracefully
- [ ] Database connection failures are recovered automatically
- [ ] Concurrent operations maintain data consistency
- [ ] Long-running operations don't block main thread
- [ ] System remains stable under high player activity

---

This comprehensive testing checklist ensures all aspects of the player and player world functionality are thoroughly validated before considering the implementation complete.
