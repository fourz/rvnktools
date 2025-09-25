# Night Event Server Features

## Feature 1: Perpetual Night Environment

### Overview

The server maintains a permanent nighttime environment to create a unique atmospheric gameplay experience. This feature eliminates the day/night cycle and locks the world in perpetual darkness.

### Technical Implementation

**Server Configuration:**
- `doDaylightCycle=false` gamerule
- `time set night` command on server start
- Server.properties modifications for consistent time state

**Expected Behavior:**
- World time remains at ~18000 (night)
- No sun progression or daylight changes
- Moon and stars remain visible consistently
- Darkness level appropriate for mob spawning

### Success Criteria

- [ ] Time does not progress beyond night values
- [ ] Server restart maintains night setting
- [ ] World lighting remains at night levels
- [ ] No daylight progression after 30+ minutes of testing

---

## Feature 2: Phantom Prevention System

### Overview

Prevents phantom mobs from spawning despite the permanent night environment, ensuring players are not overwhelmed by phantom attacks during the event.

### Technical Implementation

**Primary Method:**
```properties
# Gamerule approach
gamerule doInsomnia false
```

**Alternative Methods:**
- Plugin-based phantom spawn blocking
- Mob spawning rule modifications
- Entity type filtering

### Expected Behavior

- No phantom spawns regardless of player sleep patterns
- Other night mobs spawn normally (zombies, skeletons, etc.)
- Players can remain active during night without phantom harassment
- Gameplay balance maintained for night-time activities

### Success Criteria

- [ ] Zero phantom spawns during 20+ minute AFK testing
- [ ] Players can stay awake indefinitely without consequences
- [ ] Other hostile mobs spawn normally
- [ ] No phantom-related server errors or warnings

---

## Feature 3: Custom World Generation

### Overview

Server generates world using specific seed `-1106759604738884840` to ensure consistent terrain generation and predictable world features for the event.

### Technical Implementation

**Configuration:**
```properties
level-seed=-1106759604738884840
level-type=default
```

**World Parameters:**
- Seed: `-1106759604738884840`
- World type: Default/Normal
- Generate structures: Enabled
- World border: Default settings

### Expected Behavior

- World generates with specified seed consistently
- Terrain features match seed expectations
- Structures (villages, dungeons) generate as expected for seed
- Multiple server restarts produce identical world generation

### Success Criteria

- [ ] World generates with correct seed value
- [ ] Terrain matches expected seed characteristics  
- [ ] Consistent generation across server restarts
- [ ] Structures generate in expected locations

---

## Feature 4: Player Starting Kit System

### Overview

Every player receives a standardized starting kit upon joining the server or respawning, ensuring consistent gameplay experience and survival capabilities.

### Kit Contents

**Standard Kit:**
- **Iron Sword** (1x) - Primary weapon for combat
- **Shield** (1x) - Defensive equipment  
- **Carrots** (64x stack) - Food source for sustenance

### Technical Implementation

**Plugin-Based Approach:**
- EssentialsX or similar kit plugin
- Automatic distribution on player join
- Respawn kit handling for deaths

**Configuration Example:**
```yaml
nightevent:
  delay: 0
  items:
    - iron_sword 1
    - shield 1  
    - carrot 64
  auto: true
```

### Trigger Events

1. **First Join**: New players receive kit immediately
2. **Respawn**: Players receive kit after death/respawn
3. **Manual Request**: Optional manual kit claiming (if enabled)

### Expected Behavior

- Kit appears in player inventory upon join
- Items are added to available inventory slots
- Full inventory handling (drops excess items)
- Consistent kit delivery across all players

### Success Criteria

- [ ] 100% kit delivery success rate for new players
- [ ] Respawn kit distribution functions correctly
- [ ] Proper inventory management (no item loss)
- [ ] Kit contents match specifications exactly

---

## Feature 5: Server Performance Optimization

### Overview

Maintain optimal server performance during the night event despite increased mob spawning and player activity levels.

### Performance Targets

**Resource Limits:**
- CPU usage: <70% average
- Memory usage: <80% allocated RAM  
- TPS (Ticks Per Second): >18 TPS consistently
- Player capacity: Support 10+ concurrent players

### Optimization Strategies

1. **Mob Spawning Balance**
   - Configure appropriate mob spawn rates
   - Prevent excessive mob accumulation
   - Balance difficulty with performance

2. **Kit Distribution Efficiency** 
   - Minimize server impact during kit delivery
   - Batch operations where possible
   - Efficient inventory management

3. **World Generation Optimization**
   - Pre-generate spawn area chunks
   - Optimize world border settings
   - Monitor chunk loading performance

### Monitoring Metrics

- Server TPS monitoring
- Memory usage tracking  
- Player connection stability
- Console error/warning frequency

### Success Criteria

- [ ] Server maintains >18 TPS under load
- [ ] Memory usage stays within safe limits
- [ ] No performance-related player disconnections
- [ ] Stable operation with 10+ concurrent players

---

## Integration Requirements

### MCP Tool Compatibility

All features must be configurable and manageable through SparkedHost MCP tools:

- Configuration file uploads (`mcp_sparkedhost_upload-file`)
- Server restart management (`mcp_sparkedhost_restart-server`)
- Console command execution (`mcp_sparkedhost_send-console-command`)
- Real-time monitoring (`mcp_sparkedhost_console-stream`)

### Plugin Dependencies

**Required Plugins:**
- Kit management plugin (EssentialsX recommended)
- Optional: Anti-phantom plugin (if gamerules insufficient)
- Optional: Performance monitoring plugin

**Plugin Selection Criteria:**
- Paper/Spigot compatibility
- Lightweight resource usage
- MCP-compatible configuration methods
- Reliable update/maintenance history

---

## Feature Testing Matrix

| Feature | Test Method | Success Criteria | Priority |
|---------|-------------|------------------|----------|
| Night Lock | Time progression monitoring | No time advancement | High |
| Phantom Prevention | Extended AFK testing | Zero phantom spawns | High |  
| World Seed | Terrain comparison | Correct seed generation | Medium |
| Player Kits | Join/respawn testing | 100% kit delivery | High |
| Performance | Load testing | >18 TPS stable | Medium |

**Testing Environment**: RVNK Test Server (serverId: `b2bc4d7e`)  
**Testing Duration**: Minimum 2 hours comprehensive testing  
**Acceptance Criteria**: All High priority features must pass 100% of tests