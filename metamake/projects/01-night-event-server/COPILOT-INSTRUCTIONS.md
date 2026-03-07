# RVNK Night Event Server - Copilot Instructions

## Project-Specific Guidance

When working on the RVNK Night Event Server configuration project, follow these specialized instructions for optimal implementation and troubleshooting.

### MCP Tool Usage Patterns

**SparkedHost MCP Integration:**
- Always use `serverId: "b2bc4d7e"` and `serverName: "RVNK Test"` for all MCP operations
- Authenticate once per session using `mcp_sparkedhost_authenticate`
- Test connectivity before major operations using `mcp_sparkedhost_send-console-command` with `version`
- Use console streaming for real-time monitoring during critical changes

**File Management Best Practices:**
- Backup configurations before modifications using `mcp_sparkedhost_get-file-contents`
- Upload configurations in logical sequence (server.properties first, then plugins)
- Verify file uploads by reading back contents after upload
- Use proper file paths: `/server.properties`, `/plugins/Essentials/config.yml`

### Configuration Priority Order

**Implementation Sequence:**
1. **Server Properties**: World seed, basic server settings
2. **Gamerules**: Time control and phantom prevention  
3. **Plugin Installation**: EssentialsX for kit management
4. **Plugin Configuration**: Kit definitions and auto-distribution
5. **Server Restart**: Apply all changes simultaneously
6. **Validation Testing**: Comprehensive functionality verification

### World Seed Implementation

**Critical Configuration:**
```properties
level-seed=-1106759604738884840
level-type=default
generate-structures=true
```

**Validation Commands:**
- `/seed` - Verify correct seed loaded
- Test world generation in multiple directions from spawn
- Confirm structures generate as expected for the seed

### Time Lock Mechanism

**Gamerule Configuration:**
```javascript
gamerule doDaylightCycle false
time set 18000
gamerule doInsomnia false
```

**Monitoring Strategy:**
- Check time every 5 minutes during testing: `/time query daytime`
- Expected value: ~18000 (night time)
- No progression should occur over extended periods

### Phantom Prevention Strategy

**Primary Method**: Gamerule approach preferred
- `gamerule doInsomnia false` - Prevents phantom spawning trigger
- More reliable than plugin-based solutions
- Integrates with vanilla Minecraft mechanics

**Validation Approach**:
- Extended AFK testing (20+ minutes)
- Player should not sleep during test period
- Monitor for phantom spawns using console and visual confirmation

### Player Kit System Configuration

**EssentialsX Kit Definition:**
```yaml
nightevent:
  delay: 0
  items:
    - iron_sword 1
    - shield 1
    - carrot 64
  auto: true
```

**Distribution Triggers:**
- New player join (automatic)
- Player respawn after death
- Manual command execution for testing

### Error Handling Protocols

**Common Issues & Solutions:**

**Plugin Loading Failures:**
- Check server compatibility (Paper/Spigot version)
- Verify JAR file integrity after upload
- Review console startup logs for specific error messages

**Kit Distribution Problems:**
- Test manual kit command first: `/essentials:kit nightevent [player]`
- Verify `auto: true` in kit configuration
- Check player inventory space availability

**Time Lock Not Working:**
- Re-execute gamerule commands after server restart
- Verify no conflicting plugins override time settings
- Monitor console for gamerule confirmation messages

### Performance Monitoring

**Key Metrics to Track:**
- Server TPS: Should maintain >18 consistently
- Memory usage: Monitor during kit distribution events
- CPU usage: Track during world generation phases
- Console errors: Zero tolerance for critical errors

**Monitoring Commands:**
```javascript
// Performance check
mcp_sparkedhost_send-console-command({
  command: "tps"
})

// Plugin status
mcp_sparkedhost_send-console-command({
  command: "plugins"
})

// Memory usage
mcp_sparkedhost_send-console-command({
  command: "gc"
})
```

### Testing Validation Protocols

**Comprehensive Testing Sequence:**
1. **Server Restart Test**: All settings persist after restart
2. **Multi-Player Test**: Kit distribution for concurrent joins
3. **Extended Duration Test**: 2+ hours stability testing
4. **Edge Case Testing**: Full inventory, plugin conflicts
5. **Performance Test**: Resource usage under load

### Troubleshooting Decision Tree

**Configuration Issues:**
1. Verify MCP connection and file upload success
2. Check server console for configuration errors
3. Test individual components before integration
4. Use manual commands to validate functionality

**Performance Issues:**
1. Monitor resource usage during problematic operations
2. Test with single player before multi-player scenarios
3. Review plugin compatibility and resource requirements
4. Implement gradual load testing approach

### Documentation Standards

**Change Tracking:**
- Document every configuration modification made
- Record MCP commands executed for reproducibility
- Note any deviations from standard procedures
- Maintain rollback procedures for each change

**Success Criteria Documentation:**
- World seed verification screenshot/log
- Time lock confirmation over extended period
- Phantom prevention test results
- Kit distribution success rate metrics

### Emergency Procedures

**Rollback Process:**
1. Use backed up configurations via `mcp_sparkedhost_upload-file`
2. Restart server to apply rollback configurations
3. Verify functionality returns to pre-change state
4. Document rollback reason and resolution

**Escalation Process:**
- SparkedHost support for server access issues
- Plugin documentation/community for plugin-specific problems
- Minecraft documentation for gamerule/server.properties issues

---

**Project Focus**: Configuration management using MCP tools for server event setup
**Risk Level**: Low (test environment)
**Success Metrics**: Functional night event server with automated player kit distribution
**Timeline**: Complete implementation within 2-3 hours including testing