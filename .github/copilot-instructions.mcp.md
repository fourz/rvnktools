# MCP Integration Guide

This document describes how to use the RVNKDev MCP server tools for Minecraft server management.

## Overview

The RVNKDev MCP server provides a unified interface for managing Minecraft servers across multiple providers (SparkedHost, MCSS). All server operations are handled through MCP tools invoked via Claude Code.

## Available MCP Tools

### Server Discovery

```python
# Find all configured servers
mcp__rvnkdev-minecraft-server__find_servers()

# Get tool documentation
mcp__rvnkdev-minecraft-server__get_tool_help(tool_name="get_server_state")
```

### Server State (Production-Safe)

```python
# Get server status, CPU, memory, players, uptime
mcp__rvnkdev-minecraft-server__get_server_state(server_id="b2bc4d7e")
```

### Server Control (Test Servers Only)

```python
# Start, stop, or restart server
mcp__rvnkdev-minecraft-server__set_server_state(server_id="b2bc4d7e", action="restart")

# Timeout guidance:
#   stop: API 15s, wait 30s before verify
#   start: API 30s, wait 45s before verify
#   restart: API 45s, wait 60s before verify
```

### Console Operations

```python
# Get recent console output (production-safe)
mcp__rvnkdev-minecraft-server__get_console_output(server_id="b2bc4d7e", lines=50)

# Send console command (test servers only)
mcp__rvnkdev-minecraft-server__send_console_command(server_id="b2bc4d7e", command="reload")
```

### File Operations

```python
# List directory contents (production-safe)
mcp__rvnkdev-minecraft-server__file_read(action="list", server_id="b2bc4d7e", remote_path="/plugins")

# Read file contents (production-safe)
mcp__rvnkdev-minecraft-server__file_read(action="read", server_id="b2bc4d7e", remote_path="/plugins/RVNKTools/config.yml")

# Upload file (test servers only)
mcp__rvnkdev-minecraft-server__file_write(action="upload", server_id="b2bc4d7e", local_path="./target/plugin.jar", remote_path="/plugins")

# Delete file (test servers only)
mcp__rvnkdev-minecraft-server__file_write(action="delete", server_id="b2bc4d7e", remote_path="/plugins/old-plugin.jar")
```

### Database Operations

```python
# Test database connection
mcp__rvnkdev-minecraft-server__database_tools(action="test")

# List all tables
mcp__rvnkdev-minecraft-server__database_tools(action="list_tables")

# Describe table structure
mcp__rvnkdev-minecraft-server__database_tools(action="describe", table_name="players")

# Execute SQL query
mcp__rvnkdev-minecraft-server__database_tools(action="query", query="SELECT * FROM players LIMIT 10")
```

## Configured Servers

| Alias | Server ID | Provider | Type | Access |
|-------|-----------|----------|------|--------|
| rvnk-test | b2bc4d7e | SparkedHost | Test | Full access |
| rvnk-prod | 140324c4 | SparkedHost | Production | Read-only |

## VSCode Tasks

The following MCP-based tasks are available in the command palette:

| Task | Description |
|------|-------------|
| MCP: Query Console | Get recent console output |
| MCP: Query Status | Get server state and metrics |
| MCP: Query Stats | Get performance statistics |
| MCP: Send Command | Execute console command |
| MCP: Restart Server | Restart the server |
| MCP: Reload Plugins | Reload without full restart |
| MCP: List Files | List remote directory |
| MCP: Database List Tables | List database tables |
| MCP: Database Test Connection | Test database connectivity |

## Keyboard Shortcuts

| Shortcut | Task |
|----------|------|
| Ctrl+Alt+C | MCP: Query Console |
| Ctrl+Alt+S | MCP: Query Status |
| Ctrl+Alt+R | MCP: Restart Server |

## Helper Scripts

CLI helper scripts are available in `.vscode/mcp/`:

```bash
# Python
python .vscode/mcp/query-server.py console --lines 50 --server rvnk-test
python .vscode/mcp/control-server.py restart --server rvnk-test
python .vscode/mcp/file-ops.py list /plugins --server rvnk-test
python .vscode/mcp/database-ops.py list-tables
python .vscode/mcp/test-all-servers.py --verbose

# PowerShell
.\\.vscode\\mcp\\Query-Server.ps1 console -Lines 50 -Server rvnk-test
.\\.vscode\\mcp\\Control-Server.ps1 restart -Server rvnk-test
.\\.vscode\\mcp\\File-Ops.ps1 list /plugins -Server rvnk-test
.\\.vscode\\mcp\\Database-Ops.ps1 list-tables
.\\.vscode\\mcp\\Test-AllServers.ps1 -ShowDetail
```

**Note:** These scripts provide documentation and configuration helpers. Actual MCP tool invocation happens through Claude Code.

## Security Model

- **Test servers**: Full read/write access (start, stop, restart, file upload, command execution)
- **Production servers**: Read-only access (status, console logs, file listing)
- **Credentials**: Managed by MCP server, never exposed to scripts or LLM context

## Development Workflow

### Build and Deploy

1. Build the plugin: `mvn clean package`
2. Upload to test server via MCP:
   ```python
   mcp__rvnkdev-minecraft-server__file_write(
       action="upload",
       server_id="b2bc4d7e",
       local_path="./toolkitplugin/target/rvnkcore-1.3.0-alpha.jar",
       remote_path="/plugins"
   )
   ```
3. Restart server:
   ```python
   mcp__rvnkdev-minecraft-server__set_server_state(server_id="b2bc4d7e", action="restart")
   ```
4. Verify deployment:
   ```python
   mcp__rvnkdev-minecraft-server__get_console_output(server_id="b2bc4d7e", lines=50)
   ```

### Debugging

1. Check server status:
   ```python
   mcp__rvnkdev-minecraft-server__get_server_state(server_id="b2bc4d7e")
   ```
2. Get recent console output:
   ```python
   mcp__rvnkdev-minecraft-server__get_console_output(server_id="b2bc4d7e", lines=100)
   ```
3. Send debug command:
   ```python
   mcp__rvnkdev-minecraft-server__send_console_command(server_id="b2bc4d7e", command="rvnktools debug")
   ```

## Migration from Legacy Scripts

The following legacy scripts are replaced by MCP tools:

| Legacy Script | MCP Replacement |
|--------------|-----------------|
| query-server-DEV.ps1 console | get_console_output |
| query-server-DEV.ps1 status | get_server_state |
| query-server-DEV.ps1 command | send_console_command |
| restart-server-DEV.ps1 | set_server_state(action="restart") |
| reload-server-DEV.ps1 | send_console_command(command="reload") |
| copyto-server-DEV.ps1 | file_write(action="upload") |
| cleanup-folder-DEV.ps1 | file_write(action="delete") |
| clean-mysqldb-DEV.ps1 | database_tools(action="query") |

Legacy scripts remain available as fallback but are deprecated.
