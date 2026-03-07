---
name: rvnkdev-mcp
description: RvnkDev MCP Server tool usage and workflows (9 consolidated tools). Use when managing Minecraft servers via MCP tools.
allowed-tools: mcp__rvnkdev-minec__*
---

# RVNKDev MCP Server Tools

**Minecraft server management via RvnkDev FastMCP server (v2.5.0)**

**Last Updated**: January 12, 2026

## Overview

**9 Consolidated Tools** using wrapper pattern with action parameters to reduce context window usage while preserving all functionality.

**Production Status**: 100% operational with multi-provider support (SparkedHost, MCSS)

## Tool Categories (9 Tools Total - Consolidated from 22)

### Discovery (2 tools)
- `get_tool_help(tool_name?, category?, show_workflows?)` - Tool documentation and usage help
- `find_servers(provider?, search?)` - Discover available servers across providers

### Database (1 consolidated tool)
- `database_tools(action, query?, params?, table_name?, connection_name?)` - Database operations
  - `action="list_tables"` - List all tables
  - `action="query"` + `query=` - Execute SQL query
  - `action="describe"` + `table_name=` - Get table structure
  - `action="test"` - Test database connection

### Files (2 consolidated tools)
- `file_read(action, server_id, remote_path?, local_path?)` - Read operations (production-safe)
  - `action="list"` - List directory contents
  - `action="read"` - Read file contents
  - `action="download"` + `local_path=` - Download file
- `file_write(action, server_id, remote_path?, content?, local_path?, operations?)` - Write operations (test only)
  - `action="write"` + `content=` - Write to file
  - `action="upload"` + `local_path=` - Upload file
  - `action="delete"` - Delete file
  - `action="batch"` + `operations=` - Batch operations

### Server (2 consolidated tools)
- `get_server_state(server_id)` - Get server status, resources, players (production-safe)
- `set_server_state(action, server_id)` - Control server state (test only)
  - `action="start"` - Start server
  - `action="stop"` - Stop server
  - `action="restart"` - Restart server

### Console (2 tools)
- `get_console_output(server_id, lines=50)` - Get console logs (production-safe)
- `send_console_command(server_id, command)` - Execute console commands (test only)

## Production Safety

**Environment-Aware Operations:**

```python
# PRODUCTION (Read-Only Safe) - 5 tools
production_safe = [
    "get_tool_help",           # Tool documentation
    "find_servers",            # Server discovery
    "file_read",               # Files (all actions)
    "get_server_state",        # Server status
    "get_console_output",      # Console logs
]

# DEV/TEST ONLY (Requires server_type='test') - 4 tools
restricted = [
    "database_tools",          # Database operations
    "file_write",              # File modifications
    "set_server_state",        # Server control
    "send_console_command",    # Command execution
]
```

## Common Workflows

### Status Check

```python
# 1. Discover servers
servers = find_servers()

# 2. Check server status
status = get_server_state(server_id="b2bc4d7e")

# 3. Review console output
console = get_console_output(server_id="b2bc4d7e", lines=100)
```

### File Review (Production Safe)

```python
# 1. Browse directories
files = file_read(action="list", server_id="b2bc4d7e", remote_path="/plugins")

# 2. Read configuration
config = file_read(action="read", server_id="b2bc4d7e", remote_path="/server.properties")

# 3. Download file for analysis
result = file_read(action="download", server_id="b2bc4d7e",
                   remote_path="/logs/latest.log", local_path="/tmp/latest.log")
```

### Database Exploration

```python
# 1. Test database connection
result = database_tools(action="test")

# 2. List database tables
tables = database_tools(action="list_tables")

# 3. Describe table structure
structure = database_tools(action="describe", table_name="players")

# 4. Execute query
data = database_tools(action="query", query="SELECT * FROM players LIMIT 10")
```

### Server Control (Test Only)

```python
# 1. Check server state
status = get_server_state(server_id="test123")

# 2. Restart server
result = set_server_state(action="restart", server_id="test123")

# 3. Verify status
new_status = get_server_state(server_id="test123")
```

---

## Detailed Tool Reference

### database_tools

**Actions:**
- `list_tables` - List all tables in the database
- `query` - Execute SQL query (requires `query` param)
- `describe` - Get table structure (requires `table_name` param)
- `test` - Test database connection

**Examples:**
```python
database_tools(action="list_tables")
database_tools(action="query", query="SELECT * FROM players LIMIT 10")
database_tools(action="query", query="SELECT * FROM players WHERE name=%s", params=["Steve"])
database_tools(action="describe", table_name="players")
database_tools(action="test", connection_name="default")
```

### file_read / file_write

**file_read Actions:**
- `list` - List directory contents
- `read` - Read file contents
- `download` - Download file to local path (requires `local_path`)

**file_write Actions:**
- `write` - Write content to file (requires `remote_path`, `content`)
- `upload` - Upload local file (requires `local_path`, `remote_path`)
- `delete` - Delete remote file (requires `remote_path`)
- `batch` - Execute batch operations (requires `operations_manifest`)

**Batch Operations Example:**
```python
operations_manifest = [
    {
        "operation": "copy",
        "source": "prod_server:/logs/latest.log",
        "destination": "backup_server:/archives/prod_latest.log"
    },
    {
        "operation": "move",
        "source": "staging_server:/uploads/file.jar",
        "destination": "prod_server:/mods/file.jar"
    }
]

result = file_write(action="batch", server_id="test123", operations=operations_manifest)
```

### get_server_state / set_server_state

**get_server_state Returns:**
- Server state (running/stopped)
- Memory usage
- CPU usage
- Player count
- Uptime

**set_server_state Actions:**
- `start` - Start server (30-60 seconds to running)
- `stop` - Stop server gracefully (1-2 minutes)
- `restart` - Restart server (2-3 minutes total)

---

## Using send_console_command with Plugins

The `send_console_command()` tool executes console commands on the server. Common usage:

- **LuckPerms**: `lp user <player> permission set <perm>`
- **CoreProtect**: `co lookup r:#world t:2h a:-block p:<player>`
- **Dynmap**: `dynmap fullrender world`

Example:
```python
result = send_console_command(server_id="b2bc4d7e", command="say Hello everyone!")
console = get_console_output(server_id="b2bc4d7e", lines=20)
```

## File Paths Reference

```
/
├── plugins/
│   ├── RVNKCore/config.yml
│   ├── LuckPerms/config.yml
│   ├── Dynmap/configuration.txt
│   └── CoreProtect/config.yml
├── logs/
│   └── latest.log
├── server.properties
└── world/
    ├── level.dat
    └── region/
```

## Best Practices

1. **Status First** - Always check server state before operations
2. **Read Before Write** - Review configs before modifications
3. **Console Verification** - Check console output after commands
4. **Environment Awareness** - Respect production restrictions
5. **Use Action Parameters** - Consolidated tools use action parameter for operations
6. **Error Context** - Capture console on failures for debugging

**Server Discovery:**

Use `find_servers()` to list all configured servers from the MCP configuration.

## Security Notes

**Server Classification:**
- Production servers (e.g., `140324c4`): **Read-only operations ONLY**
  - Allowed: `get_server_state`, `get_console_output`, `file_read`, etc.
  - Blocked: `set_server_state`, `send_console_command`, `file_write`, etc.
- Test servers (e.g., `b2bc4d7e`, `1eb313b1-...`): Full tool access permitted

**Credential Security:**
- Credentials: Managed via Bitwarden vault (zero exposure in config)
- BW_SESSION: Set via environment variable in mcp.json (auth tools internal only)
- API validation: ConfigManager validates server_id and permissions before execution

## Restarting MCP Server (Code Changes)

When code changes are made to the MCP server, you must restart it to pick up changes:

**Kill and Restart Process:**

```bash
# Kill all rvnkdev Python processes
wmic process where "commandline like '%rvnkdev%'" call terminate 2>nul

# Wait for restart (5-10 seconds)
sleep 5

# Test MCP tool - Claude Code will auto-restart the server
```

**Verify New Code is Loaded:**

1. Kill processes with wmic command above
2. Wait 5+ seconds for processes to terminate
3. Call any MCP tool - server will auto-restart
4. Check response for expected behavior/error messages

**Common Issues:**

- MCP server shows old behavior after code change → Kill process and wait for restart
- "MCP server not connected" error → Wait and retry, server is restarting
- Old error messages appearing → pycache may need clearing: `find . -name "__pycache__" -exec rm -rf {} +`

**Reinstall Package (if needed):**

```bash
cd rvnkdev-fastmcp-server
pip install -e . --quiet
```

## Error Reference

| Error | Tool | Solution |
|-------|------|----------|
| `Server not found` | Any | Verify server_id exists in config |
| `Permission denied` | Control tools | Production servers are read-only |
| `Bitwarden unavailable` | Any | Check BW_SESSION env var in mcp.json |
| `Database offline` | database_tools | Verify database connection with action="test" |
| `File not found` | file_read/file_write | Check file path and permissions |
| `Unknown action` | Any wrapper tool | Verify action parameter is valid |
| `MCP server not connected` | Any | Server restarting - wait and retry |

## Related Components

- **fastmcp-developer** agent - MCP tool implementation patterns
- **security-engineer** agent - Credential management and production safety
