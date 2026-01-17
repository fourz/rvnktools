---
description: Comprehensive code review for RVNK Java/Maven plugins with ecosystem-specific checks
argument-hint: [PR number, branch, file path, or empty for staged changes]
---

# RVNK Code Review

You are about to perform a comprehensive code review for the Ravenkraft plugin ecosystem. This review focuses on Java/Maven best practices, Minecraft plugin patterns, and RVNK-specific coding standards.

## Scope: $ARGUMENTS

If no arguments provided, review staged changes (`git diff --cached`).

---

## Step 1: Gather Code to Review

Based on the scope provided:

### For PR number:
```bash
gh pr diff <PR_NUMBER>
```

### For branch:
```bash
git diff main...<branch_name>
```

### For file path:
```bash
# Read the specified file(s)
```

### For staged changes (default):
```bash
git diff --cached
```

---

## Step 2: RVNK Coding Standards Review

Review the code against these RVNK-specific standards:

### 2.1 Java 17+ Compliance
- [ ] Uses modern Java features (records, sealed classes, pattern matching)
- [ ] No deprecated APIs
- [ ] Proper use of `var` where appropriate
- [ ] No raw generic types

### 2.2 Naming Conventions
- [ ] Classes: `PascalCase` (e.g., `QuestService`, `PlayerRepository`)
- [ ] Methods/Variables: `camelCase` (e.g., `getPlayerData`, `questName`)
- [ ] Constants: `UPPER_SNAKE_CASE` (e.g., `MAX_RETRIES`, `DEFAULT_TIMEOUT`)
- [ ] Packages: `lowercase` (e.g., `org.fourz.rvnkquests.service`)
- [ ] NO Hungarian notation (no `IService`, `AbstractBase`)

### 2.3 Async Patterns (CompletableFuture)
- [ ] Database operations use `CompletableFuture`
- [ ] I/O operations are async
- [ ] No `.join()` or `.get()` on main thread
- [ ] Proper exception handling with `.exceptionally()` or `.handle()`
- [ ] Uses `runAsync`/`supplyAsync` with appropriate executor

**Example of correct async pattern:**
```java
public CompletableFuture<Player> getPlayer(UUID uuid) {
    return CompletableFuture.supplyAsync(() -> {
        return repository.findById(uuid);
    }).exceptionally(ex -> {
        logger.error("Failed to get player: " + uuid, ex);
        return null;
    });
}
```

### 2.4 Console Command Support
- [ ] Commands support console execution (not player-only unless justified)
- [ ] Uses `CommandSender` instead of `Player` where possible
- [ ] Proper permission checks for console vs player
- [ ] Console-friendly output formatting

**Check for anti-pattern:**
```java
// ❌ BAD - Player only
if (!(sender instanceof Player)) {
    sender.sendMessage("Players only!");
    return;
}

// ✅ GOOD - Console supported
if (args.length < 1 && !(sender instanceof Player)) {
    sender.sendMessage("Usage: /command <player>");
    return;
}
```

---

## Step 3: RVNKCore Integration Review

### 3.1 ServiceRegistry Usage
- [ ] Services registered via `ServiceRegistry.register()`
- [ ] Services retrieved via `ServiceRegistry.get()`
- [ ] No direct instantiation of services (use DI)
- [ ] Proper service lifecycle (init → start → stop)

**Check for pattern:**
```java
// ✅ GOOD
PlayerService playerService = ServiceRegistry.get(PlayerService.class);

// ❌ BAD
PlayerService playerService = new PlayerServiceImpl();
```

### 3.2 Repository Pattern
- [ ] Repositories extend base repository interface
- [ ] CRUD operations follow standard naming (`findById`, `save`, `delete`)
- [ ] Returns `CompletableFuture` for async operations
- [ ] Uses QueryBuilder for complex queries
- [ ] Proper entity ↔ DTO conversion

### 3.3 DTO Usage
- [ ] DTOs used for cross-boundary data transfer
- [ ] DTOs are immutable (records preferred)
- [ ] No entity objects exposed outside service layer
- [ ] Proper `toDTO()` and `fromDTO()` methods

---

## Step 4: Database Pattern Review

### 4.1 Connection Management
- [ ] Uses HikariCP for connection pooling
- [ ] Connections properly closed (try-with-resources)
- [ ] No connection leaks
- [ ] Appropriate pool size configuration

### 4.2 Query Patterns
- [ ] Uses PreparedStatement (no string concatenation)
- [ ] QueryBuilder for complex queries
- [ ] Proper transaction boundaries
- [ ] MySQL/SQLite compatibility maintained

### 4.3 Schema Compliance
- [ ] Table naming: `plugin_entity` (lowercase, underscore)
- [ ] Column naming: `snake_case`
- [ ] Foreign keys properly defined
- [ ] Indexes on frequently queried columns

---

## Step 5: Security Review

### 5.1 Input Validation
- [ ] All user input validated
- [ ] No SQL injection vulnerabilities
- [ ] No command injection
- [ ] Proper permission checks

### 5.2 Sensitive Data
- [ ] No hardcoded credentials
- [ ] Config values for sensitive data
- [ ] Proper logging (no sensitive data logged)

---

## Step 6: Performance Review

### 6.1 Main Thread Safety
- [ ] No blocking I/O on main thread
- [ ] Heavy computations offloaded
- [ ] Bukkit scheduler used appropriately

### 6.2 Resource Management
- [ ] Streams properly closed
- [ ] Collections sized appropriately
- [ ] No memory leaks (especially in event handlers)
- [ ] Caching where appropriate

---

## Step 7: Testing Considerations

- [ ] Unit tests for service methods
- [ ] Integration tests for repository operations
- [ ] Test coverage for edge cases
- [ ] Mock dependencies properly

---

## Output: Code Review Report

Generate a structured review report:

```markdown
# RVNK Code Review Report

**Scope**: [PR/Branch/Files reviewed]
**Date**: [Current date]
**Reviewer**: Claude Code (rvnk-code-review)

## Summary

**Overall Assessment**: [APPROVED / NEEDS CHANGES / BLOCKED]

| Category | Status | Issues |
|----------|--------|--------|
| Java Standards | ✅/⚠️/❌ | X |
| Async Patterns | ✅/⚠️/❌ | X |
| RVNKCore Integration | ✅/⚠️/❌ | X |
| Database Patterns | ✅/⚠️/❌ | X |
| Security | ✅/⚠️/❌ | X |
| Performance | ✅/⚠️/❌ | X |

## Critical Issues (Must Fix)

### Issue 1: [Title]
- **File**: `path/to/file.java:LINE`
- **Problem**: [Description]
- **Solution**: [How to fix]
```java
// Suggested fix
```

## Important Issues (Should Fix)

### Issue 1: [Title]
- **File**: `path/to/file.java:LINE`
- **Problem**: [Description]
- **Recommendation**: [Suggestion]

## Suggestions (Nice to Have)

- [Suggestion 1]
- [Suggestion 2]

## Positive Aspects

- [What was done well]
- [Good patterns followed]

## RVNK-Specific Checks

| Check | Result |
|-------|--------|
| Console command support | ✅/❌ |
| ServiceRegistry usage | ✅/❌ |
| Repository pattern | ✅/❌ |
| DTO usage | ✅/❌ |
| Async CompletableFuture | ✅/❌ |

## Next Steps

1. [Action item 1]
2. [Action item 2]
```

---

## Reference Documentation

- **Coding Standards**: `docs/standards/coding-standards.md`
- **Database Patterns**: `docs/standards/database-patterns.md`
- **RVNKCore Integration**: `docs/standards/rvnkcore-integration.md`
- **Shared Architecture**: `docs/architecture/shared-patterns.md`

---

*This command implements RVNK-specific code review based on the ecosystem coding standards.*
