---
description: Generate Root Cause Analysis report for Minecraft plugin or server issues
argument-hint: <issue description or error message>
---

# RVNK Root Cause Analysis

You are about to perform a systematic Root Cause Analysis (RCA) for an issue in the Ravenkraft plugin ecosystem. This analysis will identify the root cause and provide resolution recommendations.

## Issue: $ARGUMENTS

---

## Step 1: Initial Assessment

### 1.1 Issue Classification

Classify the issue type:

| Type | Symptoms | Priority |
|------|----------|----------|
| **Server Crash** | Server stops, restart required | CRITICAL |
| **Plugin Failure** | Plugin disabled, errors in console | HIGH |
| **Database Issue** | Connection failures, data loss | HIGH |
| **Performance** | Lag, TPS drop, memory issues | MEDIUM |
| **Functionality** | Feature not working as expected | MEDIUM |
| **Configuration** | Misconfiguration, missing values | LOW |

### 1.2 Affected Components

Identify affected areas:
- [ ] RVNKCore (core services)
- [ ] RVNKLore (lore system)
- [ ] RVNKQuests (quest system)
- [ ] RVNKWorlds (world management)
- [ ] BarterShops (economy)
- [ ] TokenEconomy (tokens)
- [ ] Database (MySQL/SQLite)
- [ ] External APIs (Dynmap, LuckPerms, etc.)
- [ ] Server (Paper/Spigot)

### 1.3 Timeline

Gather timeline information:
- When did the issue first occur?
- Was there a recent change (plugin update, config change, server update)?
- Is the issue intermittent or consistent?
- What was the server doing when the issue occurred?

---

## Step 2: System Health Check

### 2.1 Server Status

Check server health indicators:

```bash
# If RvnkDev MCP is available:
# - Check server status
# - Get recent console output
# - Review server logs

# Manual checks:
# - TPS (ticks per second) - should be ~20
# - Memory usage
# - Player count at time of issue
```

### 2.2 Plugin Status

Check plugin states:
- Are all RVNK plugins enabled?
- Any plugins in error state?
- Dependency plugins present (Vault, LuckPerms, etc.)?

### 2.3 Database Connectivity

Check database health:
- Connection pool status (HikariCP)
- Active connections vs max pool size
- Recent query errors
- Database server availability

---

## Step 3: Log Analysis

### 3.1 Error Extraction

Search for relevant errors:

```bash
# Common error patterns to search:
grep -i "exception\|error\|severe\|warn" logs/latest.log | tail -100

# Plugin-specific errors:
grep -i "rvnk\|fourz" logs/latest.log | grep -i "error\|exception"

# Stack traces:
grep -A 20 "Exception" logs/latest.log
```

### 3.2 Common RVNK Error Patterns

| Error Pattern | Likely Cause | Investigation |
|---------------|--------------|---------------|
| `NullPointerException in ServiceRegistry` | Service not registered | Check plugin load order |
| `HikariPool - Connection not available` | DB pool exhausted | Check for connection leaks |
| `SQLException: Connection refused` | DB server down | Verify MySQL/SQLite status |
| `NoClassDefFoundError: RVNKCore` | Missing dependency | Check plugin dependencies |
| `IllegalStateException: async on main thread` | Sync call in async context | Review async patterns |
| `EventException` | Event handler error | Check event listener code |
| `CommandException` | Command execution error | Review command implementation |

### 3.3 Timing Correlation

Correlate errors with timeline:
- First occurrence timestamp
- Frequency of occurrence
- Correlation with player actions or scheduled tasks

---

## Step 4: Targeted Investigation

Based on issue classification, perform targeted checks:

### For Database Issues:

```sql
-- Check connection status
SHOW PROCESSLIST;

-- Check table status
SHOW TABLE STATUS LIKE 'rvnk%';

-- Check for locks
SHOW OPEN TABLES WHERE In_use > 0;
```

### For Performance Issues:

- Check async task execution
- Review main thread blocking operations
- Analyze memory allocation patterns
- Check for event listener bottlenecks

### For Plugin Conflicts:

- Identify recently added/updated plugins
- Check for duplicate event handlers
- Review command conflicts
- Check API version compatibility

### For Configuration Issues:

- Validate YAML syntax
- Check required config values
- Verify file permissions
- Compare with default config

---

## Step 5: Root Cause Identification

### 5.1 Evidence Collection

Document findings:
- Relevant log entries
- Configuration snippets
- Code locations (file:line)
- Reproduction steps

### 5.2 Cause Categories

Identify root cause category:

| Category | Examples |
|----------|----------|
| **Code Bug** | Logic error, null pointer, race condition |
| **Configuration** | Invalid value, missing required field |
| **Environment** | Java version, server version, OS issue |
| **Resource** | Memory exhaustion, connection limit, disk space |
| **External** | Database down, API unavailable, network issue |
| **User Error** | Incorrect usage, missing permissions |

### 5.3 Contributing Factors

Identify contributing factors that made the issue possible or worse.

---

## Step 6: Impact Analysis

### 6.1 Scope of Impact

- Number of players affected
- Data integrity impact
- Server availability impact
- Duration of impact

### 6.2 Business Impact

- Gameplay disruption
- Data loss risk
- Player experience degradation

---

## Step 7: Resolution

### 7.1 Immediate Actions (Mitigation)

Steps to restore service immediately:
1. [Immediate action 1]
2. [Immediate action 2]

### 7.2 Permanent Fix

Steps to permanently resolve the root cause:
1. [Fix step 1]
2. [Fix step 2]

### 7.3 Prevention

Steps to prevent recurrence:
1. [Prevention measure 1]
2. [Prevention measure 2]

---

## Output: RCA Report

Generate a comprehensive RCA report:

```markdown
# Root Cause Analysis Report

**Issue**: [Brief description]
**Date**: [Current date]
**Severity**: [CRITICAL/HIGH/MEDIUM/LOW]
**Status**: [RESOLVED/IN PROGRESS/UNDER INVESTIGATION]

---

## Executive Summary

[1-2 paragraph summary of the issue, root cause, and resolution]

---

## Timeline

| Time | Event |
|------|-------|
| [Time] | Issue first reported/detected |
| [Time] | Investigation started |
| [Time] | Root cause identified |
| [Time] | Mitigation applied |
| [Time] | Permanent fix deployed |

---

## Issue Description

### Symptoms
- [Symptom 1]
- [Symptom 2]

### Affected Components
- [Component 1]
- [Component 2]

### Impact
- **Players Affected**: [Number/Description]
- **Duration**: [Time period]
- **Data Impact**: [Description]

---

## Investigation

### Evidence Collected

**Log Entries**:
```
[Relevant log snippets]
```

**Configuration**:
```yaml
[Relevant config snippets]
```

**Code Location**:
- `path/to/file.java:LINE` - [Description]

### Analysis

[Detailed analysis of the evidence]

---

## Root Cause

**Primary Cause**: [Clear statement of root cause]

**Category**: [Code Bug/Configuration/Environment/Resource/External/User Error]

**Contributing Factors**:
1. [Factor 1]
2. [Factor 2]

---

## Resolution

### Immediate Actions (Mitigation)
- [x] [Action 1]
- [x] [Action 2]

### Permanent Fix
- [ ] [Fix 1]
- [ ] [Fix 2]

### Code Changes
```java
// Before (problematic)
[code snippet]

// After (fixed)
[code snippet]
```

---

## Prevention

### Short-term
- [Measure 1]
- [Measure 2]

### Long-term
- [Measure 1]
- [Measure 2]

### Monitoring
- [What to monitor to detect recurrence]

---

## Lessons Learned

1. [Lesson 1]
2. [Lesson 2]

---

## References

- **Related Issues**: [Links to related tasks/issues]
- **Documentation**: [Links to relevant docs]
- **Code Changes**: [Links to commits/PRs]

---

*RCA conducted by Claude Code (rvnk-rca command)*
*Report generated: [Date]*
```

---

## Common RVNK Issues Reference

### Database Connection Pool Exhaustion
- **Symptom**: "Connection not available, request timed out"
- **Cause**: Connections not being returned to pool
- **Fix**: Use try-with-resources, check for missing `.close()` calls

### Service Not Found
- **Symptom**: `ServiceNotFoundException` or null from `ServiceRegistry.get()`
- **Cause**: Service not registered or wrong load order
- **Fix**: Check `plugin.yml` dependencies, verify registration

### Async Operation on Main Thread
- **Symptom**: Server lag, `IllegalStateException`
- **Cause**: Blocking database/I/O call on main thread
- **Fix**: Wrap in `CompletableFuture.supplyAsync()`

### Configuration Reload Failure
- **Symptom**: Config changes not applied
- **Cause**: YAML syntax error, missing reload, caching
- **Fix**: Validate YAML, implement proper reload handler

---

*This command provides structured RCA for RVNK plugin ecosystem issues.*
