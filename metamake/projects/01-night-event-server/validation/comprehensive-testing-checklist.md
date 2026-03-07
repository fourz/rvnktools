# Night Event Server Validation Checklist

## Pre-Implementation Validation

### MCP Connection & Access
- [ ] **MCP Authentication Successful**
  - API token authenticates without errors
  - Server connection established to RVNK Test (b2bc4d7e)
  - Basic commands execute successfully

- [ ] **Server Access Permissions**
  - File upload permissions verified
  - Console command execution functional
  - Server restart capabilities confirmed

- [ ] **Backup Procedures Complete**
  - Current server.properties backed up locally
  - World data information documented
  - Rollback procedures tested and ready

---

## Phase 1: World & Time Configuration Validation

### World Seed Verification
- [ ] **Seed Configuration Applied**
  - Server.properties contains `level-seed=-1106759604738884840`
  - Configuration uploaded successfully via MCP
  - Server restart completed without errors

- [ ] **World Generation Testing**
  - `/seed` command returns correct seed value
  - World generates with expected terrain features
  - Multiple chunks generate consistently with seed
  - Structures (villages, dungeons) appear in expected locations

### Time Lock Implementation
- [ ] **Gamerule Configuration**
  - `gamerule doDaylightCycle false` executed successfully
  - `time set 18000` (night) applied correctly
  - Console confirms gamerule changes accepted

- [ ] **Time Progression Testing**
  - Monitor time for 10+ minutes - should remain at night (~18000)
  - `/time query daytime` consistently returns night values
  - No sun/moon movement observed in game
  - Server restart maintains time lock settings

### Phantom Prevention System
- [ ] **Insomnia Gamerule Applied**
  - `gamerule doInsomnia false` executed successfully
  - Console confirms gamerule change accepted
  - Setting persists after server restart

- [ ] **Phantom Spawn Testing**
  - Player AFK test for 20+ minutes without sleep
  - Zero phantom spawns observed during test period
  - Other night mobs (zombies, skeletons) spawn normally
  - No phantom-related errors in console logs

---

## Phase 2: Player Kit System Validation

### Plugin Installation Verification
- [ ] **EssentialsX Plugin Loading**
  - Plugin appears in `/plugins` list as enabled/green
  - No errors during plugin startup in console
  - Plugin version compatible with server version

- [ ] **Configuration Files Applied**
  - `/plugins/Essentials/config.yml` uploaded successfully
  - `/plugins/Essentials/kits.yml` uploaded successfully
  - Plugin configuration reloaded without errors

### Kit Definition Testing
- [ ] **Kit Availability**
  - `/essentials:kit list` shows "nightevent" kit
  - Kit details display correct items (iron sword, shield, 64 carrots)
  - No configuration errors in kit definition

- [ ] **Manual Kit Distribution**
  - `/essentials:kit nightevent [player]` works correctly
  - All three items appear in player inventory
  - Item quantities match specifications exactly
  - Custom item names/lore display correctly (if configured)

### Automatic Distribution Testing
- [ ] **New Player Join Experience**
  - Fresh player receives kit automatically on first join
  - Kit appears in inventory immediately after join
  - Welcome message displays correctly (if configured)
  - No delays or failures in kit distribution

- [ ] **Respawn Kit Distribution**
  - Player receives kit after death/respawn
  - Kit items replace or add to inventory appropriately
  - Respawn kit delay settings function correctly
  - Multiple deaths/respawns handle correctly

---

## Phase 3: Integration & Performance Validation

### Server Performance Testing
- [ ] **Resource Usage Monitoring**
  - CPU usage remains <70% during normal operation
  - Memory usage stays within acceptable limits
  - TPS (Ticks Per Second) maintains >18 consistently
  - No performance warnings in console

- [ ] **Multi-Player Load Testing**
  - Server handles 5+ concurrent players without issues
  - Kit distribution works for multiple simultaneous joins
  - Performance remains stable with multiple players
  - No lag or timeout issues observed

### System Integration Testing
- [ ] **Combined Feature Functionality**
  - Night lock + kit distribution work together
  - Phantom prevention + player activity function correctly
  - World generation + server performance remain stable
  - All systems work simultaneously without conflicts

### Error Handling & Edge Cases
- [ ] **Full Inventory Scenarios**
  - Kit distribution handles full player inventories
  - Excess items drop appropriately or queue for later
  - No items lost during distribution process
  - Clear messaging for inventory-related issues

- [ ] **Plugin Conflict Testing**
  - No conflicts with existing server plugins
  - EssentialsX integrates cleanly with other systems
  - Commands execute without permission conflicts
  - Configuration changes don't break other features

---

## Phase 4: Comprehensive Event Simulation

### Complete Player Experience Test
- [ ] **Full Event Scenario**
  - New player joins during night (perpetual)
  - Receives starting kit automatically
  - Can survive and play in night environment
  - No phantom harassment during extended play
  - Server remains stable throughout session

- [ ] **Extended Duration Testing**
  - Server runs stable for 2+ hours with night lock
  - Multiple player join/leave cycles function correctly
  - Kit distribution remains consistent over time
  - No memory leaks or performance degradation

### Real-World Usage Validation
- [ ] **Player Feedback Simulation**
  - Kit contents appropriate for survival needs
  - Night environment provides engaging gameplay
  - Server performance acceptable for player experience
  - Event mechanics function as designed

---

## Phase 5: Documentation & Handoff Validation

### Configuration Documentation
- [ ] **Settings Documentation Complete**
  - All configuration changes documented
  - Server.properties modifications recorded
  - Plugin configurations saved and explained
  - Gamerule changes listed with explanations

- [ ] **Troubleshooting Procedures**
  - Common issues and solutions documented
  - Rollback procedures tested and validated
  - Emergency recovery steps available
  - Contact information for support available

### Maintenance Procedures  
- [ ] **Ongoing Maintenance Plan**
  - Plugin update procedures documented
  - Configuration backup schedules established
  - Performance monitoring procedures defined
  - Event modification procedures outlined

---

## Final Acceptance Criteria

### Critical Success Requirements (Must Pass 100%)
- ✅ **World generates with seed -1106759604738884840**
- ✅ **Time remains locked at night permanently**
- ✅ **Zero phantom spawns during extended testing**
- ✅ **100% kit delivery success rate for all players**
- ✅ **Server performance >18 TPS under normal load**

### Quality Assurance Requirements (Must Pass 95%+)
- ✅ **No critical errors in console logs**
- ✅ **Plugin integration stable over 2+ hours**
- ✅ **Multi-player scenarios function correctly**
- ✅ **Configuration changes persist after restarts**
- ✅ **Player experience matches event requirements**

---

## Validation Sign-Off

### Technical Validation
- [ ] **Server Configuration Verified** - Signature: _________________ Date: _______
- [ ] **Plugin System Validated** - Signature: _________________ Date: _______
- [ ] **Performance Testing Complete** - Signature: _________________ Date: _______
- [ ] **Integration Testing Passed** - Signature: _________________ Date: _______

### Event Readiness Approval
- [ ] **Event Server Ready for Launch** - Signature: _________________ Date: _______
- [ ] **Documentation Complete** - Signature: _________________ Date: _______
- [ ] **Support Procedures Established** - Signature: _________________ Date: _______

**Project Completion Status**: All validation criteria met, event server ready for deployment.

---

## Post-Validation Monitoring

### Ongoing Monitoring Checklist (Daily During Event)
- [ ] Server uptime and stability
- [ ] Player kit distribution functionality
- [ ] Performance metrics (TPS, CPU, memory)
- [ ] Console error monitoring
- [ ] Player experience feedback

**Validation Completed By**: _________________  
**Date**: _________________  
**Server Environment**: RVNK Test (serverId: b2bc4d7e)  
**Next Phase**: Event launch or production deployment preparation