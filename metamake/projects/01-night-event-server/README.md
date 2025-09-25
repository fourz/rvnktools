# RVNK Night Event Server Configuration Project

## Project Overview

This metamake project configures the RVNK Test server for a special nighttime event using SparkedHost MCP tools. The server will be transformed into a perpetual night environment with custom player spawning mechanics and phantom prevention.

## Event Specifications

### World Configuration
- **Server**: RVNK Test (SparkedHost serverId: `b2bc4d7e`)
- **Platform**: Paper/Spigot Minecraft Server
- **World Seed**: `-1106759604738884840`
- **Time Setting**: Always night (time lock)
- **Phantom Spawning**: Disabled

### Player Starting Kit
Every player will spawn with:
- **Iron Sword** (1x)
- **Shield** (1x) 
- **Carrots** (64x stack)

## Technical Architecture

### Server Management
- **MCP Integration**: SparkedHost tools for remote server management
- **Configuration Method**: Direct server.properties and plugin configuration
- **File Management**: SFTP uploads via MCP tools
- **Monitoring**: Console streaming and command execution

### Key Components
1. **World Generation**: Custom seed implementation
2. **Time Control**: Server-side time locking mechanism
3. **Phantom Prevention**: Mob spawning rule modifications
4. **Player Kit System**: Join event handler for item distribution

## Project Goals

1. **Configure Server Environment**
   - Set world seed for consistent generation
   - Lock server time to nighttime
   - Disable phantom mob spawning

2. **Implement Player Kit System**
   - Automatic kit distribution on player join
   - Consistent item delivery (iron sword, shield, carrots)
   - Handle respawn scenarios

3. **Validate Event Setup**
   - Test time locking mechanism
   - Verify phantom prevention
   - Confirm player kit distribution
   - Monitor server performance

## MCP Tool Integration

This project leverages SparkedHost MCP tools for:
- **Remote File Management**: Upload configuration files
- **Server Control**: Restart and monitor server status  
- **Console Monitoring**: Real-time log streaming and command execution
- **Plugin Management**: Install and configure required plugins

## Success Criteria

- ✅ Server runs with specified world seed
- ✅ Time remains locked at night (no day cycle)
- ✅ No phantom spawning occurs during night
- ✅ Players receive starting kit upon join/respawn
- ✅ Server performance remains stable
- ✅ Event mechanics function as designed

## Project Structure

```
01-night-event-server/
├── README.md              # This overview document
├── ROADMAP.md             # Implementation phases and status
├── project-details.md     # Technical workflow and context
├── features/              # Feature specifications
├── implementation/        # Step-by-step guides
├── validation/           # Testing and QA procedures
└── docs/                 # Supporting documentation
```

## Quick Start

1. Review implementation phases in `ROADMAP.md`
2. Follow server configuration steps in `implementation/`
3. Use validation checklist to verify functionality
4. Monitor server status using MCP console tools

---

*Project Type*: Server Configuration & Event Management  
*Technology Stack*: Minecraft Paper/Spigot, SparkedHost MCP, YAML Configuration  
*Target Environment*: RVNK Test Server (b2bc4d7e)