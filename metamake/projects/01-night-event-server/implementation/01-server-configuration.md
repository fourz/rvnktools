# Server Configuration Implementation Guide

## Step 1: MCP Connection Setup

### Prerequisites
- SparkedHost API token configured
- Access to RVNK Test server (serverId: `b2bc4d7e`)

### Authentication Process

Execute MCP authentication and verify server access:

```javascript
// Authenticate with SparkedHost MCP
mcp_sparkedhost_authenticate({
  apiToken: "[Your SparkedHost API Token]"
})

// Verify server connection
mcp_sparkedhost_send-console-command({
  serverId: "b2bc4d7e",
  serverName: "RVNK Test",
  command: "version"
})
```

### Validation Steps
1. Confirm authentication success
2. Verify server response to commands
3. Test file access permissions

---

## Step 2: Current Configuration Backup

### Backup Critical Files

Download and save current server configuration:

```javascript
// Backup server.properties
mcp_sparkedhost_get-file-contents({
  serverId: "b2bc4d7e", 
  serverName: "RVNK Test",
  filePath: "/server.properties"
})

// Backup world data info
mcp_sparkedhost_get-file-contents({
  serverId: "b2bc4d7e",
  serverName: "RVNK Test", 
  filePath: "/world/level.dat"
})
```

### Create Local Backup Files
Save the downloaded configurations locally for rollback purposes:
- `backup-server.properties`
- `backup-level.dat` (if accessible)

---

## Step 3: Server Properties Configuration

### World Seed Configuration

Create modified `server.properties` with world seed:

```properties
#Minecraft server properties (Night Event Configuration)
allow-flight=false
allow-nether=true
broadcast-console-to-ops=true
broadcast-rcon-to-ops=true
difficulty=normal
enable-command-block=false
enable-jmx-monitoring=false
enable-rcon=false
enable-status=true
enforce-whitelist=false
entity-broadcast-range-percentage=100
force-gamemode=false
function-permission-level=2
gamemode=survival
generate-structures=true
generator-settings={}
hardcore=false
level-name=world
level-seed=-1106759604738884840
level-type=default
max-build-height=256
max-players=20
max-tick-time=60000
max-world-size=29999984
motd=RVNK Night Event Server
network-compression-threshold=256
online-mode=true
op-permission-level=4
player-idle-timeout=0
prevent-proxy-connections=false
pvp=true
query.port=25565
rate-limit=0
rcon.password=
rcon.port=25575
server-ip=
server-port=25781
simulation-distance=10
spawn-animals=true
spawn-monsters=true
spawn-npcs=true
spawn-protection=16
sync-chunk-writes=true
text-filtering-config=
use-native-transport=true
view-distance=10
white-list=true
```

### Upload Modified Configuration

```javascript
mcp_sparkedhost_upload-file({
  serverId: "b2bc4d7e",
  serverName: "RVNK Test",
  content: "[server.properties content above]",
  filePath: "/server.properties"
})
```

---

## Step 4: Time Control Implementation

### Set Gamerules for Night Lock

Execute console commands to configure time settings:

```javascript
// Disable daylight cycle
mcp_sparkedhost_send-console-command({
  serverId: "b2bc4d7e", 
  serverName: "RVNK Test",
  command: "gamerule doDaylightCycle false"
})

// Set time to night
mcp_sparkedhost_send-console-command({
  serverId: "b2bc4d7e",
  serverName: "RVNK Test", 
  command: "time set 18000"
})

// Disable insomnia (phantom prevention)
mcp_sparkedhost_send-console-command({
  serverId: "b2bc4d7e",
  serverName: "RVNK Test",
  command: "gamerule doInsomnia false"
})
```

### Verify Time Settings

```javascript
// Check current time
mcp_sparkedhost_send-console-command({
  serverId: "b2bc4d7e",
  serverName: "RVNK Test",
  command: "time query daytime"
})

// Verify gamerules
mcp_sparkedhost_send-console-command({
  serverId: "b2bc4d7e", 
  serverName: "RVNK Test",
  command: "gamerule doDaylightCycle"
})
```

---

## Step 5: Server Restart

### Restart Server with New Configuration

```javascript
mcp_sparkedhost_restart-server({
  serverId: "b2bc4d7e",
  serverName: "RVNK Test"
})
```

### Monitor Restart Process

Wait 30-60 seconds, then verify server startup:

```javascript
// Check server status after restart
mcp_sparkedhost_send-console-command({
  serverId: "b2bc4d7e",
  serverName: "RVNK Test", 
  command: "list"
})

// Verify world loaded with correct seed
mcp_sparkedhost_send-console-command({
  serverId: "b2bc4d7e",
  serverName: "RVNK Test",
  command: "seed"
})
```

---

## Step 6: Configuration Validation

### Test World Seed

Verify world generation matches expected seed:

```javascript
// Check world seed
mcp_sparkedhost_send-console-command({
  serverId: "b2bc4d7e",
  serverName: "RVNK Test",
  command: "seed"
})

// Expected output: Seed: [-1106759604738884840]
```

### Validate Time Lock

Monitor time progression:

```javascript
// Check time multiple times over 5+ minutes
mcp_sparkedhost_send-console-command({
  serverId: "b2bc4d7e",
  serverName: "RVNK Test", 
  command: "time query daytime"
})

// Time should remain at ~18000 (night)
```

---

## Troubleshooting Common Issues

### Issue 1: World Seed Not Applied

**Symptoms**: World generates with default/random seed
**Solution**: 
1. Verify `level-seed` in server.properties
2. Delete existing world folder (backup first)
3. Restart server to regenerate world

### Issue 2: Time Still Progressing

**Symptoms**: Day/night cycle continues despite gamerule
**Solution**:
1. Re-execute gamerule command
2. Verify command success in console
3. Check for plugin conflicts overriding gamerules

### Issue 3: Phantoms Still Spawning

**Symptoms**: Phantoms appear despite `doInsomnia=false`
**Solution**:
1. Verify gamerule applied correctly
2. Consider additional phantom-blocking plugin
3. Test with extended AFK scenarios

---

## Next Steps

After completing server configuration:

1. **Proceed to Plugin Installation**: Set up player kit system
2. **Run Validation Tests**: Verify all configuration changes
3. **Monitor Performance**: Check server stability
4. **Document Changes**: Record all modifications made

**Implementation Status**: Server configuration complete, ready for plugin setup.