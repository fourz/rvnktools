# RVNK Night Event Server - Project Details & Workflow

## Project Context

### MCP Integration Framework

This project leverages SparkedHost MCP (Model Context Protocol) tools for comprehensive remote server management of RVNK Test server. The MCP framework provides programmatic access to server operations without requiring direct SSH or panel access.

**Primary MCP Tools Used:**

- `mcp_sparkedhost_authenticate` - API authentication
- `mcp_sparkedhost_upload-file` - Configuration file deployment  
- `mcp_sparkedhost_restart-server` - Server lifecycle management
- `mcp_sparkedhost_send-console-command` - Remote command execution
- `mcp_sparkedhost_get-file-contents` - Configuration validation
- `mcp_sparkedhost_console-stream` - Real-time monitoring

### Server Specifications

**Target Server**: RVNK Test  
**Server ID**: `b2bc4d7e`  
**Server Name**: `"RVNK Test"`  
**Platform**: Paper/Spigot Minecraft Server  
**Environment**: Development/Testing (Safe for experimental changes)

## Technical Workflow

### Phase 1: Pre-Implementation Setup

1. **MCP Authentication**
   ```javascript
   mcp_sparkedhost_authenticate({
     apiToken: "[SparkedHost API Token]"
   })
   ```

2. **Server Status Verification**
   ```javascript
   mcp_sparkedhost_send-console-command({
     serverId: "b2bc4d7e",
     serverName: "RVNK Test", 
     command: "version"
   })
   ```

3. **Current Configuration Backup**
   ```javascript
   mcp_sparkedhost_get-file-contents({
     serverId: "b2bc4d7e",
     serverName: "RVNK Test",
     filePath: "/server.properties"
   })
   ```

### Phase 2: World Configuration Implementation

1. **Server Properties Modification**
   - Download current `server.properties`
   - Modify world seed: `level-seed=-1106759604738884840`
   - Configure time settings: `doDaylight=false`
   - Upload modified configuration

2. **World Generation Parameters**
   ```properties
   # World Configuration
   level-seed=-1106759604738884840
   level-type=default
   
   # Time Control
   doDaylight=false
   ```

3. **Gamerule Configuration**
   ```javascript
   mcp_sparkedhost_send-console-command({
     serverId: "b2bc4d7e",
     command: "gamerule doDaylightCycle false"
   })
   
   mcp_sparkedhost_send-console-command({
     serverId: "b2bc4d7e", 
     command: "time set night"
   })
   ```

### Phase 3: Phantom Prevention Strategy

**Method 1: Server Configuration** (Preferred)
```properties
# server.properties additions
spawn-monsters=true
spawn-animals=true
# Use plugin-based phantom blocking
```

**Method 2: Plugin-Based Control**
- Install phantom prevention plugin
- Configure via plugin-specific config files
- Upload configuration via MCP

**Validation Commands:**
```javascript
mcp_sparkedhost_send-console-command({
  serverId: "b2bc4d7e",
  command: "gamerule doInsomnia false"
})
```

### Phase 4: Player Kit System Implementation

1. **Plugin Selection Strategy**
   - **EssentialsX**: Full-featured, widely compatible
   - **Custom Kit Plugin**: Lightweight, specific to requirements
   - **Command Block System**: Server-native solution

2. **Kit Configuration (EssentialsX Example)**
   ```yaml
   # kits.yml configuration
   nightevent:
     delay: 0
     items:
       - iron_sword 1
       - shield 1  
       - carrot 64
     auto: true
   ```

3. **Automatic Distribution Setup**
   ```yaml
   # config.yml modifications
   new-player-kit: nightevent
   respawn-kit: nightevent
   ```

### MCP File Management Workflow

**Configuration File Upload Process:**

1. **Prepare Configuration Locally**
   ```javascript
   // Example: server.properties upload
   mcp_sparkedhost_upload-file({
     serverId: "b2bc4d7e",
     serverName: "RVNK Test",
     content: "[server.properties content]",
     filePath: "/server.properties"
   })
   ```

2. **Plugin Configuration Upload**
   ```javascript
   mcp_sparkedhost_upload-file({
     serverId: "b2bc4d7e",
     serverName: "RVNK Test", 
     content: "[plugin config content]",
     filePath: "/plugins/EssentialsX/config.yml"
   })
   ```

3. **Validation & Restart**
   ```javascript
   mcp_sparkedhost_restart-server({
     serverId: "b2bc4d7e",
     serverName: "RVNK Test"
   })
   ```

## Quality Assurance Procedures

### Configuration Validation Checklist

- [ ] **World Seed Verification**: Generate world chunks, verify seed consistency
- [ ] **Time Lock Testing**: Monitor time progression over 10+ minutes
- [ ] **Phantom Prevention**: Extended AFK testing (20+ minutes without sleep)
- [ ] **Kit Distribution**: Test new player join, respawn scenarios
- [ ] **Server Performance**: Monitor resource usage during testing

### Monitoring & Debugging

**Real-Time Console Monitoring:**
```javascript
mcp_sparkedhost_console-stream({
  serverId: "b2bc4d7e",
  serverName: "RVNK Test",
  duration: 60
})
```

**Error Detection Commands:**
```javascript
mcp_sparkedhost_send-console-command({
  serverId: "b2bc4d7e",
  command: "plugins"  // Verify plugin loading
})

mcp_sparkedhost_send-console-command({
  serverId: "b2bc4d7e", 
  command: "tps"      // Performance monitoring
})
```

## Rollback Procedures

### Configuration Rollback

1. **Backup Current State** (Before changes)
   ```javascript
   mcp_sparkedhost_get-file-contents({
     serverId: "b2bc4d7e",
     filePath: "/server.properties"
   })
   // Save content locally as backup
   ```

2. **Restore Previous Configuration**
   ```javascript
   mcp_sparkedhost_upload-file({
     serverId: "b2bc4d7e",
     content: "[backed up server.properties]",
     filePath: "/server.properties"
   })
   ```

### World Rollback Strategy

- **World Backup**: Download world files before seed changes
- **Incremental Backups**: Save world state after each major change
- **Emergency Restore**: Upload backed up world data via MCP

## Performance Considerations

### Resource Monitoring

- **Memory Usage**: Monitor during kit distribution events
- **CPU Load**: Track during world generation phases  
- **Network I/O**: Monitor MCP tool usage frequency
- **Disk Space**: Ensure adequate storage for world data

### Optimization Strategies

- **Batch Operations**: Group MCP commands for efficiency
- **Staged Deployment**: Implement changes incrementally
- **Performance Baseline**: Establish metrics before changes
- **Load Testing**: Simulate multiple player scenarios

## Security & Access Control

### MCP Security Practices

- **API Token Management**: Secure storage and rotation
- **Command Validation**: Verify all console commands before execution
- **File Permission Checks**: Ensure proper file access permissions
- **Audit Logging**: Document all configuration changes

### Server Safety Measures

- **Test Environment Focus**: Utilize RVNK Test server exclusively
- **Change Documentation**: Record all modifications made
- **Validation Requirements**: Test all changes before production consideration
- **Emergency Contacts**: Maintain access to SparkedHost support

---

**Implementation Priority**: High  
**Risk Level**: Low (Test environment)  
**Estimated Duration**: 2-3 hours total  
**Required Skills**: MCP tool usage, Minecraft server administration

**Next Steps**: Review feature specifications in `features/` directory and begin implementation following guides in `implementation/` directory.