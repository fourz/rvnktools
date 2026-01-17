---
description: Query remote Minecraft servers for status, console logs, files, and debug info
argument-hint: <server_id> <type> [options] (e.g., b2bc4d7e errors)
---

# RVNK Remote Query

**Usage**: `/rvnkdev-query <server_id> <type> [options]`

**Arguments**: $ARGUMENTS

## Query Types

| Type | Description | Example |
|------|-------------|---------|
| `status` | Server state, memory, CPU, players | `status` |
| `console [N]` | Recent console output (default 50) | `console 100` |
| `errors` | Console filtered for ERROR/WARN | `errors` |
| `plugin <name>` | Plugin-specific log entries | `plugin RVNKTools` |
| `files <path>` | List directory contents | `files /plugins` |
| `read <path>` | Read file contents | `read /server.properties` |
| `debug` | Run rvnktools debug command | `debug` |
| `command <cmd>` | Execute console command | `command "plugins"` |

---

## Status Query

```python
status = get_server_state(server_id="<server_id>")
# Returns: state, cpu_percent, memory_percent, players_online, uptime
```

---

## Console Query

```python
console = get_console_output(server_id="<server_id>", lines=50)
```

| Lines | Use Case |
|-------|----------|
| 20 | Quick check |
| 50 | Default troubleshooting |
| 100 | Plugin startup analysis |
| 200-500 | Full error investigation |

---

## Error Filtering

```python
console = get_console_output(server_id="<server_id>", lines=200)
# Filter for: ERROR, SEVERE, WARN, Exception, "at org."
```

---

## Plugin Logs

```python
console = get_console_output(server_id="<server_id>", lines=100)
# Filter for plugin name (case-insensitive)
```

---

## File Operations

### List Directory

```python
files = file_read(action="list", server_id="<server_id>", remote_path="/plugins")

# Common paths:
# /plugins       - Plugin JARs and folders
# /plugins/X     - Plugin config folder
# /logs          - Log files
```

### Read File

```python
content = file_read(action="read", server_id="<server_id>", remote_path="/server.properties")

# Common files:
# /server.properties
# /plugins/X/config.yml
# /ops.json
```

---

## Debug Command

```python
result = send_console_command(server_id="<server_id>", command="rvnktools debug")
console = get_console_output(server_id="<server_id>", lines=50)
```

---

## Console Command

```python
result = send_console_command(server_id="<server_id>", command="<command>")
console = get_console_output(server_id="<server_id>", lines=20)
```

**Common Commands:**

| Command | Purpose |
|---------|---------|
| `plugins` | List installed plugins |
| `tps` | Server performance |
| `gc` | Garbage collection |
| `save-all` | Save world data |
| `list` | Online players |
| `lp info` | LuckPerms status |
| `dynmap stats` | Dynmap statistics |
| `co status` | CoreProtect status |

---

## MCP Tools

| Query | Tool | Notes |
|-------|------|-------|
| `status` | `get_server_state` | - |
| `console` | `get_console_output` | - |
| `errors` | `get_console_output` | + filter |
| `plugin` | `get_console_output` | + filter |
| `files` | `file_read(list)` | - |
| `read` | `file_read(read)` | - |
| `debug` | `send_console_command` | + console output |
| `command` | `send_console_command` | + console output |

---

## Production vs Test

**All servers**: status, console, errors, plugin, files, read

**Test only**: debug, command (requires write access)

---

## Common Patterns

### Post-Deployment Validation

```text
/rvnkdev-query b2bc4d7e status       # Server running
/rvnkdev-query b2bc4d7e errors       # No new errors
/rvnkdev-query b2bc4d7e plugin X     # Plugin loaded
/rvnkdev-query b2bc4d7e debug        # Services active
```

### Startup Troubleshooting

```text
/rvnkdev-query b2bc4d7e status
/rvnkdev-query b2bc4d7e errors
/rvnkdev-query b2bc4d7e plugin RVNKTools
/rvnkdev-query b2bc4d7e console 200
```

### Performance Check

```text
/rvnkdev-query b2bc4d7e status
/rvnkdev-query b2bc4d7e command "tps"
/rvnkdev-query b2bc4d7e console 100
```

### Database Issues

```text
/rvnkdev-query b2bc4d7e debug
/rvnkdev-query b2bc4d7e errors
/rvnkdev-query b2bc4d7e read /plugins/RVNKTools/config.yml
```