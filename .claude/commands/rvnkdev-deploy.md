---
description: Deploy files to remote Minecraft servers via SFTP with restart/reload and validation
argument-hint: <server_id> [action] (e.g., b2bc4d7e full)
---

# RVNK Remote Deployment

**Usage**: `/rvnkdev-deploy <server_id> [action]`

**Arguments**: $ARGUMENTS

## Actions

| Action | Description |
|--------|-------------|
| `full` | Copy + Restart + Validate (default) |
| `copy-only` | Copy files without restart |
| `restart-only` | Restart without copy |
| `reload-only` | Reload plugins (config changes) |
| `validate` | Check deployment success |

---

## Step 1: Pre-Deployment

```python
# Check server status
status = get_server_state(server_id="<server_id>")
# Production servers (server_type='production') block file_write

# List current plugins
files = file_read(action="list", server_id="<server_id>", remote_path="/plugins")
```

Check local build artifact exists: `target/*.jar`

---

## Step 2: File Deployment

### Single JAR Upload

```python
result = file_write(
    action="upload",
    server_id="<server_id>",
    local_path="target/RVNKPlugin-1.0.0.jar",
    remote_path="/plugins/RVNKPlugin-1.0.0.jar"
)
```

### Config Upload (Optional)

```python
result = file_write(
    action="upload",
    server_id="<server_id>",
    local_path="src/main/resources/config.yml",
    remote_path="/plugins/RVNKPlugin/config.yml"
)
```

### Batch Upload

```python
operations = [
    {"operation": "copy", "source": "local:target/Plugin.jar", "destination": "/plugins/Plugin.jar"},
    {"operation": "copy", "source": "local:src/main/resources/config.yml", "destination": "/plugins/Plugin/config.yml"}
]
result = file_write(action="batch", server_id="<server_id>", operations=operations)
```

---

## Step 3: Server Restart

### JAR Changes (Full Restart)

```python
# 1. Initiate restart
result = set_server_state(action="restart", server_id="<server_id>")

# 2. Wait 60s for startup

# 3. Check console for result (or status if timeout)
console = get_console_output(server_id="<server_id>", lines=50)
# If no output: get_server_state(server_id="<server_id>")
```

### Config Only (Reload)

```python
result = send_console_command(server_id="<server_id>", command="reload confirm")
console = get_console_output(server_id="<server_id>", lines=20)
```

---

## Step 4: Validation

```python
console = get_console_output(server_id="<server_id>", lines=100)

# Success indicators:
# - "[Plugin] Enabling Plugin"
# - "[Plugin] Plugin enabled successfully"

# Error patterns:
# - "ERROR", "SEVERE", "Exception"
# - "Could not load", "Disabling plugin"
```

### Debug Command

```python
result = send_console_command(server_id="<server_id>", command="rvnktools debug")
console = get_console_output(server_id="<server_id>", lines=50)
```

---

## Workflows

### Full Development Cycle

```powershell
# 1. Build
mvn clean package -DskipTests

# 2. Deploy
/rvnkdev-deploy b2bc4d7e full
```

### Quick Config Iteration

```powershell
/rvnkdev-deploy b2bc4d7e reload-only
```

### Staged Deployment

```powershell
/rvnkdev-deploy b2bc4d7e copy-only    # Stage files
/rvnkdev-query b2bc4d7e files /plugins # Verify
/rvnkdev-deploy b2bc4d7e restart-only  # Apply
/rvnkdev-deploy b2bc4d7e validate      # Check
```

---

## MCP Tools

| Tool | Purpose |
|------|---------|
| `get_server_state` | Check server status |
| `file_read(list)` | List remote directory |
| `file_write(upload)` | Copy files to server |
| `file_write(batch)` | Multi-file deployment |
| `set_server_state(restart)` | Restart server |
| `send_console_command` | Reload/debug commands |
| `get_console_output` | Validate deployment |

---

## Common Errors

| Pattern | Cause | Fix |
|---------|-------|-----|
| `Could not load 'plugins/X.jar'` | JAR corruption | Rebuild |
| `NoClassDefFoundError` | Missing library | Check shading in pom.xml |
| `Disabling plugin X` | Missing dependency | Check plugin.yml depends |
| `YAML parsing error` | Config syntax | Validate YAML |

---

## Output Format

```text
=== RVNK Deployment ===
Server: {id} ({type})
Action: {action}

[1/4] Pre-check... [OK]
[2/4] Deploy... [OK]
[3/4] Restart... [OK] (waited 60s)
[4/4] Validate... [OK]
  - Plugin loaded: RVNKPlugin v1.0.0
  - No errors detected

=== Deployment Complete ===
```

On failure, show errors and suggested fixes.