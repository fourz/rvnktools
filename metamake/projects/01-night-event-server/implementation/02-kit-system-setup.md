# Player Kit System Implementation Guide

## Step 1: Plugin Selection & Download

### Recommended Plugin: EssentialsX

EssentialsX provides comprehensive kit management with automatic distribution capabilities.

**Download Source**: [EssentialsX Releases](https://github.com/EssentialsX/Essentials/releases)
**Required JAR**: `EssentialsX-2.x.x.jar` (latest stable version)

### Alternative: Custom Kit Plugin

For lightweight solution, consider:
- **Kits Plugin** by various developers
- **Custom command-based solution**
- **Datapack implementation** (if server supports)

---

## Step 2: EssentialsX Installation

### Upload Plugin to Server

```javascript
// Upload EssentialsX JAR file
mcp_sparkedhost_upload-file({
  serverId: "b2bc4d7e",
  serverName: "RVNK Test",
  localFilePath: "path/to/EssentialsX-2.x.x.jar",
  filePath: "/plugins/EssentialsX.jar"
})
```

### Initial Plugin Configuration

Create basic EssentialsX configuration:

```yaml
# /plugins/Essentials/config.yml (Basic Configuration)
kit-auto-equip: true
new-player-kit: nightevent
newbie-spawn: 
  kit: nightevent
spawn-on-join: false
update-bed-at-daytime: true
warn-on-smite: true
```

Upload configuration:

```javascript
mcp_sparkedhost_upload-file({
  serverId: "b2bc4d7e", 
  serverName: "RVNK Test",
  content: "[config.yml content above]",
  filePath: "/plugins/Essentials/config.yml"
})
```

---

## Step 3: Kit Configuration Setup

### Create Night Event Kit

Configure the specific kit for night event players:

```yaml
# /plugins/Essentials/kits.yml
kits:
  nightevent:
    delay: 0
    items:
      - iron_sword 1 name:&bNight_Warrior's_Blade
      - shield 1 name:&bProtector's_Shield  
      - carrot 64
    auto: true
    group: default
```

### Advanced Kit Configuration

For more sophisticated kit handling:

```yaml
# Extended kit configuration with metadata
kits:
  nightevent:
    delay: 0
    items:
      - iron_sword 1 name:&bNight_Warrior's_Blade lore:&7A_blade_forged_for_the_eternal_night
      - shield 1 name:&bProtector's_Shield lore:&7Defense_against_the_darkness
      - carrot 64 name:&eNourishing_Carrots lore:&7Sustenance_for_the_long_night
    auto: true
    link: true
    permissions: 
      - essentials.kit.nightevent
    group: default
    commands:
      - "say {PLAYER} has received the Night Event kit!"
```

### Upload Kit Configuration

```javascript
mcp_sparkedhost_upload-file({
  serverId: "b2bc4d7e",
  serverName: "RVNK Test", 
  content: "[kits.yml content above]",
  filePath: "/plugins/Essentials/kits.yml"
})
```

---

## Step 4: Automatic Kit Distribution

### Configure Auto-Kit on Join

Modify main EssentialsX config for automatic distribution:

```yaml
# Enhanced config.yml for automatic kit distribution
kit-auto-equip: true
new-player-kit: nightevent
newbie-kit-delay: 0
respawn-kit: nightevent
respawn-kit-delay: 0

# New player handling
new-players:
  announce-format: "&dWelcome {DISPLAYNAME} to the Night Event!"
  kit: nightevent
  teleport-to-spawn: true

# Death/respawn handling  
death:
  respawn-kit: nightevent
  respawn-kit-delay: 0
```

### Alternative: Command-Based Distribution

If automatic distribution fails, set up command-based fallback:

```javascript
// Create startup commands for manual kit distribution
mcp_sparkedhost_send-console-command({
  serverId: "b2bc4d7e",
  serverName: "RVNK Test",
  command: "essentials:kit nightevent [player]"
})
```

---

## Step 5: Plugin Installation & Testing

### Restart Server with Plugin

```javascript
mcp_sparkedhost_restart-server({
  serverId: "b2bc4d7e", 
  serverName: "RVNK Test"
})
```

### Verify Plugin Loading

After server restart, check plugin status:

```javascript
// Check if EssentialsX loaded successfully
mcp_sparkedhost_send-console-command({
  serverId: "b2bc4d7e",
  serverName: "RVNK Test",
  command: "plugins"
})

// Should show: EssentialsX v2.x.x (green/enabled)
```

### Test Kit Functionality

```javascript
// Test kit command manually
mcp_sparkedhost_send-console-command({
  serverId: "b2bc4d7e", 
  serverName: "RVNK Test",
  command: "essentials:kit list"
})

// Should show: nightevent kit available
```

---

## Step 6: Kit Distribution Validation

### Test New Player Experience

Simulate new player join to test automatic kit distribution:

```javascript
// If testing with real player account
// Monitor console for kit distribution messages
mcp_sparkedhost_console-stream({
  serverId: "b2bc4d7e",
  serverName: "RVNK Test", 
  duration: 60
})
```

### Manual Kit Distribution Test

Test manual kit giving for troubleshooting:

```javascript
// Give kit to specific player (replace [player] with actual username)
mcp_sparkedhost_send-console-command({
  serverId: "b2bc4d7e",
  serverName: "RVNK Test",
  command: "essentials:kit nightevent [player]"
})
```

### Verify Kit Contents

Confirm kit contains correct items:
- Iron Sword (1x)
- Shield (1x)  
- Carrots (64x)

---

## Troubleshooting Kit Issues

### Issue 1: Plugin Not Loading

**Symptoms**: EssentialsX not in plugin list
**Solution**:
1. Verify JAR file uploaded correctly
2. Check server compatibility (Paper/Spigot version)
3. Review console for error messages during startup

### Issue 2: Kit Not Auto-Distributing  

**Symptoms**: Players don't receive kit on join
**Solution**:
1. Check `new-player-kit` configuration
2. Verify `auto: true` in kit definition
3. Test manual kit command
4. Review EssentialsX permissions

### Issue 3: Incorrect Kit Contents

**Symptoms**: Kit contains wrong items or quantities
**Solution**:
1. Verify `kits.yml` configuration
2. Check item name format (use minecraft item IDs)
3. Validate quantity specifications
4. Reload plugin configuration

---

## Configuration Validation Commands

### Check Kit Configuration

```javascript
// List all available kits
mcp_sparkedhost_send-console-command({
  serverId: "b2bc4d7e",
  serverName: "RVNK Test", 
  command: "essentials:kit list"
})

// Check specific kit details
mcp_sparkedhost_send-console-command({
  serverId: "b2bc4d7e",
  serverName: "RVNK Test",
  command: "essentials:kit nightevent"
})
```

### Reload Plugin Configuration

```javascript
// Reload EssentialsX without server restart
mcp_sparkedhost_send-console-command({
  serverId: "b2bc4d7e", 
  serverName: "RVNK Test",
  command: "essentials:reload"
})
```

---

## Next Steps

After kit system installation:

1. **Run Full Validation Tests**: Test complete player experience
2. **Monitor Performance**: Check server impact of kit distribution
3. **Document Final Configuration**: Record working kit setup
4. **Prepare for Event Launch**: Validate all systems working together

**Implementation Status**: Kit system configured, ready for comprehensive testing.